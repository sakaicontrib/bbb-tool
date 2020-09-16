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

package org.sakaiproject.bbb.api;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.user.api.User;
/**
 * BBBMeetingManager is the API for managing BigBlueButton meetings.
 *
 * @author Adrian Fish, Nuno Fernandes
 */
public interface BBBMeetingManager {
    /** Entity prefix */
    public static final String ENTITY_PREFIX = "bbb-tool";

    /** Meetings tool ID */
    public final static String TOOL_ID = "sakai.bbb";

    /** Meetings tool Webapp */
    public final static String TOOL_WEBAPP = "/bbb-tool";

    // Tool Settings in sakai.properties.
    public final static String CFG_URL = "bbb.url";
    public final static String CFG_SALT = "bbb.salt";
    public final static String CFG_AUTOCLOSE_WIN = "bbb.autocloseMeetingWindow";
    public final static String CFG_VERSIONCHECKINTERVAL = "bbb.versionCheckInterval";
    public final static String CFG_NOTICE_TEXT = "bbb.notice.text";
    public final static String CFG_NOTICE_LEVEL = "bbb.notice.level";
    public final static String CFG_DEFAULT_PERMS_PRFX = "bbb.default.permissions.";
    public final static String CFG_DEFAULT_ALLUSERS = "bbb.default.participants.all_users";
    public final static String CFG_DEFAULT_OWNER = "bbb.default.participants.owner";
    public final static String CFG_AUTOREFRESHMEETINGS = "bbb.autorefresh.meetings";
    public final static String CFG_AUTOREFRESHRECORDINGS = "bbb.autorefresh.recordings";
    public final static String CFG_RECORDING_ENABLED = "bbb.recording.enabled";
    public final static String CFG_RECORDING_EDITABLE = "bbb.recording.editable";
    public final static String CFG_RECORDING_DEFAULT = "bbb.recording.default";
    public final static String CFG_RECORDINGREADYNOTIFICATION_ENABLED = "bbb.recordingready.enabled";
    public final static String CFG_DESCRIPTIONMAXLENGTH = "bbb.descriptionmaxlength";
    public final static String CFG_DESCRIPTIONTYPE = "bbb.descriptiontype";
    public final static String CFG_DURATION_ENABLED = "bbb.duration.enabled";
    public final static String CFG_DURATION_DEFAULT = "bbb.duration.default";
    public final static String CFG_WAITMODERATOR_ENABLED = "bbb.waitmoderator.enabled";
    public final static String CFG_WAITMODERATOR_EDITABLE = "bbb.waitmoderator.editable";
    public final static String CFG_WAITMODERATOR_DEFAULT = "bbb.waitmoderator.default";
    public final static String CFG_MULTIPLESESSIONSALLOWED_ENABLED = "bbb.multiplesessionsallowed.enabled";
    public final static String CFG_MULTIPLESESSIONSALLOWED_EDITABLE = "bbb.multiplesessionsallowed.editable";
    public final static String CFG_MULTIPLESESSIONSALLOWED_DEFAULT = "bbb.multiplesessionsallowed.default";
    public final static String CFG_PREUPLOADPRESENTATION_ENABLED = "bbb.preuploadpresentation.enabled";
    public final static String CFG_GROUPSESSIONS_ENABLED = "bbb.groupsessions.enabled";
    public final static String CFG_GROUPSESSIONS_EDITABLE = "bbb.groupsessions.editable";
    public final static String CFG_GROUPSESSIONS_DEFAULT = "bbb.groupsessions.default";
    public final static String CFG_RECORDINGSTATS_ENABLED = "bbb.recordingstats.enabled";
    public final static String CFG_RECORDINGSTATS_USERID = "bbb.recordingstats.userid";
    public final static String CFG_RECORDINGFORMATFILTER_ENABLED = "bbb.recordingformatfilter.enabled";
    public final static String CFG_RECORDINGFORMATFILTER_WHITELIST = "bbb.recordingformatfilter.whitelist";
    public final static String CFG_CHECKICALOPTION = "bbb.checkicaloption";

    // System Settings in sakai.properties.
    public final static String SYSTEM_UPLOAD_MAX = "content.upload.max";

    // Permissions
    public static final String FN_PREFIX = "bbb.";
    public static final String FN_CREATE = "bbb.create";
    public static final String FN_EDIT_OWN = "bbb.edit.own";
    public static final String FN_EDIT_ANY = "bbb.edit.any";
    public static final String FN_DELETE_OWN = "bbb.delete.own";
    public static final String FN_DELETE_ANY = "bbb.delete.any";
    public static final String FN_PARTICIPATE = "bbb.participate";
    public static final String FN_RECORDING_VIEW = "bbb.recording.view";
    public static final String FN_RECORDING_EDIT_OWN = "bbb.recording.edit.own";
    public static final String FN_RECORDING_EDIT_ANY = "bbb.recording.edit.any";
    public static final String FN_RECORDING_DELETE_OWN = "bbb.recording.delete.own";
    public static final String FN_RECORDING_DELETE_ANY = "bbb.recording.delete.any";
    public static final String FN_RECORDING_EXTENDEDFORMATS_OWN = "bbb.recording.extendedformats.own";
    public static final String FN_RECORDING_EXTENDEDFORMATS_ANY = "bbb.recording.extendedformats.any";
    public static final List<String> FUNCTIONS = Arrays.asList(new String[] { FN_CREATE,
            FN_EDIT_OWN, FN_EDIT_ANY, FN_DELETE_OWN, FN_DELETE_ANY, FN_PARTICIPATE,
            FN_RECORDING_VIEW, FN_RECORDING_EDIT_OWN, FN_RECORDING_EDIT_ANY,
            FN_RECORDING_DELETE_OWN, FN_RECORDING_DELETE_ANY,
            FN_RECORDING_EXTENDEDFORMATS_OWN, FN_RECORDING_EXTENDEDFORMATS_ANY });
    // Extra function used to enable admin interface in the client
    public static final String FN_ADMIN = "bbb.admin";

    // Log Events
    /** A meeting was created on the BBB server */
    public static final String EVENT_MEETING_CREATE = "bbb.create";
    /** A meeting was edited on the BBB server */
    public static final String EVENT_MEETING_EDIT = "bbb.edit";
    /** A meeting was ended on the BBB server */
    public static final String EVENT_MEETING_END = "bbb.end";
    /** An user joined a meeting on the BBB server */
    public static final String EVENT_MEETING_JOIN = "bbb.join";
    /** A recording was deleted on the BBB server */
    public static final String EVENT_RECORDING_DELETE = "bbb.recording.delete";
    /** A recording was published on the BBB server */
    public static final String EVENT_RECORDING_PUBLISH = "bbb.recording.publish";
    /** A recording was unpublished on the BBB server */
    public static final String EVENT_RECORDING_UNPUBLISH = "bbb.recording.unpublish";

    /** ALL Log Events */
    public static final String[] EVENT_KEYS = new String[] {
            EVENT_MEETING_CREATE, EVENT_MEETING_EDIT, EVENT_MEETING_END,
            EVENT_MEETING_JOIN };


    public static final boolean INCLUDE_DELETED_MEETINGS = true;
    public static final boolean NOT_INCLUDE_DELETED_MEETINGS = false;


    // -----------------------------------------------------------------------
    // --- BBB Implementation related methods --------------------------------
    // -----------------------------------------------------------------------
    /**
     * Get the meeting identified by the supplied meetingId
     */
    public BBBMeeting getMeeting(String meetingId)
    		throws SecurityException, Exception;

    /**
     * Returns the meetings for the supplied site that the current Sakai user
     * can participate in.
     *
     * @param siteId
     *            The site to retrieve meetings for
     * @return A list of BBBMeeting objects
     */
    public List<BBBMeeting> getSiteMeetings(String siteId)
            throws SecurityException, Exception;

    /**
     * Creates a meeting using the passed in object. Populates the id, password
     * and token fields of <code>meeting</code> with the data returned from BBB.
     *
     * @param meeting
     */
    public boolean createMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar, boolean iCalAttached, Long iCalAlarmMinutes)
            throws SecurityException, BBBException;

    /**
     * Updates an existing meeting using the passed in object. Passwords and
     * token fields of <code>meeting</code> won't be changed.
     *
     * @param meeting
     */
    public boolean updateMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar, boolean iCalAttached, Long iCalAlarmMinutes, boolean meetingOnly)
            throws SecurityException, BBBException;

    /**
     * Check the BigBlueButton server to see if the meeting is running (i.e.
     * there is someone in the meeting)
     */
    public boolean isMeetingRunning(String meetingID)
            throws BBBException;

    /**
     * Get all meetings from BBB server.
     */
    public Map<String, Object> getMeetings()
            throws BBBException;

    /**
     * Get live meeting details from BBB server.
     */
    public Map<String, Object> getMeetingInfo(String meetingID, String groupId)
            throws BBBException;

    /**
     * Get playback recordings from BBB server.
     */
    public Map<String, Object> getRecordings(String meetingID, String groupId, String siteId)
            throws BBBException;

    /**
     * Get ALL playback recordings from BBB server.
     */
    public Map<String, Object> getAllRecordings()
			throws BBBException;

    /**
     * Get ALL playback recordings from BBB server for the current Site.
     */
    public Map<String, Object> getSiteRecordings(String siteId)
            throws SecurityException, Exception;

    /**
     * Log an event indicating that the current user joined the specified
     * meeting
     */
    public void logMeetingJoin(String meetingID);

    /**
     * Currently clears up the Sakai records and endMeeting.
     */
    public boolean deleteMeeting(String id)
            throws SecurityException, BBBException;

    /**
     * Only executes endMeeting.
     */
    public boolean endMeeting(String id, String groupId, boolean endAll)
            throws SecurityException, BBBException;

    /**
     * Deletes a recording on the BBB server
     */
    public boolean deleteRecordings(String meetingID, String recordID)
            throws SecurityException, BBBException;

    /**
     * Publish and unpublish recordings using the publishRecordings api command
     */
    public boolean publishRecordings(String meetingID, String recordID,
            String publish) throws SecurityException, BBBException;

    /**
     * Protect and unprotect recordings using the *_______________* api command
     */
    public boolean protectRecordings(String meetingID, String recordID,
            String protect) throws SecurityException, BBBException;

    /**
     * Check if meeting is ready to be joined.
     */
    public void checkJoinMeetingPreConditions(BBBMeeting meeting)
            throws BBBException;

    // -----------------------------------------------------------------------
    // --- BBB Security related methods --------------------------------------
    // -----------------------------------------------------------------------

    /**
     * Returns true if the current user is a participant in the supplied
     * meeting.
     */
    public boolean isMeetingParticipant(BBBMeeting meeting);

    /**
     * Returns true if the current user can create meetings in the supplied
     * site.
     */
    public boolean getCanCreate(String siteId);

    /**
     * Returns true if the current user can edit the specified meeting in the
     * supplied site.
     */
    public boolean getCanEdit(String siteId, BBBMeeting meeting);

    /**
     * Returns true if the current user can delete the specified meeting in the
     * supplied site.
     */
    public boolean getCanDelete(String siteId, BBBMeeting meeting);

    /**
     * Returns true if the current user can participate on meetings in the
     * supplied site.
     */
    public boolean getCanParticipate(String siteId);

    /**
     * Returns true if the current user can view recordings in the supplied site
     */
    public boolean getCanView(String siteId);

    /**
     * Checks tool permissions in site, apply defaults if no perms set and
     * defaults set on sakai.properties.
     */
    public void checkPermissions(String siteId);

    // -----------------------------------------------------------------------
    // --- Public utility methods --------------------------------------------
    // -----------------------------------------------------------------------

    /**
     * Returns current server time (in milliseconds) in user timezone.
     */
    public Map<String, Object> getServerTimeInUserTimezone();

    /**
     * Returns current server time (in milliseconds) in user timezone.
     */
    public Map<String, Object> getServerTimeInDefaultTimezone();

    /**
     * Returns server version.
     */
    public Map<String, Object> getToolVersion();

    /**
     * Returns the text notice (if any) to be displayed on the first time the
     * tool is accessed by an user.
     */
    public String getNoticeText();

    /**
     * Returns the text notice level to be used when displaying the notice text
     * (info | warn | success).
     */
    public String getNoticeLevel();

    /**
     * Returns the url for joining to a meeting
     */
    public String getJoinUrl(BBBMeeting meeting, User user)
            throws SecurityException, Exception;

    /**
     * Returns true if participants were notified when recording was ready
     */
    public boolean recordingReady(String meetingId);

    /**
     * Returns bbb.autorefresh.meetings parameter set up on sakai.properties or the one set up by default.
     */
    public String getAutorefreshForMeetings();

    /**
     * Returns bbb.autorefresh.recordings parameter set up on sakai.properties or the one set up by default.
     */
    public String getAutorefreshForRecordings();

    public String isRecordingEnabled();

    public String isRecordingEditable();

    public String getRecordingDefault();

    public String isDurationEnabled();

    public String getDurationDefault();

    public String isWaitModeratorEnabled();

    public String isWaitModeratorEditable();

    public String getWaitModeratorDefault();

    public String isMultipleSessionsAllowedEnabled();

    public String isMultipleSessionsAllowedEditable();

    public String getMultipleSessionsAllowedDefault();

    public String isPreuploadPresentationEnabled();

    public String isGroupSessionsEnabled();

    public String isGroupSessionsEditable();

    public String getGroupSessionsDefault();

    public String getMaxLengthForDescription();

    public String getTextBoxTypeForDescription();

    public boolean databaseStoreMeeting(BBBMeeting meeting);

    public boolean databaseDeleteMeeting(BBBMeeting meeting);

    public Participant getParticipantFromMeeting(BBBMeeting meeting, String userId);

    public boolean isUserAllowedInLocation(String userId, String permission, String locationId);

    public String getUserRoleInSite(String userId, String siteId);

    public List<String> getUserGroupIdsInSite(String userId, String siteId);

    public boolean isRecordingFormatFilterEnabled();

    public String getRecordingFormatFilterWhitelist();
}
