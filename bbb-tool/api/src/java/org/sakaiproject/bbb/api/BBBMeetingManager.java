/**
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

package org.sakaiproject.bbb.api;

import java.util.List;
import java.util.Map;

import org.sakaiproject.bbb.api.BBBException;

/**
 * BBBMeetingManager is the API for managing BigBlueButton meetings.
 * 
 * @author Adrian Fish,Nuno Fernandes
 */
public interface BBBMeetingManager {
    /** Entity prefix */
    public static final String ENTITY_PREFIX = "bbb-tool";

    /** Meetings tool ID */
    public final static String TOOL_ID = "sakai.bbb";

    /** Meetings tool Webapp */
    public final static String TOOL_WEBAPP = "/bbb-tool";

    // sakai.properties Settings
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
    public final static String CFG_GETSITERECORDINGS = "bbb.getsiterecordings";
    public final static String CFG_RECORDING = "bbb.recording";
    public final static String CFG_DESCRIPTIONMAXLENGTH = "bbb.descriptionmaxlength";
    
    // Permissions
    public static final String FN_PREFIX = "bbb.";
    public static final String FN_CREATE = "bbb.create";
    public static final String FN_EDIT_OWN = "bbb.edit.own";
    public static final String FN_EDIT_ANY = "bbb.edit.any";
    public static final String FN_DELETE_OWN = "bbb.delete.own";
    public static final String FN_DELETE_ANY = "bbb.delete.any";
    public static final String FN_PARTICIPATE = "bbb.participate";
    public static final String[] FUNCTIONS = new String[] { FN_CREATE,
            FN_EDIT_OWN, FN_EDIT_ANY, FN_DELETE_OWN, FN_DELETE_ANY,
            FN_PARTICIPATE };

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
    public boolean createMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar)
            throws SecurityException, BBBException;

    /**
     * Updates an existing meeting using the passed in object. Passwords and
     * token fields of <code>meeting</code> won't be changed.
     * 
     * @param meeting
     */
    public boolean updateMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar)
            throws SecurityException, BBBException;

    /**
     * Check the BigBlueButton server to see if the meeting is running (i.e.
     * there is someone in the meeting)
     */
    public boolean isMeetingRunning(String meetingID) 
            throws BBBException;

    /**
     * Get live meeting details from BBB server.
     */
    public Map<String, Object> getMeetingInfo(String meetingID)
            throws BBBException;

    /**
     * Get playback recordings from BBB server.
     */
    public Map<String, Object> getRecordings(String meetingID)
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
    public boolean endMeeting(String id) 
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
     * Returns bbb.autorefresh.meetings parameter set up on sakai.properties or the one set up by default.
     */
    public String getAutorefreshForMeetings();

    /**
     * Returns bbb.autorefresh.recordings parameter set up on sakai.properties or the one set up by default.
     */
    public String getAutorefreshForRecordings();

    public String isRecordingEnabled();
    
    public String getMaxLengthForDescription();
    
    public boolean databaseStoreMeeting(BBBMeeting meeting); 

    public boolean databaseDeleteMeeting(BBBMeeting meeting);

}
