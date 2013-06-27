/*
 * Copyright (c) 2010-2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var BBBUtils;

(function() {

	if(BBBUtils == null)
		BBBUtils = new Object();
		
    var bbbUserSelectionOptions = null;
	var bbbTrimpathModifiers = null;
	var bbbTrimpathMacros = null;

	// Get the current user from EB
	BBBUtils.getCurrentUser = function() {
		var user = null;
		jQuery.ajax( {
	 		url : "/direct/user/current.json",
	   		dataType : "json",
	   		async : false,
		   	success : function(u) {
				user = u;
			},
			error : function(xmlHttpRequest,status,error) {
				BBBUtils.handleError(bbb_err_curr_user, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});

		return user;
	}

	// Get the current site from EB and returns its maintainRole
	BBBUtils.getMaintainRole = function() {
		var maintainRole = null;
		jQuery.ajax( {
	 		url : "/direct/site/" + bbbSiteId + ".json",
	   		dataType : "json",
	   		async : false,
		   	success : function(site) {
				maintainRole = site.maintainRole;
			},
			error : function(xmlHttpRequest,status,error) {
				BBBUtils.handleError(bbb_err_maintain_role, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});

		return maintainRole;
	}
	
	// Get a meeting
	BBBUtils.getMeeting = function(meetingId) {                
		var meeting = null;
		jQuery.ajax( {
            url: "/direct/bbb-tool/" + meetingId + ".json",
            dataType : "json",
            async : false,
            success : function(data) {
                meeting = data;
                BBBUtils.setMeetingPermissionParams(meeting); 
            },
            error : function(xmlHttpRequest,status,error) {
                BBBUtils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            }
        });
        return meeting;
	}
	
	// Get all site meetings
	BBBUtils.getMeetingList = function(siteId) {
		var list = null;
        jQuery.ajax( {
            url : "/direct/bbb-tool.json?siteId=" + siteId,
            dataType : "json",
            async : false,
            success : function(m,status) {
            	list = m['bbb-tool_collection'];
                if(!list) list = [];
    
                // Work out whether the current user is a moderator of any of the
                // meetings. If so, mark the meeting with a moderator flag.
                //for(var i=0,j=list.length;i<j;i++) {
                //    BBBUtils.setMeetingPermissionParams(list[i]);
                //}
            },
            error : function(xmlHttpRequest,status,error) {
                BBBUtils.handleError(bbb_err_meeting_list, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return list;
    }

	// Create a json representation of the meeting and post it to new on the bbb-tool provider
	BBBUtils.addUpdateMeeting = function() {
        // Consolidate date + time fields
        var today = new Date();
        var startMillis = 0, endMillis = 0;
        if(jQuery('#startDate1').attr('checked')) {
            var date = jQuery('#startDate2').datepick('getDate');
            var time = jQuery('#startTime').val().split(':');
            startMillis = date.getTime();
            startMillis += time[0] * 60 * 60 * 1000;
            startMillis += time[1] * 60 * 1000;
            
            date.setTime(startMillis);
            startMillis -= date.getTimezoneOffset() * 60 * 1000;
            startMillis += (parseInt(bbbUserTimeZoneOffset) * -1);
            
            date.setTime(startMillis);
            if( today.dst() != date.dst() ){
                startMillis = startMillis + 3600000;
            }
            
            jQuery('#startDate').val(startMillis);
        }else{
            jQuery('#startDate').removeAttr('name');
            jQuery('#addToCalendar').removeAttr('checked');
        }
        if(jQuery('#endDate1').attr('checked')) {
            var date = jQuery('#endDate2').datepick('getDate');
            var time = jQuery('#endTime').val().split(':');
            endMillis = date.getTime();
            endMillis += time[0] * 60 * 60 * 1000;
            endMillis += time[1] * 60 * 1000;
            
            date.setTime(endMillis);
            endMillis -= date.getTimezoneOffset() * 60 * 1000;
            endMillis += (parseInt(bbbUserTimeZoneOffset) * -1);
            
            date.setTime(endMillis);
            if( today.dst() != date.dst() ){
                endMillis = endMillis + 3600000;
            }
            
            jQuery('#endDate').val(endMillis);
        }else{
            jQuery('#endDate').removeAttr('name');
            jQuery('#endDate').val(null);
        }

        // Validation
        BBBUtils.hideMessage();
        var errors = false;
        
        // Validate title field
        var meetingTitle = jQuery('#bbb_meeting_name_field').val().replace(/^\s+/, '').replace(/\s+$/, '');
        if(meetingTitle == '') {
            BBBUtils.showMessage(bbb_err_no_title, 'warning');
        	errors = true;
        }
        
        // Validate participants list
        if(jQuery('#selContainer tbody tr').length == 0) {
            BBBUtils.showMessage(bbb_err_no_participants, 'warning');
            errors = true;
        }
        
        // Validate date fields
        if(jQuery('#startDate1').attr('checked') && jQuery('#endDate1').attr('checked')) {
            if(endMillis == startMillis) {
                BBBUtils.showMessage(bbb_err_startdate_equals_enddate, 'warning');
                errors = true;
            }else if(endMillis < startMillis) {
                BBBUtils.showMessage(bbb_err_startdate_after_enddate, 'warning');
                errors = true;
            }
        }
        
        // Get description/welcome msg from FCKEditor
        BBBUtils.updateFromInlineFCKEditor('bbb_welcome_message_textarea');

        // Validate description length
        var maxLength = bbbAddUpdateFormConfigParameters.descriptionMaxLength;
        var descriptionLength = jQuery('#bbb_welcome_message_textarea').val().length;
        if( descriptionLength > maxLength ) {
            BBBUtils.showMessage(bbb_err_meeting_description_too_long(maxLength, descriptionLength), 'warning');
            errors = true;
        }
        
        if(errors) return false
        
        $('.bbb_site_member,.bbb_site_member_role').removeAttr('disabled');
        
        // Submit!
        var isNew = $("#isNew").val() == true || $("#isNew").val() == 'true';
        var actionUrl = $("#bbb_add_update_form").attr('action');
        var meetingId = $("#meetingId").val();
        jQuery.ajax( {
            url : actionUrl,
            type : 'POST',
            dataType: 'text',
            async : true,
            data : jQuery("#bbb_add_update_form").serializeArray(),
            beforeSend: function(xmlHttpRequest) {
                BBBUtils.hideMessage();
                jQuery('#bbb_save,#bbb_cancel').attr('disabled','disabled');
                BBBUtils.showAjaxIndicator('#bbb_addUpdate_ajaxInd');
            },
            success : function(returnedMeetingId) {
            	var _meetingId = returnedMeetingId ? returnedMeetingId : meetingId;
                var meeting = BBBUtils.getMeeting(_meetingId);
                bbbCurrentMeetings.addUpdateMeeting(meeting);                  
                BBBUtils.hideAjaxIndicator('#bbb_addUpdate_ajaxInd');
                switchState('currentMeetings');
            },
            error : function(xmlHttpRequest, status, error) {
                BBBUtils.hideAjaxIndicator('#bbb_addUpdate_ajaxInd');
                jQuery('#bbb_save,#bbb_cancel').removeAttr('disabled');
                if(isNew) {
                    BBBUtils.handleError(bbb_err_create_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                }else{
                    BBBUtils.handleError(bbb_err_update_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                }
            }
        });
    }
	
	BBBUtils.setMeetingInfoParams = function(meeting) {
		//Clear attendees
		if( meeting.attendees && meeting.attendees.length > 0 )
			delete meeting.attendees;
		meeting.attendees = new Array();
		meeting.hasBeenForciblyEnded = "false";
		meeting.participantCount = 0;
		meeting.moderatorCount = 0;
		meeting.unreachableServer = "false";
			
		var meetingInfo = BBBUtils.getMeetingInfo(meeting.id);
		if ( meetingInfo != null && meetingInfo.returncode != null) {
			if ( meetingInfo.returncode != 'FAILED' ) {
				meeting.attendees = meetingInfo.attendees;
				meeting.hasBeenForciblyEnded = meetingInfo.hasBeenForciblyEnded;
				meeting.participantCount = meetingInfo.participantCount;
				meeting.moderatorCount = meetingInfo.moderatorCount;
			} else {
				//Different errors can be handled here
				meeting.unreachableServer = "true";
			}
		}

	}

	BBBUtils.setRecordingPermissionParams = function(recording) {
        // specific recording permissions
        var offset = bbbServerTimeStamp.timezoneOffset;
        recording.timezoneOffset = "GMT" + (offset > 0? "+": "") +(offset/3600000);
        
        if(bbbCurrentUser.id === recording.ownerId) {
            recording.canEdit = bbbUserPerms.bbbEditOwn || bbbUserPerms.bbbEditAny;
            recording.canEnd = bbbUserPerms.bbbEditOwn || bbbUserPerms.bbbEditAny;
            recording.canDelete = bbbUserPerms.bbbDeleteOwn || bbbUserPerms.bbbDeleteAny;
        }else{
        	recording.canEdit = bbbUserPerms.bbbEditAny;
        	recording.canEnd = bbbUserPerms.bbbEditAny;
        	recording.canDelete = bbbUserPerms.bbbDeleteAny;
        }
	}
	
	BBBUtils.setMeetingPermissionParams = function(meeting) {

		// joinable only if on specified date interval (if any)
		
		var serverTimeStamp = parseInt(bbbServerTimeStamp.timestamp);
		serverTimeStamp = (serverTimeStamp - serverTimeStamp % 1000);

		var startOk = !meeting.startDate || meeting.startDate == 0 || serverTimeStamp >= meeting.startDate;
        var endOk = !meeting.endDate || meeting.endDate == 0 || serverTimeStamp < meeting.endDate;

        meeting.notStarted = !startOk && endOk;
        meeting.finished = startOk && !endOk;
        meeting.joinable = startOk && endOk;
        
        // specific meeting permissions
        if(bbbCurrentUser.id === meeting.ownerId) {
            meeting.canEdit = bbbUserPerms.bbbEditOwn | bbbUserPerms.bbbEditAny;
            meeting.canEnd = bbbUserPerms.bbbEditOwn | bbbUserPerms.bbbEditAny;
            meeting.canDelete = bbbUserPerms.bbbDeleteOwn | bbbUserPerms.bbbDeleteAny;
        }else{
            meeting.canEdit = bbbUserPerms.bbbEditAny;
            meeting.canEnd = bbbUserPerms.bbbEditAny;
            meeting.canDelete = bbbUserPerms.bbbDeleteAny;
        }
	}

	BBBUtils.setMeetingJoinableModeParams = function(meeting) {
	    //if joinable set the joinableMode
		meeting.joinableMode = "nojoinable";
		if( meeting.joinable ){
			if( meeting.unreachableServer == "false" ){
				meeting.joinableMode = "available";
				if ( meeting.hasBeenForciblyEnded == "true" ) {
					meeting.joinableMode = "unavailable";
				} else if ( meeting.attendees.length > 0 ) {
					meeting.joinableMode = "inprogress";
				}
			} else {
				meeting.joinableMode = "unreachable";
			}
		}
	}
	
	// End the specified meeting. The name parameter is required for the confirm
	// dialog
	BBBUtils.endMeeting = function(name, meetingID) {
		var question = bbb_action_end_meeting_question(unescape(name));
	
		if(!confirm(question)) return;
		
		jQuery.ajax( {
	 		url : "/direct/bbb-tool/endMeeting?meetingID=" + meetingID,
			dataType:'text',
			type:"GET",
		   	success : function(result) {
				switchState('currentMeetings');
			},
			error : function(xmlHttpRequest,status,error) {
                var msg = bbb_err_end_meeting(name);
                BBBUtils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});
	}

	// Delete the specified meeting. The name parameter is required for the confirm
	// dialog
	BBBUtils.deleteMeeting = function(name,meetingId) {
		var question = bbb_action_delete_meeting_question(unescape(name));
	
		if(!confirm(question)) return;
		
		jQuery.ajax( {
	 		url : "/direct/bbb-tool/" + meetingId,
			dataType:'text',
			type:"DELETE",
		   	success : function(result) {
				// Remove the meeting from the cached meeting array
				for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
					if(meetingId === bbbCurrentMeetings[i].id) {
						bbbCurrentMeetings.splice(i,1);
                        break;
					}
				}

				switchState('currentMeetings');
			},
			error : function(xmlHttpRequest,status,error) {
                var msg = bbb_err_end_meeting(name);
                BBBUtils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});
	}
    
	// Delete the specified recording from the BigBlueButton server. The name parameter is required for the confirm
	// dialog
	BBBUtils.deleteRecordings = function(meetingID, recordID, stateFunction, confirmationMsg) {
	
		var question = bbb_action_delete_recording_question(unescape(confirmationMsg));

		if(!confirm(question)) return;
		
		jQuery.ajax( {
	 		url : "/direct/bbb-tool/deleteRecordings?meetingID=" + meetingID + "&recordID=" + recordID,
			dataType:'text',
			type:"GET",
		   	success : function(result) {
				if(stateFunction == 'recordings')
					switchState('recordings');
				else
					switchState('recordings_meeting',{'meetingId':meetingID});
			},
			error : function(xmlHttpRequest,status,error) {
                	var msg = bbb_err_delete_recording(recordID);
                BBBUtils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});
	}

	// Publish the specified recording from the BigBlueButton server. 
	BBBUtils.publishRecordings = function(meetingID, recordID, stateFunction) {
		BBBUtils.setRecordings(meetingID, recordID, "true", stateFunction);
	
	}

	// Unpublish the specified recording from the BigBlueButton server. 
	BBBUtils.unpublishRecordings = function(meetingID, recordID, stateFunction) {
		BBBUtils.setRecordings(meetingID, recordID, "false", stateFunction);
	
	}

	// Publish the specified recording from the BigBlueButton server. 
	BBBUtils.setRecordings = function(meetingID, recordID, action, stateFunction) {

		jQuery.ajax( {
	 		url : "/direct/bbb-tool/publishRecordings?meetingID=" + meetingID + "&recordID=" + recordID + "&publish=" + action,
			dataType:'text',
			type: "GET",
		   	success : function(result) {
				if(stateFunction == 'recordings')
					switchState('recordings');
				else
					switchState('recordings_meeting',{'meetingId':meetingID});
			},
			error : function(xmlHttpRequest,status,error) {
				if( action == 'PUBLISH' )
                	var msg = bbb_err_publish_recording(recordID);
                else
                	var msg = bbb_err_unpublish_recording(recordID);
                BBBUtils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});
	}

    // Get meeting info from BBB server
    BBBUtils.getMeetingInfo = function(meetingId) {  
    	var meetingInfo = null;

        jQuery.ajax( {
            url: "/direct/bbb-tool/" + meetingId + "/getMeetingInfo.json",
            dataType : "json",
            async : false,
            success : function(data) {
                meetingInfo = data;
            },
            error : function(xmlHttpRequest,status,error) {
            	BBBUtils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            }
        });
        return meetingInfo;
    }
    
    // Get site recordings from BBB server
    BBBUtils.getSiteRecordingList = function(siteId) {
    	if (siteId == null) siteId = "";
    		
    	var response = Object();
        jQuery.ajax( {
            url: "/direct/bbb-tool/getSiteRecordings.json?siteId=" + siteId,
            dataType : "json",
            async : false,
            success : function(data) {
        		response = data;
            },
            error : function(xmlHttpRequest,status,error) {
            	BBBUtils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return response;
    }
    
    // Get meeting recordings from BBB server
    BBBUtils.getMeetingRecordingList = function(meetingId) {
    	if (meetingId == null) meetingId = "";

    	var response = Object();
        jQuery.ajax( {
            url: "/direct/bbb-tool/" + meetingId + "/getRecordings.json",
            dataType : "json",
            async : false,
            success : function(data) {
    			response = data;
            },
            error : function(xmlHttpRequest,status,error) {
            	BBBUtils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return response;
    }

    // Log an event indicating user is joining meeting
    BBBUtils.joinMeeting = function(meetingId, linkSelector) { 
        jQuery.ajax( {
            url: "/direct/bbb-tool/"+meetingId+"/joinMeeting",
            async : false,
            success : function(url) {
            	BBBUtils.hideMessage();
            	if(linkSelector) {
            		jQuery(linkSelector).attr('href', url);
            		$('#meeting_joinlink_' + meetingId).hide();

            		//After joining stop requesting updates
            		clearInterval(bbbCheckOneMeetingAvailabilityId);
					clearInterval(bbbCheckRecordingAvailabilityId);
            	}
            	return true;
            },
            error : function(xmlHttpRequest,status,error) {
                BBBUtils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                if(linkSelector) {
                	jQuery(linkSelector).removeAttr('href');
                }
                return false;
            }
        });
    }
    
    // Get current server time (in milliseconds) in user timezone
    BBBUtils.updateServerTime = function() {
    	var response = Object();
    	jQuery.ajax( {
            url: "/direct/bbb-tool/getServerTimeInDefaultTimezone.json",
            dataType : "json",
            async : false,
            success : function(timestamp) {
            	response = timestamp;
            }
        });
        return response;
    }

    // Get tool version
    BBBUtils.getToolVersion = function() {

    	var response = Object();

    	jQuery.ajax( {
            url: "/direct/bbb-tool/getToolVersion.json",
            dataType : "json",
            async : false,
            success : function(version) {
            	response = version;
            }
        });

    	return response;
    }

    // Check if a user is already logged on a meeting
    BBBUtils.isUserInMeeting = function(userName, meeting) {
		for(var p=0; p<meeting.attendees.length; p++) {
			if(bbbCurrentUser.displayName === meeting.attendees[p].fullName) {
				return true;
			}
		}
		return false;
    }
    
    // Check ONE meetings availability and update meeting details page if appropriate
    BBBUtils.checkOneMeetingAvailability = function(meetingId) {
    	for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
    		if( bbbCurrentMeetings[i].id == meetingId ) {
                BBBUtils.setMeetingInfoParams(bbbCurrentMeetings[i]);
                BBBUtils.setMeetingJoinableModeParams(bbbCurrentMeetings[i]);
    			BBBUtils.checkMeetingAvailability(bbbCurrentMeetings[i]);
      	   	 	updateMeetingInfo(bbbCurrentMeetings[i]);
    			return;
    		}
    	}

    }

    // Check ALL meetings availability and update meeting details page if appropriate
    BBBUtils.checkAllMeetingAvailability = function() {
    	for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
    		BBBUtils.setMeetingInfoParams(bbbCurrentMeetings[i]);
            BBBUtils.setMeetingJoinableModeParams(bbbCurrentMeetings[i]);
            BBBUtils.checkMeetingAvailability(bbbCurrentMeetings[i]);
    	}

    }
    
    // Check specific meeting availability and update meeting details page if appropriate
    BBBUtils.checkMeetingAvailability = function(meeting) {
        if(meeting.joinable) {
            if ( meeting.joinableMode === "available" ){
                jQuery('#meeting_joinlink_'+meeting.id).fadeIn();
                // Update the actionbar on the list
                if ( meeting.canEnd ){ 
                    jQuery('#end_meeting_'+meeting.id)
                    .removeClass()
                    .addClass('end_meeting_hidden');
                }
                // Update for list
                jQuery('#meeting_status_'+meeting.id)
                   .removeClass()
                   .addClass('status_joinable_available')
                   .text(bbb_status_joinable_available);
                // Update for detail
                jQuery('#meeting_status_joinable_'+meeting.id)
                	.removeClass()
                	.addClass('status_joinable_available')
                	.text(bbb_status_joinable_available);
        	} else if ( meeting.joinableMode === "inprogress" ){
        		if( BBBUtils.isUserInMeeting(bbbCurrentUser.displayName, meeting) )
    				jQuery('#meeting_joinlink_'+meeting.id).fadeOut();
        		else
                    jQuery('#meeting_joinlink_'+meeting.id).fadeIn();
        			
                // Update the actionbar on the list
                if ( meeting.canEnd ){ 
                    jQuery('#end_meeting_'+meeting.id)
                    .removeClass()
                    .addClass('end_meeting_shown');
                }
                // Update for list
                jQuery('#meeting_status_'+meeting.id)
                   .removeClass()
                   .addClass('status_joinable_inprogress')
                   .text(bbb_status_joinable_inprogress);
                // Update for detail
                jQuery('#meeting_status_joinable_'+meeting.id)
                	.removeClass()
                	.addClass('status_joinable_inprogress')
                	.text(bbb_status_joinable_inprogress);

        	} else if ( meeting.joinableMode === "unavailable" ){
                jQuery('#meeting_joinlink_'+meeting.id).fadeOut();
                // Update the actionbar on the list
                if ( meeting.canEnd ){ 
                    jQuery('#end_meeting_'+meeting.id)
                    .removeClass()
                    .addClass('end_meeting_hidden');
                }
                // Update for list
                jQuery('#meeting_status_'+meeting.id)
                   .removeClass()
                   .addClass('status_joinable_unavailable')
                   .text(bbb_status_joinable_unavailable);
                // Update for detail
                jQuery('#meeting_status_joinable_'+meeting.id)
                	.removeClass()
                	.addClass('status_joinable_unavailable')
                	.text(bbb_status_joinable_unavailable);

    			jQuery('#bbb_meeting_info_participants_count').html('0');
    		    jQuery('#bbb_meeting_info_participants_count_tr').fadeOut();
                jQuery('#bbb_meeting_info_participants_count_tr').hide();

        	} else if ( meeting.joinableMode === "unreachable" ){
                jQuery('#meeting_joinlink_'+meeting.id).fadeOut();
                // Update the actionbar on the list
                if ( meeting.canEnd ){ 
                    jQuery('#end_meeting_'+meeting.id)
                    .removeClass()
                    .addClass('end_meeting_hidden');
                }
                // Update for list
                jQuery('#meeting_status_'+meeting.id)
                   .removeClass()
                   .addClass('status_joinable_unreachable')
                   .text(bbb_status_joinable_unreachable);
                // Update for detail
                jQuery('#meeting_status_joinable_'+meeting.id)
                	.removeClass()
                	.addClass('status_joinable_unreachable')
                	.text(bbb_status_joinable_unreachable);

    			jQuery('#bbb_meeting_info_participants_count').html('0');
    		    jQuery('#bbb_meeting_info_participants_count_tr').fadeOut();
                jQuery('#bbb_meeting_info_participants_count_tr').hide();
        	}
        } else if(meeting.notStarted) {
            jQuery('#meeting_joinlink_'+meeting.id).fadeOut();
            jQuery('#meeting_status_'+meeting.id)
               .removeClass()
               .addClass('status_notstarted')
               .text(bbb_status_notstarted);
        }else if(meeting.finished) {
            jQuery('#meeting_joinlink_'+meeting.id).fadeOut();
            jQuery('#meeting_status_'+meeting.id)
               .removeClass()
               .addClass('status_finished')
               .text(bbb_status_finished);
               
        }
    }
    
    // Check ONE recording availability and update recording details page if appropriate
    BBBUtils.checkOneRecordingAvailability = function(meetingId) {
    	for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
            var meeting = bbbCurrentMeetings[i];
    		if( meeting.id == meetingId )
    			BBBUtils.checkRecordingAvailability(meeting);
    	}
    }

    // Check ALL recording availability and update meeting details page if appropriate
    BBBUtils.checkAllRecordingAvailability = function() {
    	for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
            BBBUtils.checkRecordingAvailability(bbbCurrentMeetings[i]);
    	}
    }
    
    BBBUtils.checkRecordingAvailability = function(meetingId) {
		var recordings = BBBUtils.getMeetingRecordingList(meetingId).recordings;
		if( recordings == null ){
            BBBUtils.showMessage(bbb_err_get_recording, 'warning');
        } else {
        	BBBUtils.hideMessage();	
        	
        	var htmlRecordings = "";
        	if(recordings.length > 0)
				htmlRecordings = '(<a href="javascript:;" onclick="return switchState(\'recordings_meeting\',{\'meetingId\':\''+ meetingId + '\'})" title="">' + bbb_meetinginfo_recordings(unescape(recordings.length)) + '</a>)&nbsp;&nbsp;';
        	else
            	htmlRecordings = "(" + bbb_meetinginfo_recordings(unescape(recordings.length)) + ")";
        		
        	jQuery('#recording_link_'+meetingId).html(htmlRecordings);
		}
    }

    // Get notice message to be displayed on the UI (first time access)
    BBBUtils.addNotice = function() {
        jQuery.ajax( {
            url: "/direct/bbb-tool/getNoticeText.json",
            dataType : "json",
            async : true,
            success : function(notice) {
            	if(notice && notice.text) {
            	   BBBUtils.showMessage(notice.text, notice.level);
            	   BBBUtils.adjustFrameHeight();
            	}
            }
        });
    }
	

    // Get the participant object associated with the specified userId
	BBBUtils.getParticipantFromMeeting = function(meeting, userId) {
		if(meeting && meeting.participants) {
            for(var i=0; i<meeting.participants.length; i++) {
                if(meeting.participants[i].selectionType == 'user'
                && meeting.participants[i].selectionId == userId) {
                    return meeting.participants[i];
                }
            }
        }
        return null;
	}
    
    // Get user selection types
    BBBUtils.getUserSelectionTypes = function() {
    	var selTypes = {
            all:    {id: 'all', title: bbb_seltype_all},
            user:   {id: 'user', title: bbb_seltype_user},
            group:  {id: 'group', title: bbb_seltype_group},
            role:   {id: 'role', title: bbb_seltype_role}
    	};
    	return selTypes;
    }
	
	// Get user selection options from EB
    BBBUtils.getUserSelectionOptions = function() {
        if(bbbUserSelectionOptions == null) {
            jQuery.ajax( {
                url : "/direct/bbb-tool/getUserSelectionOptions.json?siteId=" + bbbSiteId,
                dataType : "json",
                async : false,
                success : function(data) {
                    bbbUserSelectionOptions = data;
                },
                error : function(xmlHttpRequest,status,error) {
                    BBBUtils.handleError(bbb_err_user_sel_options, xmlHttpRequest.status, xmlHttpRequest.statusText);
                }
            });
        }

        return bbbUserSelectionOptions;
    }
	
	// Get the user permissions
	BBBUtils.getUserPermissions = function() {
		var perms = null;
        jQuery.ajax( {
            url: "/direct/site/"+bbbSiteId+"/userPerms.json",
            dataType : "json",
            async : false,
            success : function(userPermissions) {
            	if(userPermissions != null) perms = userPermissions.data;
            	if(bbbCurrentUser.id == 'admin' || perms.indexOf('site.upd') >= 0 ) perms.push("bbb.admin");
            },
            error : function(xmlHttpRequest,status,error) {
            	if(bbbCurrentUser.id == 'admin') {
            		// Workaround for SAK-18534
            		perms = ["bbb.admin", "bbb.create", "bbb.edit.any", "bbb.delete.any", "bbb.participate",
                             "site.upd", "site.viewRoster", "calendar.new", "calendar.revise.any", "calendar.delete.any"];
            	}else{
                    BBBUtils.handleError(bbb_err_get_user_permissions, xmlHttpRequest.status, xmlHttpRequest.statusText);
            	}
            }
        });
        return perms;
    }

	// Get notice message to be displayed on the UI (first time access)
    BBBUtils.autorefreshInterval = function() {
		var interval = [];
		interval.meetings = 30000;
		interval.recordings = 60000;
        jQuery.ajax( {
            url: "/direct/bbb-tool/getAutorefreshInterval.json",
            dataType : "json",
            async : false,
            success : function(autorefresh) {
            	if(autorefresh) {
            		interval.meetings = autorefresh.meetings;
            		interval.recordings = autorefresh.recordings;
            	}
            }
        });
        return interval;
    }
    
    BBBUtils.addUpdateFormConfigParameters = function() {
		var addUpdateFormConfigParams = [];
		addUpdateFormConfigParams.recording = true;
		addUpdateFormConfigParams.descriptionMaxLength = 60000;
        jQuery.ajax( {
            url: "/direct/bbb-tool/getAddUpdateFormConfigParameters.json",
            dataType : "json",
            async : false,
            success : function(formConfigParams) {
            	if(formConfigParams) {
            		addUpdateFormConfigParams.recording = (formConfigParams.recording == 'true');
            		addUpdateFormConfigParams.descriptionMaxLength = formConfigParams.descriptionMaxLength;
            	}
            }
        });
        return addUpdateFormConfigParams;
    }

    // Get the site permissions
    BBBUtils.getSitePermissions = function() {
        var perms = [];
        jQuery.ajax( {
            url : "/direct/site/" + bbbSiteId + "/perms/bbb.json",
            dataType : "json",
            async : false,
            success : function(p) {
                for(role in p.data) {
                    var permSet = {'role':role};

                    for(var i=0,j=p.data[role].length;i<j;i++) {
                        var perm = p.data[role][i].replace(/\./g,"_");
                        eval("permSet." + perm + " = true");
                    }

                    perms.push(permSet);
                }
            },
            error : function(xmlHttpRequest,status,error) {
                BBBUtils.handleError(bbb_err_get_permissions, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });

        return perms;
    }

    // Set the site permissions
    BBBUtils.setSitePermissions = function(boxesSelector, successCallback, errorCallback) {
        var boxes = $(boxesSelector);
        var myData = {};
        for(var i=0,j=boxes.length;i<j;i++) {
            var box = boxes[i];
            if(box.checked)
                myData[box.id] = 'true';
            else
                myData[box.id] = 'false';
        }

        jQuery.ajax( {
            url : "/direct/site/" + bbbSiteId + "/setPerms",
            type : 'POST',
            data : myData,
            async : false,
            dataType: 'text',
            success : function(data) {
                if(successCallback) successCallback();
            },
            error : function(xmlHttpRequest,status,error) {
                BBBUtils.handleError(bbb_err_set_permissions, xmlHttpRequest.status, xmlHttpRequest.statusText);
                if(errorCallback) errorCallback();
            }
        });
        return false;
    }

	// Convenience function for rendering a trimpath template
	BBBUtils.render = function(templateName, contextObject, output) {	
		contextObject._MODIFIERS = BBBUtils.getTrimpathModifiers();
		var templateNode = document.getElementById(templateName);
		var firstNode = templateNode.firstChild;
		var template = BBBUtils.getTrimpathMacros();
		if ( firstNode && ( firstNode.nodeType === 8 || firstNode.nodeType === 4))
  			template += templateNode.firstChild.data.toString();
   	 	 else
   			template += templateNode.innerHTML.toString();

		var trimpathTemplate = TrimPath.parseTemplate(template,templateName);

   		var render = trimpathTemplate.process(contextObject);

		if (output)
			document.getElementById(output).innerHTML = render;

		return render;
	}
	
	// Convenience function for grabbing the url parameters
	BBBUtils.getParameters = function() {
		var arg = new Object();
		var href = document.location.href;

		if ( href.indexOf('?') != -1) {
			var paramString = href.split('?')[1];
			
			if(paramString.indexOf('#') != -1)
				paramString = paramString.split("#")[0];
				
			var params = paramString.split('&');

			for (var i = 0; i < params.length; ++i) {
				var name = params[i].split('=')[0];
				var value = params[i].split('=')[1];
				arg[name] = unescape(value);
			}
		}
	
		return arg;
	}
    
    /** Setup defaults for Ajax */
    BBBUtils.setupAjax = function() {
        jQuery.ajaxSetup({
            async: true,
            cache: false,
            timeout: 30000,
            complete: function (request, textStatus) {
                try{
                    if(request.status
                            && request.status != 200 && request.status != 201 
                            && request.status != 204 && request.status != 404 && request.status != 1223){
                        if(request.status == 403) {
                            BBBUtils.handleError(bbb_err_no_permissions, request.status, request.statusText);
                        	jQuery('#bbb_content').empty();
                        }else{
                            // Handled by error() callbacks
                        }
                   }
                }catch(e){}
            }
        }); 
    }
	
	/** Handle communication errors */
	BBBUtils.handleError = function(message, statusCode, statusMessage) {
		var severity = 'error';
		var description = '';
		if(statusCode || statusMessage) {
			description += bbb_err_server_response + ': ';
            if(statusMessage) description += statusMessage;
            if(statusCode) description += ' ['+bbb_err_code+': ' + statusCode + ']';
		}
		if(message && (statusCode || statusMessage)) {
		    BBBUtils.showMessage(description, severity, message);
		}else if(message) {
		    BBBUtils.showMessage(description, severity, message);
		}else{
			BBBUtils.showMessage(description, severity);
		}
	}
	
	/** 
	 * Render a message with a specific severity
	 * @argument msgBod: The message to be displayed
	 * @argument severity: Message severity [optional, defaults to 'information')
	 * @argument msgTitle: Message title [optional, defaults to nothing] 
	 */
    BBBUtils.showMessage = function(msgBody, severity, msgTitle, hideMsgBody) {
    	var useAlternateStyle = true;
    	if(typeof hideMsgBody == 'undefined' && msgTitle && msgBody) hideMsgBody = true;
    	
    	if( !bbbErrorLog[msgBody] ){
			bbbErrorLog[msgBody] = true;

	        // severity
	        var msgClass = null;
	        if(!severity || severity == 'info' || severity == 'information')
	            msgClass = !useAlternateStyle ? 'information' : 'messageInformation';
	        else if(severity == 'success')
	            msgClass = !useAlternateStyle ? 'success' : 'messageSuccess';
	        else if(severity == 'warn' || severity == 'warning' || severity == 'error' || severity == 'fail') 
	            msgClass = !useAlternateStyle ? 'alertMessage' : 'messageError';
	        
	        // add contents
	        var id = Math.floor(Math.random()*1000);
	        var msgId = 'msg-'+id;
	        var msgDiv = jQuery('<div class="bbb_message" id="'+msgId+'"></div>');
	        var msgsDiv = jQuery('#bbb_messages').append(msgDiv);
	        var message = jQuery('<div class="'+msgClass+'"></div>');
	        if(msgTitle && msgTitle != '') {
	            message.append('<h4>'+msgTitle+'</h4>');
	            if(hideMsgBody) message.append('<span id="msgShowDetails-'+id+'">&nbsp;<small>(<a href="#" onclick="jQuery(\'#msgBody-'+id+'\').slideDown();jQuery(\'#msgShowDetails-'+id+'\').hide();BBBUtils.adjustFrameHeight();return false;">'+bbb_err_details+'</a>)</small></span>');
	        }
	        jQuery('<p class="closeMe">  (x) </p>').click(function(){ BBBUtils.hideMessage(msgId); }).appendTo(message);
	        if(msgBody) {
	            var msgBodyContent = jQuery('<div id="msgBody-'+id+'" class="content">'+msgBody+'</div>');
	            message.append(msgBodyContent);
	            if(hideMsgBody) msgBodyContent.hide();
	        }
	        
	        // display, adjust frame height, scroll to top
	        msgDiv.html(message);
	        msgsDiv.fadeIn(function(){ BBBUtils.adjustFrameHeight(); });
	        jQuery('html, body').animate({scrollTop:0}, 'slow');
			
    	}
    }
        
    /** Hide message box */
    BBBUtils.hideMessage = function(id) {
    	delete bbbErrorLog;
    	bbbErrorLog = new Object();
    	if(id) {
    		jQuery('#'+id).fadeOut();
    	}else{
            jQuery('#bbb_messages').empty().hide();
    	}
    }
    
    /** Show an ajax indicator at the following DOM selector */
    BBBUtils.showAjaxIndicator = function(outputSelector) {
    	jQuery(outputSelector).empty()
            .html('<img src="images/ajaxload.gif" alt="..." class="bbb_imgIndicator"/>')
            .show();
    }

    /** Hide the ajax indicator at the following DOM selector */
    BBBUtils.hideAjaxIndicator = function(outputSelector) {
    	jQuery(outputSelector).hide();
    }
    
    /** Adjust frame height (if in an iframe) */
    BBBUtils.adjustFrameHeight = function() {
    	if(window.frameElement && window.frameElement.id)
    	   setMainFrameHeightNow(window.frameElement.id);
    }
    
    /** Transform a textarea element on to a FCKEditor, uppon user click */
    BBBUtils.makeInlineFCKEditor = function(textAreaId, toolBarSet, width, height) {
        var textArea = jQuery('#'+textAreaId);
        var textAreaContents = jQuery(textArea).text();
        var fakeTextAreaId = textAreaId + '-' + 'fake';
        var fakeTextAreaInstrId = textAreaId + '-' + 'fakeInstr';
        if(jQuery('#'+fakeTextAreaId).length > 0) {
            jQuery('#'+fakeTextAreaId).remove();
        }
        jQuery(textArea)
           .hide()
           .before('<div id="'+fakeTextAreaId+'" class="inlineFCKEditor"><span id="'+fakeTextAreaInstrId+'" class="inlineFCKEditorInstr">'+bbb_click_to_edit+'</span>'+textAreaContents+'</div>');
        
        // Apply FCKEditor 
        var applyFCKEditor = function() {
            jQuery('#'+fakeTextAreaId).hide();
            jQuery(this).unbind('click');
            jQuery('#'+fakeTextAreaInstrId).unbind('mouseenter').unbind('mouseleave');
            jQuery('#bbb_meeting_name_field').unbind('keydown');
            // add FCKeditor
            width = !width ? '600' : width;
            height = !height ? '320' : height;
            var oFCKeditor = new FCKeditor(textAreaId);
            var collectionId = '/group/' + bbbSiteId + '/';
            oFCKeditor.BasePath = "/library/editor/FCKeditor/";
            oFCKeditor.Width = width;
            oFCKeditor.Height = height ;
            oFCKeditor.ToolbarSet = !toolBarSet ? 'Default' : toolBarSet;
            oFCKeditor.Config['ImageBrowserURL'] = oFCKeditor.BasePath + "editor/filemanager/browser/default/browser.html?Connector=/sakai-fck-connector/filemanager/connector&Type=Image&CurrentFolder=" + collectionId;
            oFCKeditor.Config['LinkBrowserURL'] = oFCKeditor.BasePath + "editor/filemanager/browser/default/browser.html?Connector=/sakai-fck-connector/filemanager/connector&Type=Link&CurrentFolder=" + collectionId;
            oFCKeditor.Config['FlashBrowserURL'] = oFCKeditor.BasePath + "editor/filemanager/browser/default/browser.html?Connector=/sakai-fck-connector/filemanager/connector&Type=Flash&CurrentFolder=" + collectionId;
            oFCKeditor.Config['ImageUploadURL'] = oFCKeditor.BasePath + "/sakai-fck-connector/filemanager/connector?Type=Image&Command=QuickUpload&Type=Image&CurrentFolder=" + collectionId;
            oFCKeditor.Config['FlashUploadURL'] = oFCKeditor.BasePath + "/sakai-fck-connector/filemanager/connector?Type=Flash&Command=QuickUpload&Type=Flash&CurrentFolder=" + collectionId;
            oFCKeditor.Config['LinkUploadURL'] = oFCKeditor.BasePath + "/sakai-fck-connector/filemanager/connector?Type=File&Command=QuickUpload&Type=Link&CurrentFolder=" + collectionId;
            oFCKeditor.Config['CurrentFolder'] = collectionId;
            oFCKeditor.Config['CustomConfigurationsPath'] = "/library/editor/FCKeditor/config.js";
            oFCKeditor.ReplaceTextarea(); 
            BBBUtils.adjustFrameHeight();
        };
           
        // events
        jQuery('#'+fakeTextAreaId).bind('mouseenter', function() {
            jQuery('#'+fakeTextAreaInstrId).fadeIn();
        }).bind('mouseleave', function() {
            jQuery('#'+fakeTextAreaInstrId).fadeOut();
        }).one('click', applyFCKEditor);
        jQuery('#bbb_meeting_name_field').bind('keydown', function(e){
            if(e.keyCode == 9 /* TAB key */) {
            	applyFCKEditor();
            }
        });
    }
            
    /** Update data from inline FCKEditor */
    BBBUtils.updateFromInlineFCKEditor = function(textAreaId) {
        if(typeof FCKeditorAPI != "undefined") {
            var editor = FCKeditorAPI.GetInstance(textAreaId);
            if(editor != null && editor.IsDirty()) {
                var ta_temp = document.createElement("textarea");
                ta_temp.innerHTML = editor.GetData().replace(/</g,"&lt;").replace(/>/g,"&gt;");
                var decoded_html = ta_temp.value;
                jQuery('#'+textAreaId).text(decoded_html);   
            }
        }
    }
    
    /** Trimpath modifiers :) */
    BBBUtils.getTrimpathModifiers = function() {
    	if(!bbbTrimpathModifiers) {
    	   	bbbTrimpathModifiers = {    	   		
    	   	};
    	}
    	return bbbTrimpathModifiers;
    }
    
    /** Trimpath macros :) */
    BBBUtils.getTrimpathMacros = function() {
        if(!bbbTrimpathMacros) {
            bbbTrimpathMacros = '';
        }
        return bbbTrimpathMacros;
    }
	
}) ();


/** Protoypes */
if(!Array.prototype.indexOf) {
    Array.prototype.indexOf = function(needle) {
        for(var i = 0; i < this.length; i++) {
            if(this[i] === needle) {
                return i;
            }
        }
        return -1;
    };
}

Array.prototype.indexOfMeeting=function(meeting){
    if(meeting && meeting.id) {
        for (var i=0; i<this.length; i++){
            if (this[i].id != null && this[i].id==meeting.id) return i;
        }
    }
    return -1;
}
Array.prototype.addUpdateMeeting=function(meeting){
    if(meeting && meeting.id) {
        var index = this.indexOfMeeting(meeting);
        if(index >= 0) {
            this[index] = meeting;
        }else{
            this.push(meeting);
        }
    }else if(meeting) {
        this.push(meeting);
    }
}

Date.prototype.toUTCString = function (){
    var date = this;
    var date_utc = new Date(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(),  date.getUTCHours(), date.getUTCMinutes(), date.getUTCSeconds());
    return date_utc.getTime();
    
}

Date.prototype.stdTimezoneOffset = function() {
    var jan = new Date(this.getFullYear(), 0, 1);
    var jul = new Date(this.getFullYear(), 6, 1);
    return Math.max(jan.getTimezoneOffset(), jul.getTimezoneOffset());
}

Date.prototype.dst = function() {
    return this.getTimezoneOffset() < this.stdTimezoneOffset();
}

Date.prototype.toISO8601String = function (format, offset) {
    /** From: http://delete.me.uk/2005/03/iso8601.html */
    /* Accepted values for the format [1-6]:
     1 Year:
       YYYY (eg 1997)
     2 Year and month:
       YYYY-MM (eg 1997-07)
     3 Complete date:
       YYYY-MM-DD (eg 1997-07-16)
     4 Complete date plus hours and minutes:
       YYYY-MM-DDThh:mmTZD (eg 1997-07-16T19:20+01:00)
     5 Complete date plus hours, minutes and seconds:
       YYYY-MM-DDThh:mm:ssTZD (eg 1997-07-16T19:20:30+01:00)
     6 Complete date plus hours, minutes and seconds (without 'T' and '+'):
       YYYY-MM-DDThh:mmTZD (eg 1997-07-16 19:20:00)
     7 Complete date plus hours, minutes, seconds and a decimal
       fraction of a second
       YYYY-MM-DDThh:mm:ss.sTZD (eg 1997-07-16T19:20:30.45+01:00)
    */
    if (!format) { var format = 6; }
    if (!offset) {
        var offset = 'Z';
        var date = this;
    } else {
        var d = offset.match(/([-+])([0-9]{2}):([0-9]{2})/);
        var offsetnum = (Number(d[2]) * 60) + Number(d[3]);
        offsetnum *= ((d[1] == '-') ? -1 : 1);
        var date = new Date(Number(Number(this) + (offsetnum * 60000)));
    }

    var zeropad = function (num) { return ((num < 10) ? '0' : '') + num; }

    var str = "";
    str += date.getFullYear();
    if (format > 1) { str += "-" + zeropad(date.getMonth() + 1); }
    if (format > 2) {
        if(format == 6) {
            str += "-" + zeropad(date.getDate());
        }else{
            str += "-" + zeropad(date.getUTCDate());
        }
    }
    if (format > 3) {
        if(format == 6) {
            str += " " + zeropad(date.getHours()) +
                   ":" + zeropad(date.getMinutes());
        }else{
            str += "T" + zeropad(date.getUTCHours()) +
                   ":" + zeropad(date.getUTCMinutes());
        }
    }
    if (format > 5) {
        var secs = Number(date.getUTCSeconds() + "." +
                   ((date.getUTCMilliseconds() < 100) ? '0' : '') +
                   zeropad(date.getUTCMilliseconds()));
        str += ":" + zeropad(secs);
    } else if (format > 4) { str += ":" + zeropad(date.getUTCSeconds()); }

    if (format > 3 && format != 6) { str += offset; }
    return str;
}

if (!Array.prototype.indexOf)
{
  Array.prototype.indexOf = function(elt /*, from*/)
  {
    var len = this.length >>> 0;

    var from = Number(arguments[1]) || 0;
    from = (from < 0)
         ? Math.ceil(from)
         : Math.floor(from);
    if (from < 0)
      from += len;

    for (; from < len; from++)
    {
      if (from in this &&
          this[from] === elt)
        return from;
    }
    return -1;
  };
}
/** Automatically transfer focus to FCKEditor when loaded */
function FCKeditor_OnComplete(editorInstance) {editorInstance.Focus();}

