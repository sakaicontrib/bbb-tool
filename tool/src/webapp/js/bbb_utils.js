/**
 * Copyright (c) 2010 onwards - The Sakai Foundation
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

(function ($) {

    meetings.utils = {};

    meetings.utils.bbbUserSelectionOptions = null;

    // Get the current user from EB.
    meetings.utils.getSettings = function (siteId, callback) {

        $.ajax({
            url: "/direct/bbb-tool/getSettings.json?siteId=" + siteId,
            dataType: "json",
            success: function (s) {
                meetings.settings = s;
                callback();
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_curr_user, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
    };

    // Get a meeting.
    meetings.utils.getMeeting = function (meetingId) {

        var meeting = null;
        $.ajax({
            url: "/direct/bbb-tool/" + meetingId + ".json",
            dataType: "json",
            async: false,
            success: function (data) {
                meeting = data;
                meetings.utils.setMeetingPermissionParams(meeting);
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            }
        });
        return meeting;
    };

    // Get all site meetings.
    meetings.utils.getMeetingList = function (siteId) {

        var list = [];
        $.ajax({
            url: "/direct/bbb-tool.json?siteId=" + siteId,
            dataType: "json",
            async: false,
            success: function (m, status) {
                list = m['bbb-tool_collection'];
                if (!list) {
                    list = [];
                }
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_meeting_list, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return list;
    };

    // Upload selected file.
    meetings.utils.doUpload = function (files) {
        file = files.files[0];
        var fd = new FormData();
        fd.append('file', file);
        var url;

        jQuery.ajax({
            url: '/direct/bbb-tool/doUpload?siteId=' + meetings.startupArgs.siteId,
            data: fd,
            processData: false,
            contentType: false,
            type: 'POST',
            dataType: 'text',
            beforeSend: function (xmlHttpRequest) {
                $('#bbb_save,#bbb_cancel').attr('disabled', 'disabled');
                meetings.utils.showAjaxIndicator('#bbb_addFile_ajaxInd');
            },
            success: function (data) {
                url = data;
                $('#bbb_save,#bbb_cancel').attr('disabled', false);
                meetings.utils.hideAjaxIndicator('#bbb_addFile_ajaxInd');
                $("#fileUrl").val(url.substring(url.indexOf('/access')));
                $("#url").attr("href", url);
                $("#url").text(url.substring(url.lastIndexOf("/") + 1));
                $("#fileView").show();
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_do_upload, xmlHttpRequest.status, xmlHttpRequest.statusText);
                $('#bbb_save,#bbb_cancel').attr('disabled', false);
                meetings.utils.hideAjaxIndicator('#bbb_addFile_ajaxInd');
            }
        });
        return url;
    };

    // Remove uploaded file.
    meetings.utils.removeUpload = function (url, meetingId) {
        var response;
        var meetingID = '';
        if (typeof meetingId != undefined)
            meetingID = '&meetingId=' + meetingId;
        jQuery.ajax({
            url: '/direct/bbb-tool/removeUpload?url=' + url + meetingID,
            dataType: 'text',
            beforeSend: function (xmlHttpRequest) {
                $('#bbb_save,#bbb_cancel').attr('disabled', 'disabled');
                meetings.utils.showAjaxIndicator('#bbb_addFile_ajaxInd');
            },
            success: function (data) {
                response = data;
                $('#bbb_save,#bbb_cancel').attr('disabled', false);
                meetings.utils.hideAjaxIndicator('#bbb_addFile_ajaxInd');
                $("#fileUrl").val('');
                $("#selectFile").val('');
                $("#selectFile").attr("disabled", false);
                $("#fileView").hide();
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_remove_upload, xmlHttpRequest.status, xmlHttpRequest.statusText);
                $('#bbb_save,#bbb_cancel').attr('disabled', false);
                meetings.utils.hideAjaxIndicator('#bbb_addFile_ajaxInd');
            }
        });
        return response;
    }

    // Create a json representation of the meeting and post it to new on the bbb-tool provider.
    meetings.utils.addUpdateMeeting = function () {

        // Consolidate date + time fields.
        var today = new Date();
        var startMillis = 0,
            endMillis = 0;
        if ($('#startDate1').prop('checked')) {
            var date = $('#startDate2').datepick('getDate');
            var time = $('#startTime').val().split(':');
            startMillis = date.getTime();
            startMillis += time[0] * 60 * 60 * 1000;
            startMillis += time[1] * 60 * 1000;
            startMillis -= date.getTimezoneOffset() * 60 * 1000;
            startMillis += (parseInt(meetings.startupArgs.timezoneoffset) * -1);
            date.setTime(startMillis);

            $('#startDate').val(startMillis);
        } else {
            $('#startDate').removeAttr('name');
            $('#startDate').val(null);
            $('#addToCalendar').removeAttr('checked');
        }
        if ($('#endDate1').attr('checked')) {
            var date = $('#endDate2').datepick('getDate');
            var time = $('#endTime').val().split(':');
            endMillis = date.getTime();
            endMillis += time[0] * 60 * 60 * 1000;
            endMillis += time[1] * 60 * 1000;
            endMillis -= date.getTimezoneOffset() * 60 * 1000;
            endMillis += (parseInt(meetings.startupArgs.timezoneoffset) * -1);
            date.setTime(endMillis);

            $('#endDate').val(endMillis);
        } else {
            $('#endDate').removeAttr('name');
            $('#endDate').val(null);
        }

        // Validation.
        meetings.utils.hideMessage();
        var errors = false;

        // Validate title field.
        var meetingTitle = $('#bbb_meeting_name_field').val().replace(/^\s+/, '').replace(/\s+$/, '');
        if (meetingTitle == '') {
            meetings.utils.showMessage(bbb_err_no_title, 'warning');
            errors = true;
        }

        // Validate participants list.
        if ($('#selContainer tbody tr').length == 0) {
            meetings.utils.showMessage(bbb_err_no_participants, 'warning');
            errors = true;
        }

        // Validate date fields.
        if ($('#startDate1').prop('checked') && $('#endDate1').prop('checked')) {
            if (endMillis == startMillis) {
                meetings.utils.showMessage(bbb_err_startdate_equals_enddate, 'warning');
                errors = true;
            } else if (endMillis < startMillis) {
                meetings.utils.showMessage(bbb_err_startdate_after_enddate, 'warning');
                errors = true;
            }
        }

        // Get description/welcome msg from CKEditor.
        meetings.utils.updateFromInlineCKEditor('bbb_welcome_message_textarea');

        // Validate description length.
        var maxLength = meetings.settings.config.addUpdateFormParameters.descriptionMaxLength;
        var descriptionLength = $('#bbb_welcome_message_textarea').val().length;
        if (descriptionLength > maxLength) {
            meetings.utils.showMessage(bbb_err_meeting_description_too_long(maxLength, descriptionLength), 'warning');
            meetings.utils.makeInlineCKEditor('bbb_welcome_message_textarea', 'BBB', '480', '200');
            errors = true;
        }

        if (errors) return false;

        $('.bbb_site_member,.bbb_site_member_role').removeAttr('disabled');

        // Submit.
        var isNew = $("#isNew").val() == true || $("#isNew").val() == 'true';
        var actionUrl = $("#bbb_add_update_form").attr('action');
        var meetingId = $("#meetingId").val();
        jQuery.ajax({
            url: actionUrl,
            type: 'POST',
            dataType: 'text',
            data: $("#bbb_add_update_form").serializeArray(),
            beforeSend: function (xmlHttpRequest) {
                meetings.utils.hideMessage();
                $('#bbb_save,#bbb_cancel').attr('disabled', 'disabled');
                meetings.utils.showAjaxIndicator('#bbb_addUpdate_ajaxInd');
            },
            success: function (returnedMeetingId) {
                var _meetingId = returnedMeetingId ? returnedMeetingId : meetingId;
                var meeting = meetings.utils.getMeeting(_meetingId);
                meetings.currentMeetings.addUpdateMeeting(meeting);
                meetings.utils.hideAjaxIndicator('#bbb_addUpdate_ajaxInd');
                meetings.switchState('currentMeetings');
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.hideAjaxIndicator('#bbb_addUpdate_ajaxInd');
                $('#bbb_save,#bbb_cancel').removeAttr('disabled');
                if (isNew) {
                    meetings.utils.handleError(bbb_err_create_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                } else {
                    meetings.utils.handleError(bbb_err_update_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                }
            }
        });
    };

    // Get meeting info from BBB server.
    meetings.utils.setMeetingInfo = function (meeting, asyncmode, groupId) {

        if (typeof (asyncmode) === 'undefined') asyncmode = true;

        var meetingInfo = null;
        var groupID = groupId ? "?groupId=" + groupId : "";
        jQuery.ajax({
            url: "/direct/bbb-tool/" + meeting.id + "/getMeetingInfo.json" + groupID,
            dataType: "json",
            async: asyncmode,
            timeout: 10000,
            success: function (data) {},
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            },
            complete: function (xmlHttpRequest, status) {
                if (xmlHttpRequest.responseText == null)
                    meetingInfo = {};
                else
                    meetingInfo = JSON.parse(xmlHttpRequest.responseText);
                meetings.utils.setMeetingInfoParams(meeting, meetingInfo);
                meetings.utils.setMeetingJoinableModeParams(meeting);
            }
        });
        return meetingInfo;
    };

    meetings.utils.getGroups = function (meeting) {
        var groups;
        jQuery.ajax({
            url: "/direct/bbb-tool/getGroups.json?meetingID=" + meeting.id,
            dataType: "json",
            async: false,
            timeout: 10000,
            success: function (data) {
                groups = data;
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            }
        });
        return groups;
    };

    meetings.utils.setMeetingsParams = function (meetingsInfo) {
        BBBMeetings = meetingsInfo.meetings ? meetingsInfo.meetings : [];

        for (var i = 0; i < meetings.currentMeetings.length; i++) {
            //Clear attendees
            if (meetings.currentMeetings[i].attendees && meetings.currentMeetings[i].attendees.length > 0)
                delete meetings.currentMeetings[i].attendees;
            if (meetings.currentMeetings[i].running)
                delete meetings.currentMeetings[i].running;
            meetings.currentMeetings[i].attendees = new Array();
            meetings.currentMeetings[i].hasBeenForciblyEnded = "false";
            meetings.currentMeetings[i].participantCount = 0;
            meetings.currentMeetings[i].moderatorCount = 0;
            meetings.currentMeetings[i].unreachableServer = "false";

            // Extend the meetings that are present in the BBBMeetings array.
            for (var j = 0; j < BBBMeetings.length; j++) {
                if (BBBMeetings[j].meetingID === meetings.currentMeetings[i].id) {
                    meetings.currentMeetings[i].hasBeenForciblyEnded = BBBMeetings[j].hasBeenForciblyEnded;
                    meetings.currentMeetings[i].participantCount = BBBMeetings[j].participantCount;
                    meetings.currentMeetings[i].running = BBBMeetings[j].running;
                }
                // Check if group session is active.
                else if ((BBBMeetings[j].meetingID).indexOf(meetings.currentMeetings[i].id) != -1 && BBBMeetings[j].participantCount != "0") {
                    meetings.currentMeetings[i].groupActive = true;
                }
            }

            if (meetingsInfo.returncode != 'SUCCESS')
                meetings.currentMeetings[i].unreachableServer = "true";

            // If joinable set the joinableMode.
            meetings.currentMeetings[i].joinableMode = "nojoinable";
            if (meetings.currentMeetings[i].joinable) {
                if (meetings.currentMeetings[i].unreachableServer == null) {
                    meetings.currentMeetings[i].joinableMode = "";
                } else if (meetings.currentMeetings[i].unreachableServer == "false") {
                    meetings.currentMeetings[i].joinableMode = "available";
                    if (meetings.currentMeetings[i].hasBeenForciblyEnded == "true") {
                        meetings.currentMeetings[i].joinableMode = "unavailable";
                    } else if (meetings.currentMeetings[i].running) {
                        meetings.currentMeetings[i].joinableMode = "inprogress";
                    }
                } else {
                    meetings.currentMeetings[i].joinableMode = "unreachable";
                }
            }

            // Update status in the view.
            var statusClass = meetings.currentMeetings[i].joinable ? 'status_joinable_' + meetings.currentMeetings[i].joinableMode : (meetings.currentMeetings[i].notStarted ? 'status_notstarted' : 'status_finished')
            var statusText = meetings.currentMeetings[i].joinable ? (meetings.currentMeetings[i].joinableMode == 'available' ? bbb_status_joinable_available : meetings.currentMeetings[i].joinableMode == 'inprogress' ? bbb_status_joinable_inprogress : meetings.currentMeetings[i].joinableMode == 'unavailable' ? bbb_status_joinable_unavailable : meetings.currentMeetings[i].joinableMode == 'unreachable' ? bbb_status_joinable_unreachable : '') : (meetings.currentMeetings[i].notStarted ? bbb_status_notstarted : bbb_status_finished);
            // If status is 'available', but a group is active, set status to 'inprogress'.
            if (statusText === bbb_status_joinable_available && meetings.currentMeetings[i].groupActive)
                statusText = bbb_status_joinable_inprogress;
            $('#meeting_status_' + meetings.currentMeetings[i].id).toggleClass(statusClass).html(statusText);
            // If meeting can be ended, update end action link in the view.
            if (meetings.currentMeetings[i].canEnd) {
                var end_meetingClass = "bbb_end_meeting_hidden";
                var end_meetingText = "";
                if (meetings.currentMeetings[i].groupActive || (meetings.currentMeetings[i].joinable && meetings.currentMeetings[i].joinableMode == 'inprogress')) {
                    end_meetingClass = "bbb_end_meeting_shown";
                    if (meetings.currentMeetings[i].groupSessions) {
                        end_meetingText = "&nbsp;|&nbsp;&nbsp;" + "<a href=\"javascript:;\" onclick=\"return meetings.utils.endMeeting('" + escape(meetings.currentMeetings[i].name) + "','" + meetings.currentMeetings[i].id + "', " + undefined + ", true);\" title=\"" + bbb_action_end_meeting_tooltip + "\">" + bbb_action_end_meeting + "</a>";
                    } else {
                        end_meetingText = "&nbsp;|&nbsp;&nbsp;" + "<a href=\"javascript:;\" onclick=\"return meetings.utils.endMeeting('" + escape(meetings.currentMeetings[i].name) + "','" + meetings.currentMeetings[i].id + "');\" title=\"" + bbb_action_end_meeting_tooltip + "\">" + bbb_action_end_meeting + "</a>";
                    }
                }
                $('#end_meeting_' + meetings.currentMeetings[i].id).toggleClass(end_meetingClass).html(end_meetingText);
            }
        }
    }

    meetings.utils.setMeetingInfoParams = function (meeting, meetingInfo) {
        // Clear attendees.
        if (meeting.attendees && meeting.attendees.length > 0) {
            delete meeting.attendees;
        }
        meeting.attendees = new Array();
        meeting.hasBeenForciblyEnded = "false";
        meeting.participantCount = 0;
        meeting.moderatorCount = 0;
        meeting.unreachableServer = "false";

        if (meetingInfo != null && meetingInfo.returncode != null) {
            if (meetingInfo.returncode != 'FAILED') {
                meeting.attendees = meetingInfo.attendees;
                meeting.hasBeenForciblyEnded = meetingInfo.hasBeenForciblyEnded;
                meeting.participantCount = meetingInfo.participantCount;
                meeting.moderatorCount = meetingInfo.moderatorCount;
                meeting.running = meetingInfo.running;
            } else if (meetingInfo.messageKey != 'notFound') {
                // Different errors can be handled here.
                meeting.unreachableServer = "true";
            }
        } else {
            delete meeting.running;
        }
    };

    meetings.utils.setRecordingPermissionParams = function (recording) {
        // Specific recording permissions.
        var offset = meetings.settings.config.serverTimeInDefaultTimezone.timezoneOffset;
        recording.timezoneOffset = "GMT" + (offset > 0 ? "+" : "") + (offset / 3600000);
        if (meetings.currentUser.id === recording.ownerId) {
            recording.canEdit = meetings.userPerms.bbbRecordingEditOwn || meetings.userPerms.bbbRecordingEditAny;
            recording.canDelete = meetings.userPerms.bbbRecordingDeleteOwn || meetings.userPerms.bbbRecordingDeleteAny;
            recording.canViewExtendedFormats = meetings.userPerms.bbbRecordingExtendedFormatsOwn || meetings.userPerms.bbbRecordingExtendedFormatsAny;
        } else {
            recording.canEdit = meetings.userPerms.bbbRecordingEditAny;
            recording.canDelete = meetings.userPerms.bbbRecordingDeleteAny;
            recording.canViewExtendedFormats = meetings.userPerms.bbbRecordingExtendedFormatsAny;
        }
    };

    meetings.utils.setMeetingPermissionParams = function (meeting) {
        // Joinable only if on specified date interval (if any).
        var serverTimeStamp = parseInt(meetings.settings.config.serverTimeInDefaultTimezone.timestamp);
        serverTimeStamp = (serverTimeStamp - serverTimeStamp % 1000);

        var startOk = !meeting.startDate || meeting.startDate == 0 || serverTimeStamp >= meeting.startDate;
        var endOk = !meeting.endDate || meeting.endDate == 0 || serverTimeStamp < meeting.endDate;

        meeting.notStarted = !startOk && endOk;
        meeting.finished = startOk && !endOk;
        meeting.joinable = startOk && endOk;

        // Specific meeting permissions.
        if (meetings.currentUser.id === meeting.ownerId) {
            meeting.canEdit = meetings.userPerms.bbbEditOwn || meetings.userPerms.bbbEditAny;
            meeting.canEnd = (meetings.userPerms.bbbEditOwn || meetings.userPerms.bbbEditAny) && (meetings.userPerms.bbbDeleteOwn || meetings.userPerms.bbbDeleteAny);
            meeting.canDelete = meetings.userPerms.bbbDeleteOwn || meetings.userPerms.bbbDeleteAny;
        } else {
            meeting.canEdit = meetings.userPerms.bbbEditAny;
            meeting.canEnd = meetings.userPerms.bbbEditAny && meetings.userPerms.bbbDeleteAny;
            meeting.canDelete = meetings.userPerms.bbbDeleteAny;
        }
    };

    meetings.utils.setMeetingJoinableModeParams = function (meeting) {

        // If joinable set the joinableMode.
        meeting.joinableMode = "nojoinable";
        if (meeting.joinable) {
            if (meeting.unreachableServer == null) {
                meeting.joinableMode = "";
            } else if (meeting.unreachableServer == "false") {
                meeting.joinableMode = "available";
                $('#meetingStatus').show();
                if (meeting.hasBeenForciblyEnded == "true") {
                    meeting.joinableMode = "unavailable";
                    $('#meetingStatus').hide();
                } else if (meeting.running) {
                    meeting.joinableMode = "inprogress";
                    if (!meeting.canEnd && (!meeting.multipleSessionsAllowed || !meetings.settings.config.addUpdateFormParameters.multiplesessionsallowedEnabled) && meetings.utils.isUserInMeeting(meetings.currentUser.displayName, meeting))
                        $('#meetingStatus').hide();
                }
            } else {
                meeting.joinableMode = "unreachable";
            }
        }

        // Update status in the view.
        var statusClass = meeting.joinable ? 'status_joinable_' + meeting.joinableMode : (meeting.notStarted ? 'status_notstarted' : 'status_finished')
        var statusText = meeting.joinable ? (meeting.joinableMode == 'available' ? bbb_status_joinable_available : meeting.joinableMode == 'inprogress' ? bbb_status_joinable_inprogress : meeting.joinableMode == 'unavailable' ? bbb_status_joinable_unavailable : meeting.joinableMode == 'unreachable' ? bbb_status_joinable_unreachable : '') : (meeting.notStarted ? bbb_status_notstarted : bbb_status_finished);
        $('#meeting_status_' + meeting.id).toggleClass(statusClass).html(statusText);
        // If meeting can be ended, update end action link in the view.
        if (meeting.canEnd) {
            var end_meetingClass = "bbb_end_meeting_hidden";
            var end_meetingText = "";
            if (meeting.joinable && meeting.joinableMode == 'inprogress') {
                end_meetingClass = "bbb_end_meeting_shown";
                if (meeting.groupSessions) {
                    end_meetingText = "&nbsp;|&nbsp;&nbsp;" + "<a href=\"javascript:;\" onclick=\"return meetings.utils.endMeeting('" + escape(meeting.name) + "','" + meeting.id + "', " + undefined + ", true);\" title=\"" + bbb_action_end_meeting_tooltip + "\">" + bbb_action_end_meeting + "</a>";
                } else {
                    end_meetingText = "&nbsp;|&nbsp;&nbsp;" + "<a href=\"javascript:;\" onclick=\"return meetings.utils.endMeeting('" + escape(meeting.name) + "','" + meeting.id + "');\" title=\"" + bbb_action_end_meeting_tooltip + "\">" + bbb_action_end_meeting + "</a>";
                }
            }
            $('#end_meeting_' + meeting.id).toggleClass(end_meetingClass).html(end_meetingText);
        }
    };

    // End the specified meeting. The name parameter is required for the confirm dialog.
    meetings.utils.endMeeting = function (name, meetingID, groupID, endAll) {
        var question;
        if (endAll) {
            question = bbb_action_end_all_meeting_question(unescape(name));
        } else {
            question = bbb_action_end_meeting_question(unescape(name));
        }
        if (!confirm(question)) return;

        var groupId = groupID ? "&groupId=" + groupID : "";
        var endAllMeetings = endAll ? "&endAll=true" : "";
        jQuery.ajax({
            url: "/direct/bbb-tool/endMeeting?meetingID=" + meetingID + groupId + endAllMeetings,
            dataType: 'text',
            type: "GET",
            success: function (result) {
                meetings.utils.checkOneMeetingAvailability(meetingID, groupID);
            },
            error: function (xmlHttpRequest, status, error) {
                var msg = bbb_err_end_meeting(name);
                meetings.utils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
    };

    // Delete the specified meeting. The name parameter is required for the confirm dialog.
    meetings.utils.deleteMeeting = function (name, meetingId) {

        var question = bbb_action_delete_meeting_question(unescape(name));

        if (!confirm(question)) return;

        jQuery.ajax({
            url: "/direct/bbb-tool/" + meetingId,
            dataType: 'text',
            type: "DELETE",
            success: function (result) {
                // Remove the meeting from the cached meeting array.
                for (var i = 0, j = meetings.currentMeetings.length; i < j; i++) {
                    if (meetingId === meetings.currentMeetings[i].id) {
                        meetings.currentMeetings.splice(i, 1);
                        break;
                    }
                }

                meetings.switchState('currentMeetings');
            },
            error: function (xmlHttpRequest, status, error) {
                var msg = bbb_err_end_meeting(name);
                meetings.utils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
    };

    // Delete the specified recording from the BigBlueButton server. The name parameter is required for the confirm dialog.
    meetings.utils.deleteRecordings = function (meetingID, recordID, stateFunction, confirmationMsg) {

        var question = bbb_action_delete_recording_question(unescape(confirmationMsg));

        if (!confirm(question)) return;

        jQuery.ajax({
            url: "/direct/bbb-tool/deleteRecordings?meetingID=" + meetingID + "&recordID=" + recordID,
            dataType: 'text',
            type: "GET",
            success: function (result) {
                if (stateFunction == 'recordings')
                    meetings.switchState('recordings');
                else
                    meetings.switchState('recordings_meeting', {
                        'meetingId': meetingID
                    });
            },
            error: function (xmlHttpRequest, status, error) {
                var msg = bbb_err_delete_recording(recordID);
                meetings.utils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
    };

    // Publish the specified recording from the BigBlueButton server.
    meetings.utils.publishRecordings = function (meetingID, recordID, stateFunction) {
        meetings.utils.setRecordings(meetingID, recordID, "true", stateFunction);
    };

    // Unpublish the specified recording from the BigBlueButton server.
    meetings.utils.unpublishRecordings = function (meetingID, recordID, stateFunction) {
        meetings.utils.setRecordings(meetingID, recordID, "false", stateFunction);

    };

    // Publish the specified recording from the BigBlueButton server.
    meetings.utils.setRecordings = function (meetingID, recordID, action, stateFunction) {

        jQuery.ajax({
            url: "/direct/bbb-tool/publishRecordings?meetingID=" + meetingID + "&recordID=" + recordID + "&publish=" + action,
            dataType: 'text',
            type: "GET",
            success: function (result) {
                if (stateFunction == 'recordings')
                    meetings.switchState('recordings');
                else
                    meetings.switchState('recordings_meeting', {
                        'meetingId': meetingID
                    });
            },
            error: function (xmlHttpRequest, status, error) {
                if (action == 'true')
                    var msg = bbb_err_publish_recording(recordID);
                else
                    var msg = bbb_err_unpublish_recording(recordID);
                meetings.utils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
    };

    meetings.utils.protectRecordings = function (meetingID, recordID, stateFunction) {
        meetings.utils.updateRecordings(meetingID, recordID, "true", stateFunction);
    };

    meetings.utils.unprotectRecordings = function (meetingID, recordID, stateFunction) {
        meetings.utils.updateRecordings(meetingID, recordID, "false", stateFunction);
    }

    // Protect the specified recording from the BigBlueButton server.
    meetings.utils.updateRecordings = function (meetingID, recordID, action, stateFunction) {

        jQuery.ajax({
            url: "/direct/bbb-tool/protectRecordings?meetingID=" + meetingID + "&recordID=" + recordID + "&protect=" + action,
            dataType: 'text',
            type: "GET",
            success: function (result) {
                if (stateFunction == 'recordings')
                    meetings.switchState('recordings');
                else
                    meetings.switchState('recordings_meeting', {
                        'meetingID': meetingID
                    });
            },
            error: function (xmlHttpRequest, status, error) {
                if (action == 'true')
                    var msg = bbb_err_protect_recording(recordID);
                else
                    var msg = bbb_err_unprotect_recording(recordID);
                meetings.utils.handleError(msg, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
    };

    // Get meeting info from BBB server.
    meetings.utils.getMeetingInfo = function (meetingId, groupId, asynch) {
        var groupID = groupId ? "?groupId=" + groupId : "";
        var meetingInfo = null;
        if (typeof asynch == 'undefined') asynch = true;
        jQuery.ajax({
            url: "/direct/bbb-tool/" + meetingId + "/getMeetingInfo.json" + groupID,
            dataType: "json",
            async: asynch,
            success: function (data) {
                meetingInfo = data;
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
                return null;
            }
        });
        return meetingInfo;
    };

    // Get site recordings from BBB server.
    meetings.utils.getSiteRecordingList = function (siteId) {

        if (siteId == null) siteId = "";

        var response = Object();
        jQuery.ajax({
            url: "/direct/bbb-tool/getSiteRecordings.json?siteId=" + siteId,
            dataType: "json",
            async: false,
            success: function (data) {
                response = data;
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_recording, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return response;
    };

    // Get meeting recordings from BBB server.
    meetings.utils.getMeetingRecordingList = function (meetingId, groupId) {

        if (meetingId == null) meetingId = "";

        var groupID = groupId ? "?groupId=" + groupId : "";

        var response = Object();
        jQuery.ajax({
            url: "/direct/bbb-tool/" + meetingId + "/getRecordings.json" + groupID,
            dataType: "json",
            async: false,
            success: function (data) {
                response = data;
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_meeting, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });
        return response;
    };

    // Log an event indicating user is joining meeting.
    meetings.utils.joinMeeting = function (meetingId, linkSelector, multipleSessionsAllowed, groupId, groupTitle) {

        var nonce = new Date().getTime();
        var url = "/direct/bbb-tool/" + meetingId + "/joinMeeting?nonce=" + nonce;
        url += groupId ? "&groupId=" + groupId : "";
        url += groupTitle ? "&groupTitle=" + groupTitle : "";
        meetings.utils.hideMessage();
        if (linkSelector) {
            $(linkSelector).attr('href', url);
            if (!multipleSessionsAllowed) {
                $('#meeting_joinlink_' + meetingId).hide();
                $('#meetingStatus').hide();
            }
            //.After joining stop requesting periodic updates.
            clearInterval(meetings.checkOneMeetingAvailabilityId);
            clearInterval(meetings.checkRecordingAvailabilityId);

            // After joining execute requesting updates only once.
            var onceAutorefreshInterval = meetings.settings.config.autorefreshInterval.meetings > 0 ? meetings.settings.config.autorefreshInterval.meetings : 15000;
            var groupID = groupId ? ", '" + groupId + "'" : "";
            meetings.updateMeetingOnceTimeoutId = setTimeout("meetings.utils.checkOneMeetingAvailability('" + meetingId + "'" + groupID + ")", onceAutorefreshInterval);
        }
        return true;
    };

    // Check if a user is already logged on a meeting.
    meetings.utils.isUserInMeeting = function (userName, meeting) {

        for (var p = 0; p < meeting.attendees.length; p++) {
            if (meetings.currentUser.displayName === meeting.attendees[p].fullName) {
                return true;
            }
        }
        return false;
    };

    // Check ONE meetings availability and update meeting details page if appropriate.
    meetings.utils.checkOneMeetingAvailability = function (meetingId, groupId) {

        if (typeof (groupId) === 'undefined') {
            for (var i = 0, j = meetings.currentMeetings.length; i < j; i++) {
                if (meetings.currentMeetings[i].id == meetingId) {
                    meetings.utils.setMeetingInfo(meetings.currentMeetings[i], false);
                    meetings.utils.checkMeetingAvailability(meetings.currentMeetings[i]);
                    meetings.updateMeetingInfo(meetings.currentMeetings[i]);
                    $("#end_session_link").attr("onclick", "return meetings.utils.endMeeting('" + meetings.currentMeetings[i].name + "', '" + meetings.currentMeetings[i].id + "');");
                    return;
                }
            }
        } else {
            var currentMeeting = meetings.utils.getMeeting(meetingId);
            meetings.utils.setMeetingInfo(currentMeeting, false, groupId);
            meetings.utils.checkMeetingAvailability(currentMeeting);
            meetings.updateMeetingInfo(currentMeeting);
            $("#end_session_link").attr("onclick", "return meetings.utils.endMeeting('" + currentMeeting.name + "','" + currentMeeting.id + "', '" + groupId + "');");
            return;
        }
    };

    // Check ALL meetings availability and update meeting details page if appropriate.
    meetings.utils.checkAllMeetingAvailability = function () {

        for (var i = 0, j = meetings.currentMeetings.length; i < j; i++) {
            if (!meetings.currentMeetings[i].joinable) {
                meetings.utils.setMeetingInfo(meetings.currentMeetings[i]);
            }
            meetings.utils.setMeetingJoinableModeParams(meetings.currentMeetings[i]);
            meetings.utils.checkMeetingAvailability(meetings.currentMeetings[i]);
        }
    };

    // Check specific meeting availability and update meeting details page if appropriate.
    meetings.utils.checkMeetingAvailability = function (meeting) {

        if (meeting.joinable) {
            if (meeting.joinableMode === "available") {
                if (meeting.multipleSessionsAllowed && meetings.settings.config.addUpdateFormParameters.multiplesessionsallowedEnabled) {
                    $('#meeting_joinlink_' + meeting.id).show();
                } else {
                    if (!meetings.utils.isUserInMeeting(meetings.currentUser.displayName, meeting)) {
                        $('#meeting_joinlink_' + meeting.id).show();
                    } else {
                        $('#meeting_joinlink_' + meeting.id).hide();
                    }
                }
                // Update the actionbar on the list.
                if (meeting.canEnd) {
                    $('#end_meeting_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_hidden');
                    $('#end_meeting_intermediate_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_hidden');
                }
                // Update for list.
                $('#meeting_status_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_available')
                    .text(bbb_status_joinable_available);
                // Update for detail.
                $('#meeting_status_joinable_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_available')
                    .text(bbb_status_joinable_available);
            } else if (meeting.joinableMode === "inprogress") {
                var end_meetingTextIntermediate = "&nbsp;|&nbsp;&nbsp;<a id=\"end_session_link\" href=\"javascript:;\" onclick=\"return meetings.utils.endMeeting('" + escape(meeting.name) + "','" + meeting.id + "');\" title=\"" + bbb_action_end_meeting_tooltip + "\" style=\"font-weight:bold\">" + bbb_action_end_meeting + "</a>&nbsp;<span><i class=\"fa fa-stop\"></i></span>";
                if (meeting.multipleSessionsAllowed && meetings.settings.config.addUpdateFormParameters.multiplesessionsallowedEnabled) {
                    $('#meeting_joinlink_' + meeting.id).show();
                } else {
                    if (!meetings.utils.isUserInMeeting(meetings.currentUser.displayName, meeting)) {
                        $('#meeting_joinlink_' + meeting.id).show();
                    } else {
                        $('#meeting_joinlink_' + meeting.id).hide();
                        end_meetingTextIntermediate = "<a id=\"end_session_link\" href=\"javascript:;\" onclick=\"return meetings.utils.endMeeting('" + escape(meeting.name) + "','" + meeting.id + "');\" title=\"" + bbb_action_end_meeting_tooltip + "\" style=\"font-weight:bold\">" + bbb_action_end_meeting + "</a>&nbsp;<span><i class=\"fa fa-stop\"></i></span>";
                    }
                }
                $('#end_meeting_intermediate_' + meeting.id).toggleClass("bbb_end_meeting_shown").html(end_meetingTextIntermediate);

                // Update the actionbar on the list.
                if (meeting.canEnd) {
                    $('#end_meeting_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_shown');
                    $('#end_meeting_intermediate_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_shown');
                }
                // Update for list.
                $('#meeting_status_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_inprogress')
                    .text(bbb_status_joinable_inprogress);
                // Update for detail.
                $('#meeting_status_joinable_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_inprogress')
                    .text(bbb_status_joinable_inprogress);

            } else if (meeting.joinableMode === "unavailable") {
                $('#meeting_joinlink_' + meeting.id).fadeOut();
                // Update the actionbar on the list.
                if (meeting.canEnd) {
                    $('#end_meeting_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_hidden');
                    $('#end_meeting_intermediate_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_hidden');
                }
                // Update for list.
                $('#meeting_status_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_unavailable')
                    .text(bbb_status_joinable_unavailable);
                // Update for detail.
                $('#meeting_status_joinable_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_unavailable')
                    .text(bbb_status_joinable_unavailable);

                $('#bbb_meeting_info_participants_count').html('0');
                $('#bbb_meeting_info_participants_count_tr').fadeOut();
                $('#bbb_meeting_info_participants_count_tr').hide();

            } else if (meeting.joinableMode === "unreachable") {
                $('#meeting_joinlink_' + meeting.id).fadeOut();
                // Update the actionbar on the list.
                if (meeting.canEnd) {
                    $('#end_meeting_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_hidden');
                    $('#end_meeting_intermediate_' + meeting.id)
                        .removeClass()
                        .addClass('bbb_end_meeting_hidden');
                }
                // Update for list.
                $('#meeting_status_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_unreachable')
                    .text(bbb_status_joinable_unreachable);
                // Update for detail.
                $('#meeting_status_joinable_' + meeting.id)
                    .removeClass()
                    .addClass('status_joinable_unreachable')
                    .text(bbb_status_joinable_unreachable);

                $('#bbb_meeting_info_participants_count').html('0');
                $('#bbb_meeting_info_participants_count_tr').fadeOut();
                $('#bbb_meeting_info_participants_count_tr').hide();
            }
        } else if (meeting.notStarted) {
            $('#meeting_joinlink_' + meeting.id).fadeOut();
            $('#meeting_status_' + meeting.id)
                .removeClass()
                .addClass('status_notstarted')
                .text(bbb_status_notstarted);
        } else if (meeting.finished) {
            $('#meeting_joinlink_' + meeting.id).fadeOut();
            $('#meeting_status_' + meeting.id)
                .removeClass()
                .addClass('status_finished')
                .text(bbb_status_finished);

        }
    };

    // Check ONE recording availability and update recording details page if appropriate.
    meetings.utils.checkOneRecordingAvailability = function (meetingId) {

        for (var i = 0, j = meetings.currentMeetings.length; i < j; i++) {
            var meeting = meetings.currentMeetings[i];
            if (meeting.id == meetingId)
                meetings.utils.checkRecordingAvailability(meeting);
        }
    };

    // Check ALL recording availability and update meeting details page if appropriate.
    meetings.utils.checkAllRecordingAvailability = function () {

        for (var i = 0, j = meetings.currentMeetings.length; i < j; i++) {
            meetings.utils.checkRecordingAvailability(meetings.currentMeetings[i]);
        }
    };

    meetings.utils.checkRecordingAvailability = function (meetingId, groupId) {

        var recordings = meetings.utils.getMeetingRecordingList(meetingId, groupId).recordings;
        if (recordings == null) {
            meetings.utils.showMessage(bbb_err_get_recording, 'warning');
        } else {
            meetings.utils.hideMessage();
            var meetingRecordingEnabled = true;
            for (var i = 0; i < meetings.currentMeetings.length; i++) {
                if (meetings.currentMeetings[i].id === meetingId) {
                    meetingRecordingEnabled = meetings.currentMeetings[i].recording;
                }
            }
            if (!meetings.userPerms.bbbRecordingView || !meetings.settings.config.addUpdateFormParameters.recordingEnabled || !meetingRecordingEnabled) {
                $('#meeting_recordings').hide();
            } else {
                $('#meeting_recordings').show();
                var htmlRecordings = "";
                var groupID = groupId ? "', 'groupId':'" + groupId : "";
                if (recordings.length > 0)
                    htmlRecordings = '(<a href="javascript:;" onclick="return meetings.switchState(\'recordings_meeting\',{\'meetingId\':\'' + meetingId + groupID + '\'})" title="">' + bbb_meetinginfo_recordings(unescape(recordings.length)) + '</a>)&nbsp;&nbsp;';
                else
                    htmlRecordings = "(" + bbb_meetinginfo_recordings(unescape(recordings.length)) + ")";

                $('#recording_link_' + meetingId).html(htmlRecordings);
            }
        }
    };

    // Get notice message to be displayed on the UI (first time access).
    meetings.utils.addNotice = function () {

        jQuery.ajax({
            url: "/direct/bbb-tool/getNoticeText.json",
            dataType: "json",
            async: true,
            success: function (notice) {
                if (notice && notice.text) {
                    meetings.utils.showMessage(notice.text, notice.level);
                }
            }
        });
    };

    // Get the participant object associated with the current user.
    meetings.utils.getParticipantFromMeeting = function (meeting) {

        var userId = meetings.currentUser.id;
        var role = meetings.currentUser.roles != null ? meetings.currentUser.roles.role : null;
        if (meeting && meeting.participants) {
            // 1. we want to first check individual user selection as it may
            // override all/group/role selection...
            for (var i = 0; i < meeting.participants.length; i++) {
                if (meeting.participants[i].selectionType == 'user' &&
                    meeting.participants[i].selectionId == userId) {
                    return meeting.participants[i];
                }
            }

            // 2. ... then with group/role selection types...
            for (var i = 0; i < meeting.participants.length; i++) {
                if (meeting.participants[i].selectionType == 'role' &&
                    meeting.participants[i].selectionId == role) {
                    return meeting.participants[i];
                }
            }

            // 3. ... then go with 'all' selection type
            for (var i = 0; i < meeting.participants.length; i++) {
                if (meeting.participants[i].selectionType == 'all') {
                    return meeting.participants[i];
                }
            }

            // 4. If not found, just check if is superuser
            ///No need for this, the superadmin has always the maintainer role
            /*
            if (securityService.isSuperUser()) {
                return new Participant(Participant.SELECTION_USER, "admin", Participant.MODERATOR);
            }
            */
        }
        return null;
    }

    // Get user selection types.
    meetings.utils.getUserSelectionTypes = function () {

        var selTypes = {
            all: {
                id: 'all',
                title: bbb_seltype_all
            },
            user: {
                id: 'user',
                title: bbb_seltype_user
            },
            group: {
                id: 'group',
                title: bbb_seltype_group
            },
            role: {
                id: 'role',
                title: bbb_seltype_role
            }
        };
        return selTypes;
    };

    // Get user selection options from EB.
    meetings.utils.getUserSelectionOptions = function () {

        if (meetings.utils.bbbUserSelectionOptions == null) {
            jQuery.ajax({
                url: "/direct/bbb-tool/getUserSelectionOptions.json?siteId=" + meetings.startupArgs.siteId,
                dataType: "json",
                async: false,
                success: function (data) {
                    meetings.utils.bbbUserSelectionOptions = data;
                },
                error: function (xmlHttpRequest, status, error) {
                    meetings.utils.handleError(bbb_err_user_sel_options, xmlHttpRequest.status, xmlHttpRequest.statusText);
                }
            });
        }

        return meetings.utils.bbbUserSelectionOptions;
    };

    // Get the site permissions.
    meetings.utils.getSitePermissions = function () {
        var perms = [];
        jQuery.ajax({
            url: "/direct/site/" + meetings.startupArgs.siteId + "/perms/bbb.json",
            dataType: "json",
            async: false,
            success: function (data) {
                // BBB-145: As the format for perms changed in version 19, convert to the old format for backward compatibility.
                var p = data;
                if (typeof data.data === "undefined") {
                    p = {'data':data};
                }
                for (role in p.data) {
                    var permSet = {
                        'role': role
                    };

                    for (var i = 0, j = p.data[role].length; i < j; i++) {
                        var perm = p.data[role][i].replace(/\./g, "_");
                        eval("permSet." + perm + " = true");
                    }

                    perms.push(permSet);
                }
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_get_permissions, xmlHttpRequest.status, xmlHttpRequest.statusText);
            }
        });

        return perms;
    }

    // Set the site permissions.
    meetings.utils.setSitePermissions = function (boxesSelector, successCallback, errorCallback) {

        var boxes = $(boxesSelector);
        var myData = {};
        for (var i = 0, j = boxes.length; i < j; i++) {
            var box = boxes[i];
            if (box.checked)
                myData[box.id] = 'true';
            else
                myData[box.id] = 'false';
        }

        jQuery.ajax({
            url: "/direct/site/" + meetings.startupArgs.siteId + "/setPerms",
            type: 'POST',
            data: myData,
            async: false,
            dataType: 'text',
            success: function (data) {
                if (successCallback) successCallback();
            },
            error: function (xmlHttpRequest, status, error) {
                meetings.utils.handleError(bbb_err_set_permissions, xmlHttpRequest.status, xmlHttpRequest.statusText);
                if (errorCallback) errorCallback();
            }
        });
        return false;
    };

    // Convenience function for rendering a trimpath template.
    meetings.utils.render = function (templateName, contextObject, output) {

        contextObject._MODIFIERS = {};
        var templateNode = document.getElementById(templateName);
        var firstNode = templateNode.firstChild;
        var template = '';
        if (firstNode && (firstNode.nodeType === 8 || firstNode.nodeType === 4))
            template += templateNode.firstChild.data.toString();
        else
            template += templateNode.innerHTML.toString();

        var trimpathTemplate = TrimPath.parseTemplate(template, templateName);

        var render = trimpathTemplate.process(contextObject);

        if (output)
            document.getElementById(output).innerHTML = render;

        return render;
    };

    // Setup defaults for Ajax.
    meetings.utils.setupAjax = function () {

        jQuery.ajaxSetup({
            async: true,
            cache: false,
            timeout: 30000,
            complete: function (request, textStatus) {
                try {
                    if (request.status &&
                        request.status != 200 && request.status != 201 &&
                        request.status != 204 && request.status != 404 && request.status != 1223) {
                        if (request.status == 403) {
                            meetings.utils.handleError(bbb_err_no_permissions, request.status, request.statusText);
                            $('#bbb_content').empty();
                        } else {
                            // Handled by error() callbacks.
                        }
                    }
                } catch (e) {}
            }
        });
    };

    // Handle communication errors.
    meetings.utils.handleError = function (message, statusCode, statusMessage) {

        var severity = 'error';
        var description = '';
        if (statusCode || statusMessage) {
            description += bbb_err_server_response + ': ';
            if (statusMessage) description += statusMessage;
            if (statusCode) description += ' [' + bbb_err_code + ': ' + statusCode + ']';
        }
        if (message && (statusCode || statusMessage)) {
            meetings.utils.showMessage(description, severity, message);
        } else if (message) {
            meetings.utils.showMessage(description, severity, message);
        } else {
            meetings.utils.showMessage(description, severity);
        }
    };

    /**
     * Render a message with a specific severity
     * @argument msgBod: The message to be displayed
     * @argument severity: Message severity [optional, defaults to 'information')
     * @argument msgTitle: Message title [optional, defaults to nothing]
     */
    meetings.utils.showMessage = function (msgBody, severity, msgTitle, hideMsgBody) {

        var useAlternateStyle = true;
        if (typeof hideMsgBody == 'undefined' && msgTitle && msgBody) hideMsgBody = true;

        if (!meetings.errorLog[msgBody]) {
            meetings.errorLog[msgBody] = true;

            // severity
            var msgClass = null;
            if (!severity || severity == 'info' || severity == 'information')
                msgClass = !useAlternateStyle ? 'information' : 'messageInformation';
            else if (severity == 'success')
                msgClass = !useAlternateStyle ? 'success' : 'messageSuccess';
            else if (severity == 'warn' || severity == 'warning' || severity == 'error' || severity == 'fail')
                msgClass = !useAlternateStyle ? 'alertMessage' : 'messageError';

            // add contents
            var id = Math.floor(Math.random() * 1000);
            var msgId = 'msg-' + id;
            var msgDiv = $('<div class="bbb_message" id="' + msgId + '"></div>');
            var msgsDiv = $('#bbb_messages').append(msgDiv);
            var message = $('<div class="' + msgClass + '"></div>');
            if (msgTitle && msgTitle != '') {
                message.append('<h4>' + msgTitle + '</h4>');
                if (hideMsgBody) message.append('<span id="msgShowDetails-' + id + '">&nbsp;<small>(<a href="#" onclick="$(\'#msgBody-' + id + '\').slideDown();$(\'#msgShowDetails-' + id + '\').hide();return false;">' + bbb_err_details + '</a>)</small></span>');
            }
            $('<p class="closeMe">  (x) </p>').click(function () {
                meetings.utils.hideMessage(msgId);
            }).appendTo(message);
            if (msgBody) {
                var msgBodyContent = $('<div id="msgBody-' + id + '" class="content">' + msgBody + '</div>');
                message.append(msgBodyContent);
                if (hideMsgBody) msgBodyContent.hide();
            }

            // display, adjust frame height, scroll to top.
            msgDiv.html(message);
            msgsDiv.fadeIn();
            $('html, body').animate({
                scrollTop: 0
            }, 'slow');

        }
    };

    // Hide message box.
    meetings.utils.hideMessage = function (id) {

        delete meetings.errorLog;
        meetings.errorLog = new Object();
        if (id) {
            $('#' + id).fadeOut();
        } else {
            $('#bbb_messages').empty().hide();
        }
    };

    // Show an ajax indicator at the following DOM selector.
    meetings.utils.showAjaxIndicator = function (outputSelector) {

        $(outputSelector).empty()
            .html('<img src="/bbb-tool/images/ajaxload.gif" alt="..." class="bbb_imgIndicator"/>')
            .show();
    };

    // Hide the ajax indicator at the following DOM selector.
    meetings.utils.hideAjaxIndicator = function (outputSelector) {
        $(outputSelector).hide();
    };

    meetings.utils.makeInlineCKEditor = function (textAreaId, toolBarSet, width, height) {
      this.editor = sakai.editor.launch(textAreaId, "basic", width, height);
    };

    // Update data from inline FCKEditor.
    meetings.utils.updateFromInlineCKEditor = function (textAreaId) {

        if (typeof CKEDITOR != "undefined") {
            var editor = CKEDITOR.instances[textAreaId];
            if (editor != null) {
                if (editor.checkDirty()) {
                    editor.updateElement();
                    var ta_temp = document.createElement("textarea");
                    ta_temp.innerHTML = editor.getData().replace(/</g, "&lt;").replace(/>/g, "&gt;");
                    var decoded_html = ta_temp.value;
                    $('#' + textAreaId).text(decoded_html);
                }
                editor.destroy();
            }
        }
    };

    meetings.utils.setNotifictionOptions = function () {

        if ($('#notifyParticipants')[0].checked) {
            $('#notifyParticipants_iCalAttach_span').empty()
                .html('<br>' + bbb_notification_notify_ical + '&nbsp;<input id="iCalAttached" name="iCalAttached" type="checkbox" ' + (meetings.startupArgs.checkICalOption ? 'checked="checked" ' : ' ') + 'onclick="meetings.utils.setNotifictioniCalOptions();"/>&nbsp;<span id="notifyParticipants_iCalAlarm_span"></span>')
                .show();
            if (meetings.startupArgs.checkICalOption) {
              $('#notifyParticipants_iCalAlarm_span').empty()
                  .html('<br>' + bbb_notification_notify_ical_alarm + '&nbsp;<input id="iCalAlarmMinutes" name="iCalAlarmMinutes" type="text" value="30" style="width: 35px;" />&nbsp;' + bbb_notification_notify_ical_alarm_units)
                  .show();
            }
        } else {
            if ($('#iCalAttached')[0].checked) {
                // Hide the iCalAlarm.
                $('#notifyParticipants_iCalAlarm_span').empty().hide();
                // Uncheck the iCalAttach checkbox.
                $('#iCalAttached').removeAttr('checked');
            }
            // Hide the iCalAttach.
            $('#notifyParticipants_iCalAttach_span').empty().hide();
        }
    };

    meetings.utils.setNotifictioniCalOptions = function () {

        if ($('#iCalAttached')[0].checked) {
            $('#notifyParticipants_iCalAlarm_span').empty()
                .html('<br>' + bbb_notification_notify_ical_alarm + '&nbsp;<input id="iCalAlarmMinutes" name="iCalAlarmMinutes" type="text" value="30" style="width: 35px;" />&nbsp;' + bbb_notification_notify_ical_alarm_units)
                .show();
        } else {
            // Hide the iCalAlarm.
            $('#notifyParticipants_iCalAlarm_span').empty().hide();
        }

    };

})(jQuery);

/** Protoypes */
if (!Array.prototype.indexOf) {
    Array.prototype.indexOf = function (needle) {
        for (var i = 0; i < this.length; i++) {
            if (this[i] === needle) {
                return i;
            }
        }
        return -1;
    };
}

Array.prototype.indexOfMeeting = function (meeting) {

    if (meeting && meeting.id) {
        for (var i = 0; i < this.length; i++) {
            if (this[i].id != null && this[i].id == meeting.id) return i;
        }
    }
    return -1;
};

Array.prototype.addUpdateMeeting = function (meeting) {

    if (meeting && meeting.id) {
        var index = this.indexOfMeeting(meeting);
        if (index >= 0) {
            this[index] = meeting;
        } else {
            this.push(meeting);
        }
    } else if (meeting) {
        this.push(meeting);
    }
};

Date.prototype.stdTimezoneOffset = function () {
    var jan = new Date(this.getFullYear(), 0, 1);
    var jul = new Date(this.getFullYear(), 6, 1);
    return Math.max(jan.getTimezoneOffset(), jul.getTimezoneOffset());
};

Date.prototype.dst = function () {
    return this.getTimezoneOffset() < this.stdTimezoneOffset();
};

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
    if (!format) {
        var format = 6;
    }
    if (!offset) {
        var offset = 'Z';
        var date = this;
    } else {
        var d = offset.match(/([-+])([0-9]{2}):([0-9]{2})/);
        var offsetnum = (Number(d[2]) * 60) + Number(d[3]);
        offsetnum *= ((d[1] == '-') ? -1 : 1);
        var date = new Date(Number(Number(this) + (offsetnum * 60000)));
    }

    var zeropad = function (num) {
        return ((num < 10) ? '0' : '') + num;
    }

    var str = "";
    str += date.getFullYear();
    if (format > 1) {
        str += "-" + zeropad(date.getMonth() + 1);
    }
    if (format > 2) {
        if (format == 6) {
            str += "-" + zeropad(date.getDate());
        } else {
            str += "-" + zeropad(date.getUTCDate());
        }
    }
    if (format > 3) {
        if (format == 6) {
            str += " " + zeropad(date.getHours()) +
                ":" + zeropad(date.getMinutes());
        } else {
            str += "T" + zeropad(date.getUTCHours()) +
                ":" + zeropad(date.getUTCMinutes());
        }
    }
    if (format > 5) {
        var secs = Number(date.getUTCSeconds() + "." +
            ((date.getUTCMilliseconds() < 100) ? '0' : '') +
            zeropad(date.getUTCMilliseconds()));
        str += ":" + zeropad(secs);
    } else if (format > 4) {
        str += ":" + zeropad(date.getUTCSeconds());
    }

    if (format > 3 && format != 6) {
        str += offset;
    }
    return str;
}

if (!Array.prototype.indexOf) {
    Array.prototype.indexOf = function (elt /*, from*/ ) {

        var len = this.length >>> 0;

        var from = Number(arguments[1]) || 0;
        from = (from < 0) ?
            Math.ceil(from) :
            Math.floor(from);
        if (from < 0) {
            from += len;
        }

        for (; from < len; from++) {
            if (from in this && this[from] === elt) {
                return from;
            }
        }
        return -1;
    };
}
