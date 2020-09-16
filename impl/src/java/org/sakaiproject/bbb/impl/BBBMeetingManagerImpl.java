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

package org.sakaiproject.bbb.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Resource;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.CalScale;
import java.time.Duration;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.RandomUidGenerator;
import net.fortuna.ical4j.util.UidGenerator;

import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.api.Participant;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.email.api.Attachment;
import org.sakaiproject.email.api.ContentType;
import org.sakaiproject.email.api.EmailAddress;
import org.sakaiproject.email.api.EmailMessage;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.email.api.EmailAddress.RecipientType;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.id.api.IdManager;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ResourceLoader;

import lombok.extern.slf4j.Slf4j;

/**
 * BBBMeetingManager is the API for managing BigBlueButton meetings.
 *
 * @author Adrian Fish,Nuno Fernandes
 */
@Slf4j
public class BBBMeetingManagerImpl implements BBBMeetingManager {

    // --- Spring injected services ---
    @Resource private BBBStorageManager storageManager = null;
    @Resource private BBBAPIWrapper bbbAPI = null;
    @Resource private UserDirectoryService userDirectoryService = null;
    @Resource private SiteService siteService = null;
    @Resource private EmailService emailService = null;
    @Resource private EventTrackingService eventTrackingService = null;
    @Resource private SecurityService securityService = null;
    @Resource private AuthzGroupService authzGroupService = null;
    @Resource private SessionManager sessionManager = null;
    @Resource private FunctionManager functionManager = null;
    @Resource private ServerConfigurationService serverConfigurationService = null;
    @Resource private PreferencesService preferencesService = null;
    @Resource private TimeService timeService = null;
    @Resource private IdManager idManager;

    // -----------------------------------------------------------------------
    // --- Initialization/Spring related methods -----------------------------
    // -----------------------------------------------------------------------
    public void init() {

        log.info("init()");

        // setup db tables
        storageManager.setupTables();

        // register security functions
        FUNCTIONS.forEach(f -> functionManager.registerFunction(f, true));

        // This has to be called after the BBB tables have been created
        bbbAPI.start();
    }

    // -----------------------------------------------------------------------
    // --- BBB Implementation related methods --------------------------------
    // -----------------------------------------------------------------------
    public BBBMeeting getMeeting(String meetingId)
    		throws SecurityException, Exception {
        BBBMeeting meeting = storageManager.getMeeting(meetingId);
        meeting = processMeeting(meeting);
        return meeting;
    }

    public List<BBBMeeting> getSiteMeetings(String siteId)
            throws SecurityException, Exception {
        List<BBBMeeting> filteredMeetings = new ArrayList<BBBMeeting>();

        // Grab all the meetings for this site
        List<BBBMeeting> meetings = storageManager.getSiteMeetings(siteId, NOT_INCLUDE_DELETED_MEETINGS);
        for (BBBMeeting meeting : meetings) {
            meeting = processMeeting(meeting);
            if (meeting != null) {
                // add to meeting list
                filteredMeetings.add(meeting);
            }
        }

        return filteredMeetings;
    }

    public boolean createMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar, boolean iCalAttached, Long iCalAlarmMinutes)
            throws SecurityException, BBBException {
        if (!getCanCreate(meeting.getSiteId())) {
            throw new SecurityException("You are not allowed to create meetings in this site");
        }

        // Due the old schema for internal loadbalancing the HostUrl must be not null
        meeting.setHostUrl("");

        // store locally, in DB
        if (storageManager.storeMeeting(meeting)) {
            // send email notifications to participants
            if (notifyParticipants) {
                notifyParticipants(meeting, true, iCalAttached, iCalAlarmMinutes, false);
            }

            // add start date to Calendar
            if (addToCalendar && meeting.getStartDate() != null) {
                addEditCalendarEvent(meeting);
            }

            // log event
            logEvent(EVENT_MEETING_CREATE, meeting);

            return true;
        } else {
            bbbAPI.endMeeting(meeting.getId(), meeting.getModeratorPassword());
            log.error("Unable to store BBB meeting in Sakai");
            return false;
        }
    }

    public boolean updateMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar, boolean iCalAttached, Long iCalAlarmMinutes, boolean meetingOnly)
            throws SecurityException, BBBException {
        if (!getCanEdit(meeting.getSiteId(), meeting)) {
            throw new SecurityException("You are not allow to update this meeting");
        }

        // store locally, in DB
        if (storageManager.updateMeeting(meeting, true)) {
            //if meetingOnly is true, only update meeting properties
            if (!meetingOnly) {
                // send email notifications to participants
                if (notifyParticipants) {
                    notifyParticipants(meeting, false, iCalAttached, iCalAlarmMinutes, false);
                }
                // add start date to Calendar
                if (addToCalendar && meeting.getStartDate() != null) {
                    addEditCalendarEvent(meeting);
                }
                // or remove it, if 'add to calendar' was unselected
                else if (meeting.getProps().getCalendarEventId() != null && !addToCalendar) {
                    removeCalendarEvent(meeting);
                    storageManager.updateMeeting(meeting, false);
                }
            }

            // set meeting join url (for moderator, which is current user)
            User user = userDirectoryService.getCurrentUser();
            String joinURL = bbbAPI.getJoinMeetingURL(meeting, user, true);
            meeting.setJoinUrl(joinURL);

            // log event
            logEvent(EVENT_MEETING_EDIT, meeting);

            return true;
        } else {
            log.error("Unable to update BBB meeting in Sakai");
            return false;
        }
    }

    public boolean isMeetingRunning(String meetingID)
            throws BBBException {
        return bbbAPI.isMeetingRunning(meetingID);
    }

    public Map<String, Object> getMeetings()
            throws BBBException {
        return bbbAPI.getMeetings();
    }

    public Map<String, Object> getMeetingInfo(String meetingID, String groupId)
            throws BBBException {
        BBBMeeting meeting = storageManager.getMeeting(meetingID);

        if(meeting.getGroupSessions() && groupId != "" && groupId != null)
            return bbbAPI.getMeetingInfo(meeting.getId() + "[" + groupId + "]", meeting.getModeratorPassword());

        return bbbAPI.getMeetingInfo(meeting.getId(), meeting.getModeratorPassword());
    }

    public Map<String, Object> getRecordings(String meetingID, String groupId, String siteId)
            throws BBBException {
        BBBMeeting meeting = storageManager.getMeeting(meetingID);

        Map<String, Object> recordings;
        if (!meeting.getRecording()) {
            //Mimic empty recordings object
            recordings = new HashMap<String, Object>();
            recordings.put("recordings", "");
            return recordings;
        }

        Map<String, String> ownerIDs = new HashMap<String, String>();
        String ownerID =  meeting.getOwnerId();
        ownerIDs.put(meetingID, ownerID);
        String meetingIDs = meetingID;
        if (meeting.getGroupSessions() && groupId != null && groupId != "") {
            ownerIDs.put(meetingID + "[" + groupId + "]", ownerID);
            meetingIDs += "," + meetingID + "[" + groupId + "]";

        }
        recordings = bbbAPI.getRecordings(meetingIDs);
        // Post-process recordings
        Object recordingList = recordings.get("recordings");
        if ("SUCCESS".equals(recordings.get("returncode")) && recordingList != null && recordingList.getClass().equals(ArrayList.class)) {
            boolean recordingFilterEnabled = this.isRecordingFormatFilterEnabled();
            User user = userDirectoryService.getCurrentUser();
            String userId = user.getId();
            for (Map<String, Object> recordingItem : (List<Map<String, Object>>)recordingList) {
                // Add meeting ownerId to the recording
                recordingItem.put("ownerId", ownerIDs.get((String)recordingItem.get("meetingID")));
                // Filter formats that are not allowed to be shown, only if filter is enabled.
                if (recordingFilterEnabled) {
                    recordingsFilterFormats(recordingItem, siteId, userId);
                }
            }
        }
        return recordings;
    }

    public Map<String, Object> getSiteRecordings(String siteId)
            throws SecurityException, Exception {
        Map<String, Object> recordings;
        List<BBBMeeting> meetings = storageManager.getSiteMeetings(siteId, INCLUDE_DELETED_MEETINGS);
        if (meetings.size() == 0 || !bbbAPI.isRecordingEnabled()) {
            // Set an empty List of recordings and a SUCCESS key as default response values.
            recordings = new HashMap<String, Object>();
            recordings.put("recordings", new ArrayList<Object>());
            recordings.put("returncode", "SUCCESS");
            recordings.put("messageKey", "noRecordings");
            return recordings;
        }

        Map<String, String> ownerIDs = new HashMap<String, String>();
        String meetingID;
        String ownerID;
        String meetingIDs = "";
        for (BBBMeeting meeting : meetings) {
            if (!meeting.getRecording()) {
                // Meeting is not set to be recorded
                continue;
            }
            meetingID = meeting.getId();
            ownerID = meeting.getOwnerId();
            ownerIDs.put(meetingID, ownerID);
            if (!meetingIDs.equals("")) {
                meetingIDs += ",";
            }
            meetingIDs += meetingID;
            if (meeting.getGroupSessions()) {
                try {
                    Site site = siteService.getSite(siteId);
                    Collection<Group> userGroups = site.getGroups();
                    for (Group g : userGroups) {
                        ownerIDs.put(meetingID + "[" + g.getId() + "]", ownerID);
                        meetingIDs += "," + meetingID + "[" + g.getId() + "]";
                    }
                } catch (IdUnusedException e) {
                    log.error("Unable to get recordings for group sessions in meeting '{}'.", meeting.getName(), e);
                }
            }
        }
        // Safety for BBB-148. Make sure meetingIDs is not empty.
        if (meetingIDs.equals("")) {
            // Set an empty List of recordings and a SUCCESS key as default response values.
            recordings = new HashMap<String, Object>();
            recordings.put("recordings", new ArrayList<Object>());
            recordings.put("returncode", "SUCCESS");
            recordings.put("messageKey", "noRecordings");
            return recordings;
        }
        // Pull the recordings from BBB.
        recordings = bbbAPI.getRecordings(meetingIDs);
        // Post-process recordings
        Object recordingList = recordings.get("recordings");
        if ("SUCCESS".equals(recordings.get("returncode")) && recordingList != null && recordingList.getClass().equals(ArrayList.class)) {
            boolean recordingFilterEnabled = this.isRecordingFormatFilterEnabled();
            User user = userDirectoryService.getCurrentUser();
            String userId = user.getId();
            for (Map<String, Object> recordingItem : (List<Map<String, Object>>)recordingList) {
                // Add meeting ownerId to the recording
                recordingItem.put("ownerId", ownerIDs.get((String)recordingItem.get("meetingID")));
                // Filter formats that are not allowed to be shown, only if filter is enabled.
                if (recordingFilterEnabled) {
                    recordingsFilterFormats(recordingItem, siteId, userId);
                }
            }
        }
        return recordings;
    }

    private void recordingsFilterFormats(Map<String, Object> recordingItem, String siteId, String userId) {
        List<Map<String, Object>> playback = (List<Map<String, Object>>)recordingItem.get("playback");
        for (Iterator<Map<String, Object>> iterator = playback.iterator(); iterator.hasNext(); ) {
            Map<String, Object> format = iterator.next();
            if ( recordingsFilterFormatRemovable( (String)format.get("type"), siteId, (String)recordingItem.get("ownerId"), userId ) ) {
                iterator.remove();
            }
        }
    }

    private boolean recordingsFilterFormatRemovable(String type, String siteId, String ownerId, String userId) {
        // Validate if type is whitelisted.
        List<String> whiltelist = Arrays.asList(this.getRecordingFormatFilterWhitelist().split(","));
        if (whiltelist.contains(type)) {
            // It is whitelisted, don't remove.
            return false;
        }
        // Validate if user is allowed to view extended formats
        if (ownerId.equals(userId)) {
            if (isUserAllowedInLocation(userId, FN_RECORDING_EXTENDEDFORMATS_OWN, siteId)) {
                // User allowed, don't remove.
                return false;
            }
        }
        if (isUserAllowedInLocation(userId, FN_RECORDING_EXTENDEDFORMATS_ANY, siteId)) {
            // User allowed, don't remove.
            return false;
        }
        // Remove it
        return true;
    }

    public Map<String, Object> getAllRecordings()
        throws BBBException {
        Map<String, Object> recordings = bbbAPI.getRecordings("");
        return recordings;
    }

    public void logMeetingJoin(String meetingId) {
        BBBMeeting meeting = storageManager.getMeeting(meetingId);
        logEvent(EVENT_MEETING_JOIN, meeting);
    }

    public boolean endMeeting(String meetingId, String groupId, boolean endAll)
    		throws SecurityException, BBBException {
        BBBMeeting meeting = storageManager.getMeeting(meetingId);

        if (!getCanEdit(meeting.getSiteId(), meeting)) {
            throw new SecurityException("You are not allowed to end this meeting");
        }

        // end meeting on server, if running
        if (meeting.getGroupSessions() && groupId != "" && groupId != null) {
            bbbAPI.endMeeting(meetingId + "[" + groupId + "]", meeting.getModeratorPassword());
        } else {
            bbbAPI.endMeeting(meetingId, meeting.getModeratorPassword());

            if(meeting.getGroupSessions() && endAll){
                //End all group sessions that could be running
                Site site;
                try {
                    site = siteService.getSite(meeting.getSiteId());
                    Collection<Group> userGroups = site.getGroups();
                    for (Group g : userGroups)
                        bbbAPI.endMeeting(meetingId + "[" + g.getId() + "]", meeting.getModeratorPassword());
                } catch (IdUnusedException e) {
                    log.error("Unable to end all group sessions for meeting '{}'.", meeting.getName(), e);
                    return false;
                }
            }
        }
        // log event
        logEvent(EVENT_MEETING_END, meeting);

        return true;
    }

    public boolean deleteMeeting(String meetingId)
    		throws SecurityException, BBBException {
        BBBMeeting meeting = storageManager.getMeeting(meetingId);

        if (!getCanDelete(meeting.getSiteId(), meeting)) {
            throw new SecurityException("You are not allow to end this meeting");
        }

        // end meeting on server, if running
        try{
            if(bbbAPI.isMeetingRunning(meetingId))
                bbbAPI.endMeeting(meetingId, meeting.getModeratorPassword());
        } catch( Exception e) {

        }

        // log event
        logEvent(EVENT_MEETING_END, meeting);

        // remove event from Calendar
        removeCalendarEvent(meeting);

        // remove from DB, if no exceptions were thrown
        storageManager.deleteMeeting(meetingId);
        return true;
    }

    public boolean deleteRecordings(String meetingID, String recordID)
    		throws SecurityException, BBBException {
        return bbbAPI.deleteRecordings(meetingID, recordID);
    }

    public boolean publishRecordings(String meetingID, String recordID, String publish)
    		throws SecurityException, BBBException {
        // publish or unpublish the recording
        return bbbAPI.publishRecordings(meetingID, recordID, publish);
    }

    public boolean protectRecordings(String meetingID, String recordID, String protect)
            throws SecurityException, BBBException {
        // protect or unprotect the recording
        return bbbAPI.protectRecordings(meetingID, recordID, protect);
    }

    public void checkJoinMeetingPreConditions(BBBMeeting meeting)
            throws BBBException {
        // check if meeting is within dates

        Site meetingSite = null;
        try{
            meetingSite = siteService.getSite(meeting.getSiteId() );
        } catch( Exception e) {
            log.warn("There is an error with the site in this meeting {}: ", meeting.getSiteId(), e.getMessage(), e);
        }

        Map<String, Object> serverTimeInDefaultTimezone = getServerTimeInDefaultTimezone();
        Date now = new Date(Long.parseLong((String) serverTimeInDefaultTimezone.get("timestamp")));

        boolean startOk = meeting.getStartDate() == null || meeting.getStartDate().before(now);
        boolean endOk = meeting.getEndDate() == null || meeting.getEndDate().after(now);

        if (!startOk)
            throw new BBBException(BBBException.MESSAGEKEY_NOTSTARTED, "Meeting has not started yet.");
        if (!endOk)
            throw new BBBException(BBBException.MESSAGEKEY_ALREADYENDED, "Meeting has already ended.");

        // Add the metadata to be used in case of create
        Map<String, String> tmpMeta = meeting.getMeta();
        if( !tmpMeta.containsKey("origin")) tmpMeta.put("origin", "Sakai");
        if( !tmpMeta.containsKey("originVersion")) tmpMeta.put("originVersion", serverConfigurationService.getString("version.sakai", ""));
        ResourceLoader toolParameters = new ResourceLoader("Tool");
        if( !tmpMeta.containsKey("originServerCommonName")) tmpMeta.put("originServerCommonName", serverConfigurationService.getServerName() );
        if( !tmpMeta.containsKey("originServerUrl")) tmpMeta.put("originServerUrl", serverConfigurationService.getServerUrl().toString() );
        if( !tmpMeta.containsKey("originTag")) tmpMeta.put("originTag", "Sakai[" + serverConfigurationService.getString("version.sakai", "") + "]" + BBBMeetingManager.TOOL_WEBAPP + "[" + toolParameters.getString("bbb_version") + '_' + toolParameters.getString("bbb_buildSerial") + "]" );
        if( !tmpMeta.containsKey("context")) tmpMeta.put("context", siteService.getSiteDisplay(meeting.getSiteId()) );
        if( !tmpMeta.containsKey("contextId")) tmpMeta.put("contextId", meeting.getSiteId() );
        if( !tmpMeta.containsKey("contextActivity")) tmpMeta.put("contextActivity", meeting.getName() );

        /*
         * //////////////////////////////////////////////////////////////////////////////////////////////////
         * //This implementation will work only for a small number of users enrolled (teachers or students)
         * //this is beacuse the long a GET call can be is limited by the configuration of the Webserver
         * //////////////////////////////////////////////////////////////////////////////////////////////////
         *

        Map<String, User> attendees = new HashMap<String, User>();
        Map<String, User> moderators = new HashMap<String, User>();
        List<Participant> participants = meeting.getParticipants();
        if( participants != null ){
            for( int i=0; i < participants.size(); i++){
                Participant participant = participants.get(i);
                if( (Participant.MODERATOR).equals(participant.getRole()) ){
                    moderators.putAll(getUsersParticipating(participant.getSelectionType(), participant.getSelectionId(), meetingSite));
                } else {
                    attendees.putAll(getUsersParticipating(participant.getSelectionType(), participant.getSelectionId(), meetingSite));
                }
            }
        }

        if( !tmpMeta.containsKey("meetingModerators")){
            String meetingModerator = "";
            for( Map.Entry<String, User> e: moderators.entrySet()){
                if( meetingModerator.length() > 0 ) meetingModerator += ", ";
                meetingModerator += e.getValue().getFirstName() + " " + e.getValue().getLastName() + " <" + e.getValue().getEmail() + ">";

            }
            tmpMeta.put("meetingModerator", meetingModerator);

        }
        if( !tmpMeta.containsKey("meetingAttendees")){
            String meetingAttendee = "";
            for( Map.Entry<String, User> e: attendees.entrySet()){
                if( meetingAttendee.length() > 0 ) meetingAttendee += ", ";
                meetingAttendee += e.getValue().getFirstName() + " " + e.getValue().getLastName() + " <" + e.getValue().getEmail() + ">";

            }
            tmpMeta.put("meetingAttendee", meetingAttendee);

        }
        */
        // Metadata ends

        // check if is running, (re)create it if not
        bbbAPI.makeSureMeetingExists(meeting);
    }



    // -----------------------------------------------------------------------
    // --- BBB Security related methods --------------------------------------
    // -----------------------------------------------------------------------
    public boolean isMeetingParticipant(BBBMeeting meeting) {
        User currentUser = userDirectoryService.getCurrentUser();
        return getParticipantFromMeeting(meeting, currentUser.getId()) != null;
    }

    public boolean getCanCreate(String siteId) {
        return isUserAllowedInLocation(userDirectoryService.getCurrentUser().getId(), FN_CREATE, siteId);
    }

    public boolean getCanEdit(String siteId, BBBMeeting meeting) {
        String currentUserId = userDirectoryService.getCurrentUser().getId();

        if (meeting != null) {
            // check if owns meeting
            if (currentUserId.equals(meeting.getOwnerId())) {
                if (isUserAllowedInLocation(currentUserId, FN_EDIT_OWN, siteId))
                    return true;
            }
        }

        // otherwise, must be able to edit any meeting
        return isUserAllowedInLocation(currentUserId, FN_EDIT_ANY, siteId);
    }

    public boolean getCanDelete(String siteId, BBBMeeting meeting) {
        String currentUserId = userDirectoryService.getCurrentUser().getId();

        if (meeting != null) {
            // check if owns meeting
            if (currentUserId.equals(meeting.getOwnerId())) {
                if (isUserAllowedInLocation(currentUserId, FN_DELETE_OWN, siteId))
                    return true;
            }
        }

        // otherwise, must be able to delete any meeting
        return isUserAllowedInLocation(currentUserId, FN_DELETE_ANY, siteId);
    }

    public boolean getCanParticipate(String siteId) {
        return isUserAllowedInLocation(userDirectoryService.getCurrentUser().getId(), FN_PARTICIPATE, siteId);
    }

    public boolean getCanView(String siteId) {
        return isUserAllowedInLocation(userDirectoryService.getCurrentUser().getId(), FN_RECORDING_VIEW, siteId);
    }

    public void checkPermissions(String siteId) {
        try {
            // get site roles & tool permisions
            Set<Role> roles = null;
            boolean noPermsDefined = true;
            // get site
            // final Site site = siteService.getSite(siteId);
            final AuthzGroup siteAuthz = authzGroupService.getAuthzGroup(siteService.siteReference(siteId));
            // get roles
            roles = siteAuthz.getRoles();
            if (roles == null || roles.size() == 0)
                throw new Exception("No roles defined in site!");
            // check if need to apply defaults (no perms defined)
            for (Role r : roles) {
                Set<String> functions = r.getAllowedFunctions();
                if (functions != null && functions.size() > 0) {
                    for (String fn : functions) {
                        if (fn.startsWith(FN_PREFIX)) {
                            noPermsDefined = false;
                            break;
                        }
                    }
                }
            }

            // no perms defined => apply defaults, if any
            boolean permsSet = false;
            if (noPermsDefined) {
                for (Role r : roles) {
                    String roleId = r.getId();
                    String fns_ = serverConfigurationService.getString(CFG_DEFAULT_PERMS_PRFX + roleId, null);
                    if (fns_ != null) {
                        String[] fns = fns_.split(",");
                        for (int i = 0; i < fns.length; i++) {
                            r.allowFunction(fns[i].trim());
                        }
                        permsSet = true;
                    }
                }
            }

            // apply (new) permissions, if defined
            if (permsSet) {
                AdminExecution exec = new AdminExecution() {
                    @Override
                    public Object execution() throws Exception {
                        authzGroupService.save(siteAuthz);
                        // siteService.save(site);
                        return null;
                    }
                };
                try {
                    exec.execute();
                } catch (Exception e) {
                    log.warn("Unable to apply default BBB permissions to site {}: ", siteId, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.warn("Unable to check tool permissions on site: {}", e.getMessage(), e);
            return;
        }
    }

    // -----------------------------------------------------------------------
    // --- Public utility methods --------------------------------------------
    // -----------------------------------------------------------------------
    public Map<String, Object> getServerTimeInUserTimezone() {

        Map<String, Object> responseMap = new HashMap<String,Object>();

        Preferences prefs = preferencesService.getPreferences(userDirectoryService.getCurrentUser().getId());
        TimeZone timeZone = TimeZone.getDefault();
        if (prefs != null) {
            ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
            String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
            if( timeZoneStr != null )
                timeZone = TimeZone.getTimeZone(timeZoneStr);
        }

        long timeMs = getTimeInTimezone(new Date(System.currentTimeMillis()), timeZone).getTime();

        responseMap.put("timestamp", "" + timeMs);
        responseMap.put("timezone", "" + timeZone.getDisplayName() );
        responseMap.put("timezoneID", "" + timeZone.getID() );
        responseMap.put("timezoneOffset", "" + timeZone.getOffset(timeMs));
        responseMap.put("defaultOffset", "" + TimeZone.getDefault().getOffset(timeMs));

        return responseMap;
    }

    public Map<String, Object> getServerTimeInDefaultTimezone() {

        Map<String, Object> responseMap = new HashMap<String,Object>();

        TimeZone timeZone = TimeZone.getDefault();

        long timeMs = getTimeInTimezone(new Date(System.currentTimeMillis()), timeZone).getTime();

        responseMap.put("timestamp", "" + timeMs);
        responseMap.put("timezone", "" + timeZone.getDisplayName() );
        responseMap.put("timezoneID", "" + timeZone.getID() );
        responseMap.put("timezoneOffset", "" + timeZone.getOffset(timeMs));
        responseMap.put("defaultOffset", "" + TimeZone.getDefault().getOffset(timeMs));

        return responseMap;
    }

    private Date getTimeInUserTimezone(Date time, String userId) {

        Preferences prefs = preferencesService.getPreferences(userId);
        TimeZone timeZone = TimeZone.getDefault();
        if (prefs != null) {
            ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
            String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
            if( timeZoneStr != null )
                timeZone = TimeZone.getTimeZone(timeZoneStr);
        }

        return getTimeInTimezone(time, timeZone);
    }

    private Date getTimeInDefaultTimezone(Date time) {

        TimeZone timeZone = TimeZone.getDefault();

        return getTimeInTimezone(time, timeZone);
    }

    private Date getTimeInTimezone(Date time, TimeZone timeZone) {

        long timeMs = time.getTime();
        timeMs =  timeMs                                   // server time in millis
                + timeZone.getOffset(timeMs)               // user timezone offset
                - TimeZone.getDefault().getOffset(timeMs); // server timezone offset

        return new Date(timeMs);
    }


    public Map<String, Object> getToolVersion() {

        Map<String, Object> responseMap = new HashMap<String,Object>();

        ResourceLoader toolParameters = new ResourceLoader("Tool");
        responseMap.put("version", toolParameters.getString("bbb_version") );
        responseMap.put("buildSerial", toolParameters.getString("bbb_buildSerial") );

        return responseMap;
    }

    public String getAutorefreshForMeetings() {
    	return "" + bbbAPI.getAutorefreshForMeetings();
    }

    public String getAutorefreshForRecordings() {
    	return "" + bbbAPI.getAutorefreshForRecordings();
    }

    public String isRecordingEnabled(){
        return "" + bbbAPI.isRecordingEnabled();
    }

    public String isRecordingEditable(){
        return "" + bbbAPI.isRecordingEditable();
    }

    public String getRecordingDefault(){
        return "" + bbbAPI.getRecordingDefault();
    }

    public String isDurationEnabled(){
        return "" + bbbAPI.isDurationEnabled();
    }

    public String getDurationDefault(){
        return "" + bbbAPI.getDurationDefault();
    }

    public String isWaitModeratorEnabled(){
        return "" + bbbAPI.isWaitModeratorEnabled();
    }

    public String isWaitModeratorEditable(){
        return "" + bbbAPI.isWaitModeratorEditable();
    }

    public String getWaitModeratorDefault(){
        return "" + bbbAPI.getWaitModeratorDefault();
    }

    public String isMultipleSessionsAllowedEnabled(){
        return "" + bbbAPI.isMultipleSessionsAllowedEnabled();
    }

    public String isMultipleSessionsAllowedEditable(){
        return "" + bbbAPI.isMultipleSessionsAllowedEditable();
    }

    public String getMultipleSessionsAllowedDefault(){
        return "" + bbbAPI.getMultipleSessionsAllowedDefault();
    }

    public String isGroupSessionsEnabled(){
        return "" + bbbAPI.isGroupSessionsEnabled();
    }

    public String isGroupSessionsEditable(){
        return "" + bbbAPI.isGroupSessionsEditable();
    }

    public String getGroupSessionsDefault(){
        return "" + bbbAPI.getGroupSessionsDefault();
    }

    public String isPreuploadPresentationEnabled(){
        return "" + bbbAPI.isPreuploadPresentationEnabled();
    }

    public String getMaxLengthForDescription(){
        return "" + bbbAPI.getMaxLengthForDescription();
    }

    public String getTextBoxTypeForDescription(){
        return "" + bbbAPI.getTextBoxTypeForDescription();
    }

    public boolean databaseStoreMeeting(BBBMeeting meeting) {
        if( meeting.getId() == null ){
            // generate uuid
            meeting.setId(idManager.createUuid());
        }
        return storageManager.storeMeeting(meeting);
    }

    public boolean databaseDeleteMeeting(BBBMeeting meeting) {
        storageManager.deleteMeeting(meeting.getId(), true);
        return false;
    }

    public String getNoticeText() {
        String bbbNoticeText = serverConfigurationService.getString(CFG_NOTICE_TEXT, null);
        if (bbbNoticeText != null && "".equals(bbbNoticeText.trim()))
            bbbNoticeText = null;
        return bbbNoticeText;
    }

    public String getNoticeLevel() {
        return serverConfigurationService.getString(CFG_NOTICE_LEVEL, "info").trim().toLowerCase();
    }

    public String getJoinUrl(BBBMeeting meeting, User user)
            throws SecurityException, Exception {
        if (meeting == null) return null;

        Participant p = getParticipantFromMeeting(meeting, userDirectoryService.getCurrentUser().getId());

        // Case #1: is participant
        if (getCanParticipate(meeting.getSiteId()) && p != null) {
            // build join url
            String joinURL = bbbAPI.getJoinMeetingURL(meeting, user, Participant.MODERATOR.equals(p.getRole()));
            return joinURL;
        }

        return null;
    }

    public boolean recordingReady(String meetingId) {
        String meetingID = null;
        String groupID = null;
        if (meetingId.contains("[")) {
            meetingID = meetingId.substring(0, meetingId.indexOf("["));
            groupID = meetingId.substring(meetingId.indexOf("[") + 1, meetingId.indexOf("]"));
        }

        BBBMeeting meeting = null;
        if (meetingID != null && groupID != null) {
            meeting = storageManager.getMeeting(meetingID);

            Site site;
            try {
                site = siteService.getSite(meeting.getSiteId());
            } catch (IdUnusedException e) {
                log.error("Unable to send recording ready notifications for meeting '{}'.", meeting.getName(), e);
                return false;
            }
            Group group = site.getGroup(groupID);
            meeting.setName(meeting.getName() + " (" + group.getTitle() + ")");
        } else {
            meeting = storageManager.getMeeting(meetingId);
        }

        if (meeting == null) return false;
        notifyParticipants(meeting, false, false, 0L, true);
        return true;
    }

    // -----------------------------------------------------------------------
    // --- BBB Private methods -----------------------------------------------
    // -----------------------------------------------------------------------
    private BBBMeeting processMeeting(BBBMeeting meeting)
            throws SecurityException, Exception {
        if (meeting == null) return null;

        // determine owner name
        if (meeting.getOwnerId() != null) {
            try {
                String ownerDisplayName = userDirectoryService.getUser(meeting.getOwnerId()).getDisplayName();
                meeting.setOwnerDisplayName(ownerDisplayName);
            } catch (UserNotDefinedException e) {
                meeting.setOwnerDisplayName(meeting.getOwnerId());
            }
        }

        // If MultipleSessionsAllowed is not enabled and the Default is set to true, override the meeting value with true.
        // This will enable all the meetings to allow any number of sessions per user
        if( !bbbAPI.isMultipleSessionsAllowedEnabled() && bbbAPI.getMultipleSessionsAllowedDefault() )
            meeting.setMultipleSessionsAllowed(Boolean.valueOf(true));

        Participant p = getParticipantFromMeeting(meeting, userDirectoryService.getCurrentUser().getId());

        // Case #1: is participant
        if (getCanParticipate(meeting.getSiteId()) && p != null) {
            meeting.setJoinUrl(null);

            return meeting;
        }

        // Case #2: is not a participant but can manage tool
        else if (getCanEdit(meeting.getSiteId(), meeting) || getCanDelete(meeting.getSiteId(), meeting)) {
            // reset join url
            meeting.setJoinUrl(null);

            return meeting;
        }

        return null;
    }

    private void notifyParticipants(BBBMeeting meeting, boolean isNewMeeting, boolean iCalAttached, long iCalAlarmMinutes, boolean recordingReady) {
        // Site title, url and directtool (universal) url for joining meeting
        Site site;
        try {
            site = siteService.getSite(meeting.getSiteId());
        } catch (IdUnusedException e) {
            log.error("Unable to send notifications for '{}' meeting participants", meeting.getName(), e);
            return;
        }
        String siteTitle = site.getTitle();
        String directToolJoinUrl = getDirectToolJoinUrl(meeting);
        // Meeting participants
        Set<User> meetingUsers = new HashSet<User>();
        for (Participant p : meeting.getParticipants()) {
            if (Participant.SELECTION_ALL.equals(p.getSelectionType())) {
                Set<String> siteUserIds = site.getUsers();
                for (String userId : siteUserIds) {
                    try {
                        meetingUsers.add(userDirectoryService.getUser(userId));
                    } catch (UserNotDefinedException e) {
                        log.warn("Unable to notify user '{}' about '{}' meeting", userId, meeting.getName(), e);
                    }
                }
                break;
            }
            if (Participant.SELECTION_GROUP.equals(p.getSelectionType())) {
                Group group = site.getGroup(p.getSelectionId());
                Set<String> groupUserIds = group.getUsers();
                for (String userId : groupUserIds) {
                    try {
                        meetingUsers.add(userDirectoryService.getUser(userId));
                    } catch (UserNotDefinedException e) {
                        log.warn("Unable to notify user '{}' about '{}' meeting", userId, meeting.getName(), e);
                    }
                }
            }
            if (Participant.SELECTION_ROLE.equals(p.getSelectionType())) {
                Set<String> roleUserIds = site.getUsersHasRole(p.getSelectionId());
                for (String userId : roleUserIds) {
                    try {
                        meetingUsers.add(userDirectoryService.getUser(userId));
                    } catch (UserNotDefinedException e) {
                        log.warn("Unable to notify user '{}' about '{}' meeting", userId, meeting.getName(), e);
                    }
                }
            }
            if (Participant.SELECTION_USER.equals(p.getSelectionType())) {
                String userId = p.getSelectionId();
                try {
                    meetingUsers.add(userDirectoryService.getUser(userId));
                } catch (UserNotDefinedException e) {
                    log.warn("Unable to notify user '{}' about '{}' meeting", userId, meeting.getName(), e);
                }
            }
        }

        ResourceLoader msgs = null;
        // iterate over all user locales found
        log.debug("Sending notifications to {} users", meetingUsers.size());
        for( User user : meetingUsers){
            String userId = user.getId();
            log.debug("User: {}", userId);
            String userLocale = getUserLocale(userId);

            if (true == isNewMeeting) {
                msgs = new ResourceLoader(userId, "EmailNotification");
            } else if (true == recordingReady){
                msgs = new ResourceLoader(userId, "EmailNotificationRecordingReady");
            } else {
                msgs = new ResourceLoader(userId, "EmailNotificationUpdate");
            }

            // Email message
            final String emailTitle = msgs.getFormattedMessage("email.title", new Object[] { siteTitle, meeting.getName() });
            StringBuilder msg = new StringBuilder();
            msg.append(msgs.getFormattedMessage("email.header", new Object[] {}));
            msg.append(msgs.getFormattedMessage("email.body", new Object[] {
                    siteTitle,
                    serverConfigurationService.getString("ui.institution"),
                    directToolJoinUrl, meeting.getName() }));
            String meetingOwnerEid = null;
            try {
                meetingOwnerEid = userDirectoryService.getUserEid(meeting.getOwnerId());
            } catch (UserNotDefinedException e1) {
                meetingOwnerEid = meeting.getOwnerId();
            }

            msg.append(msgs.getFormattedMessage("email.body.meeting_details",
                    new Object[] {
                            meeting.getName(),
                            meeting.getProps().getWelcomeMessage(),
                            meeting.getStartDate() == null ? "-" : getTimeInUserTimezone(meeting.getStartDate(), userId),
                            meeting.getEndDate() == null ? "-" : getTimeInUserTimezone(meeting.getEndDate(), userId),
                            meeting.getOwnerDisplayName() + " (" + meetingOwnerEid + ")" }));
            msg.append(msgs.getFormattedMessage("email.footer", new Object[] {
                    serverConfigurationService.getString("ui.institution"),
                    serverConfigurationService.getServerUrl() + "/portal",
                    siteTitle }));

            // Generate an ical to attach to email (if, at least, start date is defined)
            String icalFilename = iCalAttached? generateIcalFromMeetingInUserTimezone(meeting, iCalAlarmMinutes, userId): null;
            final File icalFile = icalFilename != null? new File(icalFilename): null;
            if (icalFile != null)
                icalFile.deleteOnExit();

            // Send (a single) email (per userId)!
            final String emailMessage = msg.toString();
            final User emailRecipients = user;
            try {
                new Thread(new Runnable() {
                    public void run() {
                        if (emailRecipients.getEmail() != null && !emailRecipients.getEmail().trim().equals("")) {
                            EmailMessage email = new EmailMessage();
                            email.setFrom(new EmailAddress("no-reply@"+ serverConfigurationService.getServerName(), serverConfigurationService.getString("ui.institution")));
                            email.setRecipients(RecipientType.TO, Arrays.asList(new EmailAddress(emailRecipients.getEmail(), emailRecipients.getDisplayName())));
                            email.addHeader("Content-Type", "text/html; charset=ISO-8859-1");
                            email.setContentType(ContentType.TEXT_HTML);
                            email.setSubject(emailTitle);
                            email.setBody(emailMessage);
                            if (icalFile != null && icalFile.canRead()) {
                                email.addAttachment(new Attachment(icalFile, "Calendar_Event.ics"));
                            }
                            try {
                                emailService.send(email);
                            } catch (Exception e) {
                                log.warn("Unable to send email notification to {} about new BBB meeting", emailRecipients.getEmail(), e);
                            }
                        }
                    }
                }).start();
            } catch (Exception e) {
                log.error("Unable to send {} notifications for '{}' meeting participants", userLocale, meeting.getName(), e);
            }

        }

    }

    private String getUserLocale(String userId) {

        Preferences prefs = preferencesService.getPreferences(userId);
        ResourceProperties locProps = prefs.getProperties(ResourceLoader.APPLICATION_ID);
        String localeString = locProps.getProperty(ResourceLoader.LOCALE_KEY);
        if (localeString == null) {
            localeString = Locale.getDefault().toString();
        }
        log.debug("Locale for user {} is {}", userId, localeString);
        return localeString;
    }

    private String getDirectToolJoinUrl(BBBMeeting meeting) {
        try {
            Site site = siteService.getSite(meeting.getSiteId());
            StringBuilder url = new StringBuilder();
            url.append(serverConfigurationService.getServerUrl());
            url.append("/portal");
            url.append("/directtool/");
            url.append(site.getToolForCommonId(TOOL_ID).getId());
            url.append("?state=joinMeeting&meetingId=");
            url.append(meeting.getId());
            return url.toString();
        } catch (IdUnusedException e) {
            log.warn("Unable to determine siteId from meeting with id: {}", meeting.getId(), e);
            StringBuilder url = new StringBuilder();
            url.append(serverConfigurationService.getServerUrl());
            url.append("/portal");
            return url.toString();
        }
    }

    private String getMeetingDescription(BBBMeeting meeting) {
        return meeting.getProps().getWelcomeMessage().replaceAll("\\<.*?>","");
    }

    @SuppressWarnings("deprecation")
    private boolean addEditCalendarEvent(BBBMeeting meeting) {
        log.debug("addEditCalendarEvent");
        String eventId = meeting.getProps().getCalendarEventId();
        boolean newEvent = eventId == null;
        try {
            // get CalendarService
            Object calendarService = ComponentManager.get("org.sakaiproject.calendar.api.CalendarService");

            // get site calendar
            String calendarRef = (String) calendarService.getClass().getMethod(
                    "calendarReference",
                    new Class[] { String.class, String.class }).invoke(
                    calendarService,
                    new Object[] { meeting.getSiteId(),
                            SiteService.MAIN_CONTAINER });
            Object calendar = calendarService.getClass().getMethod(
                    "getCalendar", new Class[] { String.class }).invoke(
                    calendarService, new Object[] { calendarRef });

            // build time range (with dates on user timezone - calendar does conversion)

            Time startTime = timeService.newTime(meeting.getStartDate().getTime());
            TimeRange range = null;
            if (meeting.getEndDate() != null) {
                Time endTime = timeService.newTime(meeting.getEndDate().getTime());
                range = timeService.newTimeRange(startTime, endTime);
            } else {
                range = timeService.newTimeRange(startTime);
            }

            // add or edit event
            Object event = null;
            if (newEvent) {
                event = calendar.getClass().getMethod("addEvent", new Class[] {}).invoke(calendar, new Object[] {});
                event.getClass().getMethod("setCreator", new Class[] {}).invoke(event, new Object[] {});
                eventId = (String) event.getClass().getMethod("getId", new Class[] {}).invoke(event, new Object[] {});
            } else {
                // EVENT_MODIFY_CALENDAR = "calendar.revise"
                String eventModify = (String) calendarService.getClass().getField("EVENT_MODIFY_CALENDAR").get(null);
                event = calendar.getClass().getMethod("getEditEvent", new Class[] { String.class, String.class }).invoke( calendar, new Object[] { eventId, eventModify });
            }

            // set event fields
            event.getClass().getMethod("setDisplayName", new Class[] { String.class }).invoke(event, new Object[] { meeting.getName() });
            event.getClass().getMethod("setDescription", new Class[] { String.class }).invoke(event, new Object[] { "Meeting '" + meeting.getName() + "' scheduled."/* meeting.getWelcome() */});
            event.getClass().getMethod("setType", new Class[] { String.class }).invoke(event, new Object[] { "Meeting" });
            event.getClass().getMethod("setRange", new Class[] { TimeRange.class }).invoke(event, new Object[] { range });
            event.getClass().getMethod("setModifiedBy", new Class[] {}).invoke(event, new Object[] {});
            event.getClass().getMethod("clearGroupAccess", new Class[] {}).invoke(event, new Object[] {});
            event.getClass().getMethod("setField", new Class[] { String.class, String.class }).invoke(event, new Object[] { "meetingId", meeting.getId() });

            // commit event
            calendar.getClass().getMethod( "commitEvent",
                    new Class[] { Class.forName("org.sakaiproject.calendar.api.CalendarEventEdit") }).invoke(calendar,
                    new Object[] { event });

            // update calendar eventId locally, in DB
            if (newEvent && eventId != null) {
                meeting.getProps().setCalendarEventId(eventId);
                if (!storageManager.updateMeeting(meeting, false)) {
                    log.error("Unable to update BBB meeting with Calendar.eventId in Sakai");
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Unable to add event to Calendar (no permissions or site has no Calendar tool).");
            return false;
        }
        return true;
    }

    private boolean removeCalendarEvent(BBBMeeting meeting) {
        log.debug("removeCalendarEvent");
        String eventId = meeting.getProps().getCalendarEventId();
        if (eventId != null) {
            try {
                // get CalendarService
                Object calendarService = ComponentManager.get("org.sakaiproject.calendar.api.CalendarService");

                // get site calendar
                String calendarRef = (String) calendarService.getClass()
                        .getMethod("calendarReference",
                                new Class[] { String.class, String.class })
                        .invoke(
                                calendarService,
                                new Object[] { meeting.getSiteId(),
                                        SiteService.MAIN_CONTAINER });
                Object calendar = calendarService.getClass().getMethod(
                        "getCalendar", new Class[] { String.class }).invoke(
                        calendarService, new Object[] { calendarRef });

                // remove event
                // EVENT_REMOVE_CALENDAR = "calendar.delete"
                String eventRemove = (String) calendarService.getClass()
                        .getField("EVENT_REMOVE_CALENDAR").get(null);
                Object event = calendar.getClass().getMethod("getEditEvent",
                        new Class[] { String.class, String.class }).invoke(
                        calendar, new Object[] { eventId, eventRemove });

                // commit event
                calendar.getClass().getMethod("removeEvent",
                        new Class[] { Class.forName("org.sakaiproject.calendar.api.CalendarEventEdit") }).invoke(calendar,
                        new Object[] { event });

                meeting.getProps().setCalendarEventId(null);
            } catch (Exception e) {
                log.warn("Unable to remove event from Calendar (no permissions or site has no Calendar tool).", e);
                return false;
            }
        }
        return true;
    }

    private void logEvent(String event, BBBMeeting meeting) {
        eventTrackingService.post(eventTrackingService.newEvent(event, meeting.getId(), meeting.getSiteId(), true, NotificationService.NOTI_OPTIONAL));
    }

    public Participant getParticipantFromMeeting(BBBMeeting meeting, String userId) {
        // 1. we want to first check individual user selection as it may
        // override all/group/role selection...
        List<Participant> unprocessed1 = new ArrayList<Participant>();
        for (Participant p : meeting.getParticipants()) {
            if (Participant.SELECTION_USER.equals(p.getSelectionType())) {
                if (userId.equals(p.getSelectionId()))
                    return p;
            } else {
                unprocessed1.add(p);
            }
        }

        // 2. ... then with group/role selection types...
        String userRole = getUserRoleInSite(userId, meeting.getSiteId());
        List<String> userGroups = getUserGroupIdsInSite(userId, meeting.getSiteId());
        List<Participant> unprocessed2 = new ArrayList<Participant>();
        for (Participant p : unprocessed1) {
            if (Participant.SELECTION_ROLE.equals(p.getSelectionType())) {
                if (userRole != null && userRole.equals(p.getSelectionId()))
                    return p;
            } else if (Participant.SELECTION_GROUP.equals(p.getSelectionType())) {
                if (userGroups.contains(p.getSelectionId()))
                    return p;
            } else {
                unprocessed2.add(p);
            }
        }

        // 3. ... then go with 'all' selection type
        for (Participant p : unprocessed2) {
            if (Participant.SELECTION_ALL.equals(p.getSelectionType()))
                return p;
        }

        // 4. If not found, just check if is superuser
        if (securityService.isSuperUser()) {
            return new Participant(Participant.SELECTION_USER, "admin", Participant.MODERATOR);
        }

        return null;
    }

    private Date convertDateFromCurrentUserTimezone(Date date) {
        if (date == null)
            return null;

        Preferences prefs = preferencesService.getPreferences(userDirectoryService.getCurrentUser().getId());
        TimeZone timeZone = null;
        if (prefs != null) {
            ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
            String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
            timeZone = timeZoneStr != null ? TimeZone.getTimeZone(timeZoneStr): TimeZone.getDefault();
        } else {
            timeZone = TimeZone.getDefault();
        }
        long timeMs = date.getTime();
        Date tzDate = new Date(timeMs - timeZone.getOffset(timeMs));

        return tzDate;
    }

    private Date convertDateFromServerTimezone(Date date) {
        if (date == null)
            return null;

        TimeZone timeZone = TimeZone.getDefault();

        long timeMs = date.getTime();
        Date tzDate = new Date(timeMs - timeZone.getOffset(timeMs));

        return tzDate;
    }

    private Date convertDateToUserTimezone(Date date, String userid) {
        if (date == null)
            return null;

        Preferences prefs = preferencesService.getPreferences(userid);
        TimeZone timeZone = null;
        if (prefs != null) {
            ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
            String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
            timeZone = timeZoneStr != null ? TimeZone.getTimeZone(timeZoneStr): TimeZone.getDefault();
        } else {
            timeZone = TimeZone.getDefault();
        }
        long timeMs = date.getTime();
        Date tzDate = new Date(timeMs + timeZone.getOffset(timeMs));

        return tzDate;
    }

    private Date convertDateToCurrentUserTimezone(Date date) {
        if (date == null)
            return null;

        Preferences prefs = preferencesService.getPreferences(userDirectoryService.getCurrentUser().getId());
        TimeZone timeZone = null;
        if (prefs != null) {
            ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
            String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
            timeZone = timeZoneStr != null ? TimeZone.getTimeZone(timeZoneStr): TimeZone.getDefault();
        } else {
            timeZone = TimeZone.getDefault();
        }
        long timeMs = date.getTime();
        Date tzDate = new Date(timeMs + timeZone.getOffset(timeMs));

        return tzDate;
    }

    private Date convertDateToServerTimezone(Date date) {
        if (date == null)
            return null;

        TimeZone timeZone = TimeZone.getDefault();

        long timeMs = date.getTime();
        Date tzDate = new Date(timeMs + timeZone.getOffset(timeMs));

        return tzDate;
    }

    private Date convertDateToTimezone(Date date, TimeZone timeZone) {
        if (date == null) return null;
        if (timeZone == null)
            timeZone = TimeZone.getDefault();
        long timeMs = date.getTime();
        Date tzDate = new Date(timeMs + timeZone.getOffset(timeMs));

        return tzDate;
    }

    public boolean isUserAllowedInLocation(String userId, String permission, String locationId) {
        if (securityService.isSuperUser()) {
            return true;
        }
        if (locationId != null && !locationId.startsWith(SiteService.REFERENCE_ROOT)) {
            locationId = SiteService.REFERENCE_ROOT + Entity.SEPARATOR + locationId;
        }
        if (securityService.unlock(userId, permission, locationId)) {
            return true;
        }
        return false;
    }

    public String getUserRoleInSite(String userId, String siteId) {
        String userRoleInSite = null;
        if (siteId != null) {
            try {
                Site site = siteService.getSite(siteId);
                if (!securityService.isSuperUser()) {
                    userRoleInSite = site.getUserRole(userId).getId();
                } else {
                    userRoleInSite = site.getMaintainRole();
                }
            } catch (IdUnusedException e) {
                log.error("No such site while resolving user role in site: {}", siteId);
            } catch (Exception e) {
                log.error("Unknown error while resolving user role in site: {}", siteId);
            }
        }
        return userRoleInSite;
    }

    public List<String> getUserGroupIdsInSite(String userId, String siteId) {
        List<String> groupIds = new ArrayList<String>();
        if (siteId != null) {
            try {
                Site site = siteService.getSite(siteId);
                Collection<Group> userGroups = site.getGroupsWithMember(userId);
                for (Group g : userGroups)
                    groupIds.add(g.getId());
            } catch (IdUnusedException e) {
                log.error("No such site while resolving user role in site: {}", siteId);
            } catch (Exception e) {
                log.error("Unknown error while resolving user role in site: {}", siteId);
            }
        }
        return groupIds;
    }

    public boolean isRecordingFormatFilterEnabled() {
        return bbbAPI.isRecordingFormatFilterEnabled();
    }

    public String getRecordingFormatFilterWhitelist() {
        return "" + bbbAPI.getRecordingFormatFilterWhitelist();
    }

    /**
     * Generate an iCal file in tmp dir, an return the file path on the
     * filesystem
     */
    private String generateIcalFromMeeting(BBBMeeting meeting) {
        TimeZone defaultTimezone = TimeZone.getDefault();
        Long iCalAlarmMinutesuserId = 30L;
        return generateIcalFromMeetingInTimeZone(meeting, iCalAlarmMinutesuserId, defaultTimezone);
    }

    private String generateIcalFromMeetingInUserTimezone(BBBMeeting meeting, Long iCalAlarmMinutesuserId, String userId) {

        Preferences prefs = preferencesService.getPreferences(userId);
        TimeZone timeZone = TimeZone.getDefault();
        if (prefs != null) {
            ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
            String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
            if( timeZoneStr != null )
                timeZone = TimeZone.getTimeZone(timeZoneStr);
        }

        return generateIcalFromMeetingInTimeZone(meeting, iCalAlarmMinutesuserId, timeZone);
    }

    private String generateIcalFromMeetingInTimeZone(BBBMeeting meeting, Long iCalAlarmMinutesuserId, TimeZone timeZone) {
        Date startDate = meeting.getStartDate();
        if (startDate == null)
            return null;
        Date endDate = meeting.getEndDate();

        String eventName = meeting.getName();

        // Create a TimeZone
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        net.fortuna.ical4j.model.TimeZone timezone = registry.getTimeZone(timeZone.getID());
        VTimeZone tz = timezone.getVTimeZone();

        // Create a reminder
        int minutes = (iCalAlarmMinutesuserId.intValue() % 60);
        int hours = (iCalAlarmMinutesuserId.intValue() / 60 % 24);
        int days = (iCalAlarmMinutesuserId.intValue() / 60 / 24);
        VAlarm vAlarm = new VAlarm(Duration.ofDays(days > 0 ? days * -1 : 0)
                                        .ofHours(hours > 0 ? hours * -1 : 0)
                                        .ofMinutes(minutes > 0 ? minutes * -1 : 0));

        // display a message..
        vAlarm.getProperties().add(Action.DISPLAY);
        vAlarm.getProperties().add(new Description(eventName));

        // Create the event
        VEvent vEvent = null;
        if (endDate != null) {
            DateTime start = new DateTime(startDate.getTime());
            DateTime end = new DateTime(endDate.getTime());
            vEvent = new VEvent(start, end, eventName);
        } else {
            DateTime start = new DateTime(startDate.getTime());
            vEvent = new VEvent(start, eventName);
        }

        // add description & url
        String meetingDescription = getMeetingDescription(meeting);
        String meetingUrl = getDirectToolJoinUrl(meeting);
        try {
            vEvent.getProperties().add(new Description(meetingDescription));
        } catch (Exception e1) {
            // ignore - no harm
        }
        try {
            vEvent.getProperties().add(new Url(new URI(meetingUrl)));
        } catch (Exception e1) {
            // ignore - no harm
        }

        // add timezone info..
        vEvent.getProperties().add(tz.getTimeZoneId());

        // generate unique identifier..
        UidGenerator ug = new RandomUidGenerator();
        Uid uid = ug.generateUid();
        vEvent.getProperties().add(uid);

        // add the reminder
        vEvent.getAlarms().add(vAlarm);

        // create a calendar
        net.fortuna.ical4j.model.Calendar icsCalendar = new net.fortuna.ical4j.model.Calendar();
        icsCalendar.getProperties().add( new ProdId("-//Sakai Calendar//iCal4j 1.0//EN"));
        icsCalendar.getProperties().add(Version.VERSION_2_0);
        icsCalendar.getProperties().add(CalScale.GREGORIAN);

        // add the event
        icsCalendar.getComponents().add(vEvent);

        // log ical, if debug
        log.debug(icsCalendar.toString());

        // output to temp file
        String filename = System.getProperty("java.io.tmpdir")
                + File.separatorChar + meeting.getId() + ".ics";
        try {
            FileOutputStream fout = new FileOutputStream(filename);
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(icsCalendar, fout);
        } catch (Exception e) {
            log.warn("Unable to write iCal to file: {}", filename, e);
        }

        return filename;
    }

    //Get participant permissions converted to a User Map
    private Map<String, User> getUsersParticipating(String selectionType, String selectionId, Site site) {
        Map<String, User> response = new HashMap<String, User>();

        if( selectionType.equals(Participant.SELECTION_USER)){
            try{
                User user = userDirectoryService.getUser(selectionId);

                response.put(selectionId, user);

            } catch (Exception e) {
                log.error("Failed on getUser() for {}", selectionId, e);
            }

        } else if( selectionType.equals(Participant.SELECTION_ROLE)){

            Set users = getSiteUsersInRole(site, selectionId);
            Iterator<String> usersIter = users.iterator();

            while ( usersIter.hasNext() ){
                String userId = usersIter.next();
                try{
                    User user = userDirectoryService.getUser(userId);
                    response.put(userId, user);

                } catch (Exception e) {
                    log.error("Failed on getUser() for {}", selectionId, e);
                }
            }

        }

        return response;

    }

    //Get users in a role for an specific site
    private Set getSiteUsersInRole(Site site, String role) {
        Set users = null;

        try{
            String siteReference = site.getReference();
            AuthzGroup authzGroup = authzGroupService.getAuthzGroup(siteReference);
            users = site.getUsersHasRole(role);

        } catch (Exception e) {
            log.error("Failed on getAuthzGroup() for {}", role, e);

        }

        return users;
    }


    /** Inner class to execute code as Sakai Administrator */
    abstract class AdminExecution {
        public AdminExecution() {};

        public abstract Object execution() throws Exception;

        public Object execute() throws Exception {
            Object returnObject = null;
            Session sakaiSession = sessionManager.getCurrentSession();
            String currentUserId = sakaiSession.getUserId();
            String currentUserEid = sakaiSession.getUserEid();
            if (!"admin".equals(currentUserId)) {
                // current user not admin
                try {
                    sakaiSession.setUserId("admin");
                    sakaiSession.setUserEid("admin");
                    authzGroupService.refreshUser("admin");

                    returnObject = execution();
                } catch (Exception e) {
                    throw e;
                } finally {
                    sakaiSession.setUserId(currentUserId);
                    sakaiSession.setUserEid(currentUserEid);
                    authzGroupService.refreshUser(currentUserId);
                }

            } else {
                // current user is admin
                try {
                    returnObject = execution();
                } catch (Exception e) {
                    throw e;
                }
            }
            return returnObject;
        }
    }

}
