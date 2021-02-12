/**
 * Copyright (c) 2009-2015 The Sakai Foundation
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

package org.sakaiproject.bbb.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Resource;

import org.apache.log4j.Logger;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.impl.bbbapi.BBBAPI;
import org.sakaiproject.bbb.impl.bbbapi.BaseBBBAPI;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.user.api.User;

/**
 * BBBAPIWrapper is the class responsible to interact with the BigBlueButton
 * API.
 *
 * @author Nuno Fernandes
 */
public class BBBAPIWrapper/* implements Runnable */{
    protected final Logger logger = Logger.getLogger(getClass());

    /** BBB API Version (full) */
    private String version = null;
    /** BBB API Version number */
    private float versionNumber = 0;
    /** BBB API Snapshot Version ? */
    private boolean versionSnapshot = false;

    /** BBB API version check interval (default to 5 min) */
    private long bbbVersionCheckInterval = 0;
    /** BBB UX auto close meeting window on exit */
    private boolean bbbAutocloseMeetingWindow = true;
    /** BBB API auto refresh interval for meetings (default to 0 sec means it is not activated) */
    private long bbbAutorefreshMeetings = 0;
    /** BBB API auto refresh interval for recordings(default to 0 sec means it is not activated) */
    private long bbbAutorefreshRecordings = 0;
    /** BBB UX flag to activate/deactivate recording feature for meetings (default to true) */
    private boolean bbbRecordingEnabled = true;
    /** BBB UX flag to activate/deactivate recording recording checkbox (default to true) */
    private boolean bbbRecordingEditable = true;
    /** BBB default value for 'recording' checkbox (default to false) */
    private boolean bbbRecordingDefault = false;
    /** BBB UX flag to activate/deactivate 'recording ready notifications' (default to false) */
    private boolean bbbRecordingReadyNotificationEnabled = false;
    /** BBB UX maximum length allowed for meeting description (default 2083) */
    private int bbbDescriptionMaxLength = 2048;
    /** BBB UX textBox type for meeting description (default ckeditor) */
    private String bbbDescriptionType = "ckeditor";
    /** BBB UX flag to activate/deactivate 'duration' box (default to false) */
    private boolean bbbDurationEnabled = false;
    /** BBB default value for 'duration' box (default 120 minutes) */
    private int bbbDurationDefault = 120;
    /** BBB UX flag to activate/deactivate 'wait for moderator' feature for meetings (default to true) */
    private boolean bbbWaitModeratorEnabled = true;
    /** BBB UX flag to activate/deactivate 'wait for moderator' checkbox (default to true) */
    private boolean bbbWaitModeratorEditable = true;
    /** BBB default value for 'wait for moderator' checkbox (default to true) */
    private boolean bbbWaitModeratorDefault = true;
    /** BBB UX flag to activate/deactivate 'Users can open multiple sessions' feature for meetings (default to false) */
    private boolean bbbMultipleSessionsAllowedEnabled = false;
    /** BBB UX flag to activate/deactivate 'Users can open multiple sessions' checkbox (default to true) */
    private boolean bbbMultipleSessionsAllowedEditable = true;
    /** BBB default value for 'Users can open multiple sessions' checkbox (default to false) */
    private boolean bbbMultipleSessionsAllowedDefault = false;
    /** BBB UX flag to activate/deactivate 'presentation' file input (default to true) */
    private boolean bbbPreuploadPresentationEnabled = true;
    /** BBB UX flag to activate/deactivate 'group sessions' feature for meetings (default to false) */
    private boolean bbbGroupSessionsEnabled = true;
    /** BBB UX flag to activate/deactivate 'group sessions' checkbox (default to true) */
    private boolean bbbGroupSessionsEditable = true;
    /** BBB default value for 'group sessions' checkbox (default to false) */
    private boolean bbbGroupSessionsDefault = false;
    /** BBB flag to activate/deactivate 'recording status' feature for meetings (default to false) */
    private boolean bbbRecordingStatsEnabled = false;
    /** Sakai userid used for linking events with users when 'recording status' feature is enabled (default to eid) */
    private String bbbRecordingStatsUserId = "eid";
    /** BBB flag to activate/deactivate 'recording format filter' feature for managing permissions on extended formats (default to true) */
    private boolean bbbRecordingFormatFilterEnabled = true;
    /** BBB list of formats allowed to be seen whotout applying a permissions filter (default to presentation,video) */
    private String bbbRecordingFormatFilterWhitelist = "presentation,video";

    /** BBB API */
    private BBBAPI api = null;

    private static String DEFAULT_BBB_URL = "http://test-install.blindsidenetworks.com/bigbluebutton";
    private static String DEFAULT_BBB_SALT = "8cd8ef52e8e101574e400365b55e11a6";

    @Resource private ServerConfigurationService config = null;
    @Resource private BBBStorageManager storageManager = null;

    // BBB API version check thread and semaphore
    private Thread bbbVersionCheckThread;
    private Object bbbVersionCheckThreadSemaphore = new Object();
    private boolean bbbVersionCheckThreadEnabled = false;
    private boolean bbbVersionCheckThreadRunning = false;

    private String bbbUrl;
    private String bbbSalt;

    // -----------------------------------------------------------------------
    // --- Initialization related methods ------------------------------------
    // -----------------------------------------------------------------------
    public void start() {
        if (logger.isDebugEnabled()) logger.debug("init()");

        String bbbUrlString = config.getString(BBBMeetingManager.CFG_URL, DEFAULT_BBB_URL);
        if (bbbUrlString == "") {
            logger.warn("No BigBlueButton server specified. The bbb.url property in sakai.properties must be set to a single url. There should be a corresponding shared secret value in the bbb.salt property.");
            return;
        }

        String bbbSaltString = config.getString(BBBMeetingManager.CFG_SALT, DEFAULT_BBB_SALT);
        if (bbbSaltString == "") {
            logger.warn("BigBlueButton shared secret was not specified! Use 'bbb.salt = your_bbb_shared_secret' in sakai.properties.");
            return;
        }

        // Clean Url.
        bbbUrl = bbbUrlString.substring(bbbUrlString.length()-1, bbbUrlString.length()).equals("/")? bbbUrlString: bbbUrlString + "/";
        bbbSalt = bbbSaltString;

        // api will always have a value, except when the url and salt were not configured.
        api = new BaseBBBAPI(bbbUrl, bbbSalt);

        bbbAutocloseMeetingWindow = config.getBoolean(BBBMeetingManager.CFG_AUTOCLOSE_WIN, bbbAutocloseMeetingWindow);
        bbbAutorefreshMeetings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHMEETINGS, (int) bbbAutorefreshMeetings);
        bbbAutorefreshRecordings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHRECORDINGS, (int) bbbAutorefreshRecordings);
        bbbRecordingEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDING_ENABLED, bbbRecordingEnabled);
        bbbRecordingEditable = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDING_EDITABLE, bbbRecordingEditable);
        bbbRecordingDefault = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDING_DEFAULT, bbbRecordingDefault);
        bbbRecordingReadyNotificationEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDINGREADYNOTIFICATION_ENABLED, bbbRecordingReadyNotificationEnabled);
        bbbDescriptionMaxLength = (int) config.getInt(BBBMeetingManager.CFG_DESCRIPTIONMAXLENGTH, bbbDescriptionMaxLength);
        bbbDescriptionType = (String) config.getString(BBBMeetingManager.CFG_DESCRIPTIONTYPE, bbbDescriptionType);
        bbbDurationEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_DURATION_ENABLED, bbbDurationEnabled);
        bbbDurationDefault = (int) config.getInt(BBBMeetingManager.CFG_DURATION_DEFAULT, bbbDurationDefault);
        bbbWaitModeratorEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_WAITMODERATOR_ENABLED, bbbWaitModeratorEnabled);
        bbbWaitModeratorEditable = (boolean) config.getBoolean(BBBMeetingManager.CFG_WAITMODERATOR_EDITABLE, bbbWaitModeratorEditable);
        bbbWaitModeratorDefault = (boolean) config.getBoolean(BBBMeetingManager.CFG_WAITMODERATOR_DEFAULT, bbbWaitModeratorDefault);
        bbbMultipleSessionsAllowedEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_MULTIPLESESSIONSALLOWED_ENABLED, bbbMultipleSessionsAllowedEnabled);
        bbbMultipleSessionsAllowedEditable = (boolean) config.getBoolean(BBBMeetingManager.CFG_MULTIPLESESSIONSALLOWED_EDITABLE, bbbMultipleSessionsAllowedEditable);
        bbbMultipleSessionsAllowedDefault = (boolean) config.getBoolean(BBBMeetingManager.CFG_MULTIPLESESSIONSALLOWED_DEFAULT, bbbMultipleSessionsAllowedDefault);
        bbbPreuploadPresentationEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_PREUPLOADPRESENTATION_ENABLED, bbbPreuploadPresentationEnabled);
        bbbGroupSessionsEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_GROUPSESSIONS_ENABLED, bbbGroupSessionsEnabled);
        bbbGroupSessionsEditable = (boolean) config.getBoolean(BBBMeetingManager.CFG_GROUPSESSIONS_EDITABLE, bbbGroupSessionsEditable);
        bbbGroupSessionsDefault = (boolean) config.getBoolean(BBBMeetingManager.CFG_GROUPSESSIONS_DEFAULT, bbbGroupSessionsDefault);
        bbbRecordingStatsEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDINGSTATS_ENABLED, bbbRecordingStatsEnabled);
        bbbRecordingFormatFilterEnabled = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDINGFORMATFILTER_ENABLED, bbbRecordingFormatFilterEnabled);
        bbbRecordingFormatFilterWhitelist = (String) config.getString(BBBMeetingManager.CFG_RECORDINGFORMATFILTER_WHITELIST, bbbRecordingFormatFilterWhitelist);
    }

    public void destroy() {
    }

    // -----------------------------------------------------------------------
    // --- BBB API wrapper methods -------------------------------------------
    // -----------------------------------------------------------------------
    public BBBMeeting createMeeting(BBBMeeting meeting)
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("createMeeting()");

        // Synchronized to avoid clashes with the allocator task
        synchronized (api) {
            meeting.setHostUrl(api.getUrl());
            return api.createMeeting(meeting, autocloseMeetingWindow(), isRecordingEnabled(), isRecordingReadyNotificationEnabled(), isPreuploadPresentationEnabled());
        }
    }

    public boolean isMeetingRunning(String meetingID)
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("isMeetingRunning()");

        if ( api == null ) {
            return false;
        }

        return api.isMeetingRunning(meetingID);
    }

    public Map<String, Object> getMeetings()
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getMeetings()");

        Map<String, Object> meetings = new HashMap<String, Object>();
        if ( api != null) {
            try{
                meetings = api.getMeetings();
            } catch ( BBBException e) {
                if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) ||
                        BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ||
                        BBBException.MESSAGEKEY_INVALIDRESPONSE.equals(e.getMessageKey()) ) {
                    meetings = responseError(e.getMessageKey(), e.getMessage() );
                }
            } catch ( Exception e) {
                meetings = responseError(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage() );
            }
        }

        return meetings;
    }

    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getMeetingInfo()");

        Map<String, Object> meetingInfoResponse = new HashMap<String, Object>();

        if ( api != null  ) {
            try{
                meetingInfoResponse = api.getMeetingInfo(meetingID, password);
            } catch ( BBBException e) {
                if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) ||
                        BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ||
                        BBBException.MESSAGEKEY_INVALIDRESPONSE.equals(e.getMessageKey()) ) {
                    meetingInfoResponse = responseError(e.getMessageKey(), e.getMessage() );
                }
            } catch ( Exception e) {
                meetingInfoResponse = responseError(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage() );
            }

        }

        return meetingInfoResponse;
    }

    public String getJoinMeetingURL(BBBMeeting meeting, User user, boolean isModerator)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getJoinMeetingURL()");

        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }

        String meetingID = meeting.getId();
        String userId = this.getUserId(user);
        String userDisplayName = user.getDisplayName();
        String password = meeting.getAttendeePassword();
        if (isModerator) {
            password = meeting.getModeratorPassword();
        }

        String joinMeetingURLResponse = api.getJoinMeetingURL(meetingID, userId, userDisplayName, password);
        return joinMeetingURLResponse;
    }

    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getRecordings()");

        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        Map<String, Object> recordingsResponse = new HashMap<String, Object>();
        try{
            recordingsResponse = api.getRecordings(meetingID);
        } catch ( BBBException e) {
            recordingsResponse = responseError(e.getMessageKey(), e.getMessage() );
            logger.debug("getRecordings.BBBException: message=" + e.getMessage());
        } catch ( Exception e) {
            recordingsResponse = responseError(BBBException.MESSAGEKEY_GENERALERROR, e.getMessage() );
            logger.debug("getRecordings.Exception: message=" + e.getMessage());
        }
        return recordingsResponse;
    }

    public boolean endMeeting(String meetingID, String password)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("endMeeting()");

        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        boolean endMeetingResponse = api.endMeeting(meetingID, password);
        return endMeetingResponse;
    }

    public boolean publishRecordings(String meetingID, String recordingID, String publish)
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("publishRecordings()");

        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        boolean publishRecordingsResponse = api.publishRecordings(meetingID, recordingID, publish);
        return publishRecordingsResponse;
    }

    public boolean protectRecordings(String meetingID, String recordingID, String protect)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("protectRecordings()");

        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        boolean protectRecordingsResponse = api.protectRecordings(meetingID, recordingID, protect);
        return protectRecordingsResponse;
    }

    public boolean deleteRecordings(String meetingID, String recordingID)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("publishRecordings()");

        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        boolean deleteRecordingsResponse = api.deleteRecordings(meetingID, recordingID);
        return deleteRecordingsResponse;
    }

    public void makeSureMeetingExists(BBBMeeting meeting)
    		throws BBBException {
        if ( api == null ) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        api.makeSureMeetingExists(meeting, autocloseMeetingWindow(), isRecordingEnabled(), isRecordingReadyNotificationEnabled(), isPreuploadPresentationEnabled());
    }


    // -----------------------------------------------------------------------
    // --- Utility methods ---------------------------------------------------
    // -----------------------------------------------------------------------
    public String getVersionString() {
        return version;
    }

    public float getVersionNumber() {
        return versionNumber;
    }

    public boolean isVersionSnapshot() {
        return versionSnapshot;
    }

    public long getAutorefreshForMeetings() {
        return bbbAutorefreshMeetings;
    }

    public long getAutorefreshForRecordings() {
        return bbbAutorefreshRecordings;
    }

    public boolean autocloseMeetingWindow() {
        return bbbAutocloseMeetingWindow;
    }

    public boolean isRecordingEnabled() {
        return bbbRecordingEnabled;
    }

    public boolean isRecordingEditable() {
        return bbbRecordingEditable;
    }

    public boolean getRecordingDefault() {
        return bbbRecordingDefault;
    }

    public boolean isRecordingReadyNotificationEnabled() {
        return bbbRecordingReadyNotificationEnabled;
    }

    public boolean isDurationEnabled() {
        return bbbDurationEnabled;
    }

    public int getDurationDefault() {
        return bbbDurationDefault;
    }

    public boolean isWaitModeratorEnabled() {
        return bbbWaitModeratorEnabled;
    }

    public boolean isWaitModeratorEditable() {
        return bbbWaitModeratorEditable;
    }

    public boolean getWaitModeratorDefault() {
        return bbbWaitModeratorDefault;
    }

    public boolean isMultipleSessionsAllowedEnabled() {
        return bbbMultipleSessionsAllowedEnabled;
    }

    public boolean isMultipleSessionsAllowedEditable() {
        return bbbMultipleSessionsAllowedEditable;
    }

    public boolean getMultipleSessionsAllowedDefault() {
        return bbbMultipleSessionsAllowedDefault;
    }

    public boolean isPreuploadPresentationEnabled() {
        return bbbPreuploadPresentationEnabled;
    }

    public boolean isGroupSessionsEnabled() {
        return bbbGroupSessionsEnabled;
    }

    public boolean isGroupSessionsEditable() {
        return bbbGroupSessionsEditable;
    }

    public boolean getGroupSessionsDefault() {
        return bbbGroupSessionsDefault;
    }

    public int getMaxLengthForDescription() {
        return bbbDescriptionMaxLength;
    }

    public String getTextBoxTypeForDescription() {
        return bbbDescriptionType;
    }

    public boolean isRecordingStatsEnabled() {
        return bbbRecordingStatsEnabled;
    }

    public String getRecordingStatsUserId() {
        return bbbRecordingStatsUserId;
    }

    public boolean isRecordingFormatFilterEnabled() {
        return bbbRecordingFormatFilterEnabled;
    }

    public String getRecordingFormatFilterWhitelist() {
        return bbbRecordingFormatFilterWhitelist;
    }

    private Map<String, Object> responseError(String messageKey, String message) {
        logger.debug("responseError: " + messageKey + ":" + message);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("returncode", "FAILED");
        map.put("messageKey", messageKey);
        map.put("message", message);
        return map;

    }

    private String getUserId(User user) {
        boolean recordingstatsEnabled = isRecordingStatsEnabled();
        if ( !recordingstatsEnabled ) {
            return null;
        }
        String recordingstatsUserId = getRecordingStatsUserId();
        if ( "eid".equals(recordingstatsUserId) ) {
            return user.getEid();
        }
        return user.getId();
    }
}
