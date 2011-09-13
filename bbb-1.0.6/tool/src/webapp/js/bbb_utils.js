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
            url: "/direct/bbb-meeting/" + meetingId + ".json",
            dataType : "json",
            async : false,
            success : function(data) {
                meeting = data;
                BBBUtils.setAdditionalMeetingParams(meeting); 
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
            url : "/direct/bbb-meeting.json?siteId=" + siteId,
            dataType : "json",
            async : false,
            success : function(m,status) {
            	list = m['bbb-meeting_collection'];
                if(!list) list = [];
    
                // Work out whether the current user is a moderator of any of the
                // meetings. If so, mark the meeting with a moderator flag.
                for(var i=0,j=list.length;i<j;i++) {
                    BBBUtils.setAdditionalMeetingParams(list[i]);
                }
            },
            error : function(xmlHttpRequest,status,error) {
                BBBUtils.handleError(bbb_err_meeting_list, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return list;
    }

	// Create a json representation of the meeting and post it to new on the bbb-meeting provider
	BBBUtils.addUpdateMeeting = function() {		
        // Consolidate date + time fields
        var startDate = null, endDate = null;
        var startMillis = 0, endMillis = 0;
        if(jQuery('#startDate1').attr('checked')) {
            var date = jQuery('#startDate2').datepick('getDate');
            var time = jQuery('#startTime').val().split(':');
            startMillis = date.getTime();
            startMillis += time[0] * 60 * 60 * 1000;
            startMillis += time[1] * 60 * 1000;
            date.setTime(startMillis);
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
        
        if(errors) return false
        
        // Get description/welcome msg from FCKEditor
        BBBUtils.updateFromInlineFCKEditor('bbb_welcome_message_textarea');
        
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
	
	BBBUtils.setAdditionalMeetingParams = function(meeting) {
        // joinable only if on specified date interval (if any)
        var timestamp = new Date().getTime() - bbbServerTimeDiff;
        var startOk = !meeting.startDate || meeting.startDate == 0 || timestamp >= meeting.startDate;
        var endOk = !meeting.endDate || meeting.endDate == 0 || timestamp < meeting.endDate;
        meeting.notStarted = !startOk && endOk;
        meeting.finished = startOk && !endOk;
        meeting.joinable = startOk && endOk;
        
        // specific meeting permissions
        if(bbbCurrentUser.id === meeting.ownerId) {
            meeting.canEdit = bbbUserPerms.bbbEditOwn | bbbUserPerms.bbbEditAny;
            meeting.canDelete = bbbUserPerms.bbbDeleteOwn | bbbUserPerms.bbbDeleteAny;
        }else{
            meeting.canEdit = bbbUserPerms.bbbEditAny;
            meeting.canDelete = bbbUserPerms.bbbDeleteAny;
        }
	}

	// Use EBs batch provider to DELETE all the meetings in the current site
	BBBUtils.endAllMeetingsForCurrentSite = function() {
		var refs = '';
		for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
			var url = '/direct/bbb-meeting/' + bbbCurrentMeetings[i].id;
			refs += url;
			if(i < j - 1) refs += ',';
		}
		
		if(refs == '') {
			BBBUtils.showMessage(bbb_err_meeting_list_empty);
			return;
		}
		
        if(!confirm(bbb_end_all_meetings_question)) return;

		jQuery.ajax( {
	 		url : "/direct/batch?_refs=" + refs,
			dataType:'text',
			type:"DELETE",
			async : false,
		   	success : function(result) {
				bbbCurrentMeetings = [];
				switchState('currentMeetings');
			},
			error : function(xmlHttpRequest,status,error) {
				BBBUtils.handleError(bbb_err_end_all_meetings, xmlHttpRequest.status, xmlHttpRequest.statusText);
			}
	  	});

		return false;
	}
	
	// End the specified meeting. The name parameter is required for the confirm
	// dialog
	BBBUtils.endMeeting = function(name,meetingId) {
		var question = bbb_end_meeting_question(unescape(name));
	
		if(!confirm(question)) return;
		
		jQuery.ajax( {
	 		url : "/direct/bbb-meeting/" + meetingId,
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
    
    // Get meeting info from BBB server
    BBBUtils.getMeetingInfo = function(meetingId) {  
    	var meetingInfo = null;
        jQuery.ajax( {
            url: "/direct/bbb-meeting/" + meetingId + "/getMeetingInfo.json",
            dataType : "json",
            async : false,
            success : function(data) {
                meetingInfo = data;
                //if(!meetingInfo) meetingInfo = [];
            },
            error : function(xmlHttpRequest,status,error) {
            	BBBUtils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            }
        });
        return meetingInfo;
    }
    
    // Log an event indicating user is joining meeting
    BBBUtils.joinMeeting = function(meetingId, linkSelector) { 
        jQuery.ajax( {
            url: "/direct/bbb-meeting/"+meetingId+"/joinMeeting",
            async : false,
            success : function(url) {
            	BBBUtils.hideMessage();
            	if(linkSelector) {
            		jQuery(linkSelector).attr('href', url);
					$('#meeting_joinlink_' + meetingId).hide();
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
    	jQuery.ajax( {
            url: "/direct/bbb-meeting/getServerTimeInUserTimezone.json",
            dataType : "json",
            async : true,
            success : function(timestamp) {
            	bbbServerTimeDiff = new Date().getTime() - timestamp;
            }
        });
    }
    
    // Check meetings availability and update meeting details page if appropriate
    BBBUtils.checkMeetingsAvailability = function() {
    	for(var i=0,j=bbbCurrentMeetings.length;i<j;i++) {
            var meeting = bbbCurrentMeetings[i];
        	BBBUtils.setAdditionalMeetingParams(meeting);
            if(meeting.joinable) {
				var meetingInfo = BBBUtils.getMeetingInfo(meetingi.id);

            	for(var p=0; p<meetingInfo.attendees.length; p++) {
                	if(bbbCurrentUser.id === meetingInfo.attendees[p].userID) {
            			jQuery('#meeting_joinlink_'+meeting.id).fadeIn();
					}
          		}

            	jQuery('#meeting_status_'+meeting.id)
            	   .removeClass()
            	   .addClass('status_inprogress')
            	   .text(bbb_status_inprogress);

            }else if(meeting.notStarted) {
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
    }
    
    // Get notice message to be displayed on the UI (first time access)
    BBBUtils.addNotice = function() {
        jQuery.ajax( {
            url: "/direct/bbb-meeting/getNoticeText.json",
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
                url : "/direct/bbb-meeting/getUserSelectionOptions.json?siteId=" + bbbSiteId,
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
            },
            error : function(xmlHttpRequest,status,error) {
            	if(bbbCurrentUser.id == 'admin') {
            		// Workaround for SAK-18534
            		perms = ["bbb.create", "bbb.edit.any", "bbb.delete.any", "bbb.participate",
                             "site.upd", "site.viewRoster", "calendar.new", "calendar.revise.any", "calendar.delete.any"];
            	}else{
                    BBBUtils.handleError(bbb_err_get_user_permissions, xmlHttpRequest.status, xmlHttpRequest.statusText);
            	}
            }
        });
        return perms;
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
	BBBUtils.render = function(templateName,contextObject,output) {	
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

		if ( href.indexOf( "?") != -1) {
			var paramString = href.split( "?")[1];
			
			if(paramString.indexOf("#") != -1)
				paramString = paramString.split("#")[0];
				
			var params = paramString.split("&");

			for (var i = 0; i < params.length; ++i) {
				var name = params[i].split( "=")[0];
				var value = params[i].split( "=")[1];
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
        
    /** Hide message box */
    BBBUtils.hideMessage = function(id) {
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
            if(editor != null) {
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
    str += date.getUTCFullYear();
    if (format > 1) { str += "-" + zeropad(date.getUTCMonth() + 1); }
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

/** Automatically transfer focus to FCKEditor when loaded */
function FCKeditor_OnComplete(editorInstance) {editorInstance.Focus();}

