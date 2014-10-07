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

package org.sakaiproject.bbb.tool.entity;

import java.io.OutputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.api.Participant;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.EntityTransferrerRefMigrator;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.CoreEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.CollectionResolvable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Createable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Deleteable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Describeable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Inputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Outputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Resolvable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Statisticable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Updateable;
import org.sakaiproject.entitybroker.entityprovider.extension.ActionReturn;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.entityprovider.search.Restriction;
import org.sakaiproject.entitybroker.entityprovider.search.Search;
import org.sakaiproject.entitybroker.exception.EntityException;
import org.sakaiproject.entitybroker.exception.EntityNotFoundException;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.id.api.IdManager;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ResourceLoader;

/**
 * BBBMeetingEntityProvider is the EntityProvider class that implements several
 * EntityBroker capabilities.
 * 
 * @author Adrian Fish, Nuno Fernandes
 */
public class BBBMeetingEntityProvider extends AbstractEntityProvider implements
        CoreEntityProvider, AutoRegisterEntityProvider, Inputable, Outputable,
        Createable, Updateable, Resolvable, Describeable, Deleteable,
        CollectionResolvable, ActionsExecutable, Statisticable {
    protected final Logger logger = Logger.getLogger(getClass());

    // --- Spring ------------------------------------------------------------
    private BBBMeetingManager meetingManager;

    public void setMeetingManager(BBBMeetingManager meetingManager) {
        this.meetingManager = meetingManager;
    }

    private UserDirectoryService userDirectoryService = null;

    public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    private SiteService siteService = null;

    public void setSiteService(SiteService siteService) {
        this.siteService = siteService;
    }

    private IdManager idManager;

    public void setIdManager(IdManager idManager) {
        this.idManager = idManager;
    }

    private ServerConfigurationService serverConfigurationService;

    public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        this.serverConfigurationService = serverConfigurationService;
    }

    // --- Outputable, Inputable
    // -----------------------------------------------------
    public String[] getHandledOutputFormats() {
        return new String[] { Formats.HTML, Formats.JSON, Formats.TXT };
    }

    public String[] getHandledInputFormats() {
        return new String[] { Formats.HTML, Formats.JSON, Formats.FORM };
    }

    // --- EntityProvider, CoreEntityProvider, Resolvable
    // -----------------------------
    public String getEntityPrefix() {
        return BBBMeetingManager.ENTITY_PREFIX;
    }

    public Object getEntity(EntityReference ref) {
        if (logger.isDebugEnabled())
            logger.debug("getEntity(" + ref.getId() + ")");

        String id = ref.getId();

        if (id == null || "".equals(id)) {
            return new BBBMeeting();
        }

        try {
            BBBMeeting meeting = meetingManager.getMeeting(id);

            if (meeting == null) {
                throw new EntityNotFoundException("Meeting not found", ref.getReference());
            }

            // for security reasons, clear passwords and meeting token
            meeting.setAttendeePassword(null);
            meeting.setModeratorPassword(null);

            return meeting;

        } catch (SecurityException se) {
            throw new EntityException(se.getMessage(), ref.getReference(), 400);
        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }

    }

    public boolean entityExists(String id) {
        if (logger.isDebugEnabled())
            logger.debug("entityExists(" + id + ")");

        if (id == null || "".equals(id))
            return false;

        try {
            return (meetingManager.getMeeting(id) != null);
        } catch (SecurityException se) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // --- CRUDable
    // ------------------------------------------------------------------
    public Object getSampleEntity() {
        return new BBBMeeting();
    }

    public String createEntity(EntityReference ref, Object entity, Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("createMeeting");
        logger.debug("EntityReference:" + ref.toString() + ", Entity:" + entity.toString() + ", params:" + params.toString());

        BBBMeeting meeting = (BBBMeeting) entity;

        // generate uuid
        meeting.setId(idManager.createUuid());

        // owner
        meeting.setOwnerId(userDirectoryService.getCurrentUser().getId());
        meeting.setOwnerDisplayName(userDirectoryService.getCurrentUser().getDisplayName());

        // recording flag
        String recordingStr = (String) params.get("recording");
        boolean recording = (recordingStr != null && 
                (recordingStr.toLowerCase().equals("on") || recordingStr.toLowerCase().equals("true")));
        meeting.setRecording(recording ? Boolean.TRUE : Boolean.FALSE);

        // waitForModerator flag
        String waitForModeratorStr = (String) params.get("waitForModerator");
        boolean waitForModerator = (waitForModeratorStr != null && 
                (waitForModeratorStr.toLowerCase().equals("on") || waitForModeratorStr.toLowerCase().equals("true")));
        meeting.setWaitForModerator(Boolean.valueOf(waitForModerator));

        // multipleSessionsAllowed flag
        String multipleSessionsAllowedStr = (String) params.get("multipleSessionsAllowed");
        boolean multipleSessionsAllowed = (multipleSessionsAllowedStr != null && 
                (multipleSessionsAllowedStr.toLowerCase().equals("on") || multipleSessionsAllowedStr.toLowerCase().equals("true")));
        meeting.setMultipleSessionsAllowed(Boolean.valueOf(multipleSessionsAllowed));

        // participants
        String meetingOwnerId = meeting.getOwnerId();
        List<Participant> participants = extractParticipants(params, meetingOwnerId);
        meeting.setParticipants(participants);

        // store meeting
        String addToCalendarStr = (String) params.get("addToCalendar");
        String notifyParticipantsStr = (String) params.get("notifyParticipants");
        String iCalAttachedStr = (String) params.get("iCalAttached");
        String iCalAlarmMinutesStr = (String) params.get("iCalAlarmMinutes");
        boolean addToCalendar = addToCalendarStr != null
                && (addToCalendarStr.toLowerCase().equals("on") || addToCalendarStr.toLowerCase().equals("true"));
        boolean notifyParticipants = notifyParticipantsStr != null
                && (notifyParticipantsStr.toLowerCase().equals("on") || notifyParticipantsStr.toLowerCase().equals("true"));
        boolean iCalAttached = iCalAttachedStr != null
                && (iCalAttachedStr.toLowerCase().equals("on") || iCalAttachedStr.toLowerCase().equals("true"));
        Long iCalAlarmMinutes = iCalAlarmMinutesStr != null? Long.valueOf(iCalAlarmMinutesStr): 0L;

        // generate differentiated passwords
        meeting.setAttendeePassword(generatePassword());
        do {
            meeting.setModeratorPassword(generatePassword());
        } while (meeting.getAttendeePassword().equals(
                meeting.getModeratorPassword()));

        // generate voiceBidge
        String voiceBridgeStr = (String) params.get("voiceBridge");
        logger.debug("voiceBridgeStr:" + voiceBridgeStr);
        if (voiceBridgeStr == null || voiceBridgeStr.equals("")
                || Integer.parseInt(voiceBridgeStr) == 0) {
            Integer voiceBridge = 70000 + new Random().nextInt(10000);
            meeting.setVoiceBridge(voiceBridge);
        } else {
            meeting.setVoiceBridge(Integer.valueOf(voiceBridgeStr));
        }

        try {
            if (!meetingManager.createMeeting(meeting, notifyParticipants, addToCalendar, iCalAttached, iCalAlarmMinutes))
                throw new EntityException("Unable to store meeting in DB", meeting.getReference(), 400);
        } catch (BBBException e) {
            throw new EntityException(e.getPrettyMessage(), meeting.getReference(), 400);
        }

        return meeting.getId();
    }

    public void updateEntity(EntityReference ref, Object entity,
            Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("updateMeeting");

        BBBMeeting newMeeting = (BBBMeeting) entity;

        try {
            BBBMeeting meeting = meetingManager.getMeeting(ref.getId());
            if (meeting == null) {
                throw new IllegalArgumentException("Could not locate meeting to update");
            }
            // update name
            String nameStr = (String) params.get("name");
            if (nameStr != null)
                meeting.setName(nameStr);

            // update description
            String welcomeMessageStr = (String) params.get("props.welcomeMessage");
            if (welcomeMessageStr != null)
                meeting.setWelcomeMessage(welcomeMessageStr);

            // update recording flag
            String recordingStr = (String) params.get("recording");
            boolean recording = (recordingStr != null && 
                    (recordingStr.toLowerCase().equals("on") || recordingStr.toLowerCase().equals("true")));
            meeting.setRecording(Boolean.valueOf(recording));

            // update recordingDuration
            String recordingDurationStr = (String) params.get("recordingDuration");
            if (recordingDurationStr != null)
                meeting.setRecordingDuration(Long.valueOf(recordingDurationStr));
            else
                meeting.setRecordingDuration(0L);

            // update voiceBridge only if the voiceBridge parameter is sent from
            // the view to the controller
            String voiceBridgeStr = (String) params.get("voiceBridge");
            if (voiceBridgeStr != null) {
                if (voiceBridgeStr.equals("")
                        || Integer.parseInt(voiceBridgeStr) == 0) {
                    Integer voiceBridge = 70000 + new Random().nextInt(10000);
                    meeting.setVoiceBridge(voiceBridge);
                } else {
                    meeting.setVoiceBridge(Integer.valueOf(voiceBridgeStr));
                }
            }

            // update waitForModerator flag
            String waitForModeratorStr = (String) params.get("waitForModerator");
            boolean waitForModerator = (waitForModeratorStr != null && 
                    (waitForModeratorStr.toLowerCase().equals("on") || waitForModeratorStr.toLowerCase().equals("true")));
            meeting.setWaitForModerator(Boolean.valueOf(waitForModerator));

            // update multipleSessionsAllowed flag
            String multipleSessionsAllowedStr = (String) params.get("multipleSessionsAllowed");
            boolean multipleSessionsAllowed = (multipleSessionsAllowedStr != null && 
                    (multipleSessionsAllowedStr.toLowerCase().equals("on") || multipleSessionsAllowedStr.toLowerCase().equals("true")));
            meeting.setMultipleSessionsAllowed(Boolean.valueOf(multipleSessionsAllowed));

            // update dates
            if (params.get("startDate") != null)
                meeting.setStartDate(newMeeting.getStartDate());
            else
                meeting.setStartDate(null);
            if (params.get("endDate") != null)
                meeting.setEndDate(newMeeting.getEndDate());
            else
                meeting.setEndDate(null);

            // update participants
            String meetingOwnerId = meeting.getOwnerId();
            List<Participant> participants = extractParticipants(params, meetingOwnerId);
            meeting.setParticipants(participants);

            // store meeting
            String addToCalendarStr = (String) params.get("addToCalendar");
            String notifyParticipantsStr = (String) params.get("notifyParticipants");
            String iCalAttachedStr = (String) params.get("iCalAttached");
            String iCalAlarmMinutesStr = (String) params.get("iCalAlarmMinutes");
            boolean addToCalendar = addToCalendarStr != null
                    && (addToCalendarStr.toLowerCase().equals("on") || addToCalendarStr.toLowerCase().equals("true"));
            boolean notifyParticipants = notifyParticipantsStr != null
                    && (notifyParticipantsStr.toLowerCase().equals("on") || notifyParticipantsStr.toLowerCase().equals("true"));
            boolean iCalAttached = iCalAttachedStr != null
                    && (iCalAttachedStr.toLowerCase().equals("on") || iCalAttachedStr.toLowerCase().equals("true"));
            Long iCalAlarmMinutes = iCalAlarmMinutesStr != null? Long.valueOf(iCalAlarmMinutesStr): 0L;

            try {
                if (!meetingManager.updateMeeting(meeting, notifyParticipants, addToCalendar, iCalAttached, iCalAlarmMinutes))
                    throw new EntityException("Unable to update meeting in DB", meeting.getReference(), 400);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(), meeting.getReference(), 400);
            }
        } catch (SecurityException se) {
            throw new EntityException(se.getMessage(), ref.getReference(), 400);
        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
    }

    public List<BBBMeeting> getEntities(EntityReference ref, Search search) {
        if (logger.isDebugEnabled())
            logger.debug("getEntities");
        List<BBBMeeting> meetings = null;

        Restriction locRes = search.getRestrictionByProperty(CollectionResolvable.SEARCH_LOCATION_REFERENCE);

        if (locRes != null) {
            String location = locRes.getStringValue();
            String context = null;

            if (location != null
                    && location.startsWith(SiteService.REFERENCE_ROOT)) {
                context = location.substring(SiteService.REFERENCE_ROOT.length() + 1);
            } else {
                context = location;
            }

            try {
                meetings = meetingManager.getSiteMeetings(context);

                // for security reasons, clear passwords and meeting token
                for (BBBMeeting meeting : meetings) {
                    meeting.setAttendeePassword(null);
                    meeting.setModeratorPassword(null);
                }

            } catch (Exception e) {
                throw new EntityException(e.getMessage(), ref.getReference(), 400);
            }

        } else {
            throw new IllegalArgumentException("Missing required parameter " + CollectionResolvable.SEARCH_LOCATION_REFERENCE);
        }

        return meetings;
    }

    public void deleteEntity(EntityReference ref, Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("deleteEntity");
        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        try {
            meetingManager.deleteMeeting(ref.getId());
        } catch (BBBException e) {
            throw new EntityException(e.getPrettyMessage(), ref.getReference(), 400);
        }
    }

    // --- ActionsExecutable (Custom actions)
    // ----------------------------------------
    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getSettings(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getSettings");
        Map<String, Object> settings = new LinkedHashMap<String, Object>();

        String siteId = params.containsKey("siteId")? (String) params.get("siteId"): null;
        User user = userDirectoryService.getCurrentUser();
        Map<String, Object> currentUser = getCurrentUser(user);
        currentUser.put("role", meetingManager.getUserRoleInSite((String)currentUser.get("id"), siteId));
        currentUser.put("permissions", getUserPermissionsInSite((String)currentUser.get("id"), siteId));
        settings.put("currentUser", currentUser);

        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("autorefreshInterval", getAutorefreshInterval());
        config.put("addUpdateFormParameters", getAddUpdateFormConfigParameters());
        config.put("serverTimeInDefaultTimezone", getServerTimeInDefaultTimezone());
        config.put("serverTimeInUserTimezone", getServerTimeInUserTimezone());
        settings.put("config", config);
        settings.put("toolVersion", getToolVersion());
        return new ActionReturn(settings);
    }

    private Map<String, Object>getCurrentUser(User user) {
        Map<String, Object> currentUser = new LinkedHashMap<String, Object>();
        currentUser.put("id", user.getId());
        currentUser.put("displayId", user.getDisplayId());
        currentUser.put("displayName", user.getDisplayName());
        currentUser.put("eid", user.getEid());
        currentUser.put("email", user.getEmail());
        return currentUser;
    }

    private List<String> getUserPermissionsInSite(String userId, String siteId) {
        List<String> permissions = new ArrayList<String>();
        if( meetingManager.isUserAllowedInLocation(userId, "site.viewRoster", siteId) )
            permissions.add("site.viewRoster");
        if( meetingManager.isUserAllowedInLocation(userId, "site.upd", siteId) ) {
            permissions.add("site.upd");
            permissions.add(meetingManager.FN_ADMIN);
        }
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_CREATE, siteId) )
            permissions.add(meetingManager.FN_CREATE);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_EDIT_OWN, siteId) )
            permissions.add(meetingManager.FN_EDIT_OWN);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_EDIT_ANY, siteId) )
            permissions.add(meetingManager.FN_EDIT_ANY);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_DELETE_OWN, siteId) )
            permissions.add(meetingManager.FN_DELETE_OWN);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_DELETE_ANY, siteId) )
            permissions.add(meetingManager.FN_DELETE_ANY);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_PARTICIPATE, siteId) )
            permissions.add(meetingManager.FN_PARTICIPATE);
        if( meetingManager.isUserAllowedInLocation(userId, "calendar.new", siteId) )
            permissions.add("calendar.new");
        if( meetingManager.isUserAllowedInLocation(userId, "calendar.revise.own", siteId) )
            permissions.add("calendar.revise.own");
        if( meetingManager.isUserAllowedInLocation(userId, "calendar.revise.any", siteId) )
            permissions.add("calendar.revise.any");
        if( meetingManager.isUserAllowedInLocation(userId, "calendar.delete.own", siteId) )
            permissions.add("calendar.delete.own");
        if( meetingManager.isUserAllowedInLocation(userId, "calendar.delete.any", siteId) )
            permissions.add("calendar.delete.any");
        return permissions;
    }

    private Map<String, String> getAutorefreshInterval() {
        Map<String, String> interval = new LinkedHashMap<String, String>();
        String autorefreshMeetings = meetingManager.getAutorefreshForMeetings();
        if (autorefreshMeetings != null) {
            interval.put("meetings", autorefreshMeetings);
        }
        String autorefreshRecordings = meetingManager.getAutorefreshForRecordings();
        if (autorefreshRecordings != null) {
            interval.put("recordings", autorefreshRecordings);
        }
        return interval;
    }

    private Map<String, Object> getAddUpdateFormConfigParameters() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        //UX settings for 'recording' checkbox
        Boolean recordingEnabled = Boolean.parseBoolean(meetingManager.isRecordingEnabled());
        if (recordingEnabled != null) {
            map.put("recordingEnabled", recordingEnabled);
        }
        Boolean recordingDefault = Boolean.parseBoolean(meetingManager.getRecordingDefault());
        if (recordingDefault != null) {
            map.put("recordingDefault", recordingDefault);
        }
        //UX settings for 'duration' box
        Boolean durationEnabled = Boolean.parseBoolean(meetingManager.isDurationEnabled());
        if (durationEnabled != null) {
            map.put("durationEnabled", durationEnabled);
        }
        String durationDefault = meetingManager.getDurationDefault();
        if (durationDefault != null) {
            map.put("durationDefault", durationDefault);
        }
        //UX settings for 'wait moderator' box
        Boolean waitmoderatorEnabled = Boolean.parseBoolean(meetingManager.isWaitModeratorEnabled());
        if (waitmoderatorEnabled != null) {
            map.put("waitmoderatorEnabled", waitmoderatorEnabled);
        }
        Boolean waitmoderatorDefault = Boolean.parseBoolean(meetingManager.getWaitModeratorDefault());
        if (waitmoderatorDefault != null) {
            map.put("waitmoderatorDefault", waitmoderatorDefault);
        }
        //UX settings for 'multiple sessions allowed' box
        Boolean multiplesessionsallowedEnabled = Boolean.parseBoolean(meetingManager.isMultipleSessionsAllowedEnabled());
        if (multiplesessionsallowedEnabled != null) {
            map.put("multiplesessionsallowedEnabled", multiplesessionsallowedEnabled);
        }
        Boolean multiplesessionsallowedDefault = Boolean.parseBoolean(meetingManager.getMultipleSessionsAllowedDefault());
        if (multiplesessionsallowedDefault != null) {
            map.put("multiplesessionsallowedDefault", multiplesessionsallowedDefault);
        }
        //UX settings for 'description' box
        String descriptionMaxLength = meetingManager.getMaxLengthForDescription();
        if (descriptionMaxLength != null) {
            map.put("descriptionMaxLength", descriptionMaxLength);
        }
        return map;
    }

    private Map<String, Object> getServerTimeInDefaultTimezone() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map = meetingManager.getServerTimeInDefaultTimezone();
        return map;
    }

    private Map<String, Object> getServerTimeInUserTimezone() {
        Map<String, Object> map = new HashMap<String, Object>();
        map = meetingManager.getServerTimeInUserTimezone();
        return map;

    }

    private Map<String, Object> getToolVersion() {
        Map<String, Object> map = new HashMap<String, Object>();
        map = meetingManager.getToolVersion();
        return map;
    }


    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String isMeetingRunning(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("isMeetingRunning");
        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException("Missing required parameters meetingId");
        }

        try {
            return Boolean.toString(meetingManager.isMeetingRunning(meetingID));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX + Entity.SEPARATOR + meetingID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String endMeeting(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("endMeeting");
        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException("Missing required parameter [meetingID]");
        }

        try {
            return Boolean.toString(meetingManager.endMeeting(meetingID));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX + Entity.SEPARATOR + meetingID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public ActionReturn getMeetingInfo(OutputStream out, EntityView view,
            EntityReference ref) {
        if (logger.isDebugEnabled()) logger.debug("getMeetingInfo");
        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        try {
            return new ActionReturn(meetingManager.getMeetingInfo(ref.getId()));
        } catch (BBBException e) {
            return new ActionReturn(new HashMap<String, String>());
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public ActionReturn getRecordings(OutputStream out, EntityView view,
            EntityReference ref) {
        // if(logger.isDebugEnabled()) logger.debug("getRecordings");

        if (ref == null)
            throw new EntityNotFoundException("Meeting not found", null);

        try {
            Map<String, Object> recordingsResponse = meetingManager
                    .getRecordings(ref.getId());

            return new ActionReturn(recordingsResponse);

        } catch (BBBException e) {
            return new ActionReturn(new HashMap<String, String>());
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getSiteRecordings(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getSiteRecordings");

        String siteId = (String) params.get("siteId");

        try {
            Map<String, Object> recordingsResponse = meetingManager
                    .getSiteRecordings(siteId);

            return new ActionReturn(recordingsResponse);

        } catch (Exception e) {
            return new ActionReturn(new HashMap<String, String>());
        }

    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String publishRecordings(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("publishRecordings");
        String meetingID = (String) params.get("meetingID");
        String recordID = (String) params.get("recordID");
        String publish = (String) params.get("publish");
        if (meetingID == null) {
            throw new IllegalArgumentException("Missing required parameter [meetingID]");
        }
        if (recordID == null) {
            throw new IllegalArgumentException("Missing required parameter [recordID]");
        }
        if (publish == null) {
            throw new IllegalArgumentException("Missing required parameter [publish]");
        }

        try {
            return Boolean.toString(meetingManager.publishRecordings(meetingID,
                    recordID, publish));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX
                    + Entity.SEPARATOR + meetingID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }

    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String deleteRecordings(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("deleteRecordings");
        String meetingID = (String) params.get("meetingID");
        String recordID = (String) params.get("recordID");
        if (meetingID == null) {
            throw new IllegalArgumentException("Missing required parameter [meetingID]");
        }
        if (recordID == null) {
            throw new IllegalArgumentException("Missing required parameter [recordID]");
        }

        try {
            return Boolean.toString(meetingManager.deleteRecordings(meetingID,
                    recordID));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX
                    + Entity.SEPARATOR + meetingID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public String getJoinMeetingUrl(OutputStream out, EntityView view, EntityReference ref) {
        if (logger.isDebugEnabled())
            logger.debug("getJoinUrl");
        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        // get join url
        try {
            BBBMeeting meeting = meetingManager.getMeeting(ref.getId());

            if (meeting == null) {
                throw new EntityException("This meeting is no longer available.", null, 404);
            }

            String joinUrl = meetingManager.getJoinUrl(meeting);

            if (joinUrl == null) {
                throw new EntityException("You are not allowed to join this meeting.", meeting.getReference(), 403);
            }

            try {
                meetingManager.checkJoinMeetingPreConditions(meeting);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(), meeting.getReference(), 400);
            }

            // log meeting join event
            meetingManager.logMeetingJoin(ref.getId());
            return joinUrl;

        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
        // pre-join meeting
    }

    public final static boolean WAITFORMODERATOR = true;
    public final static boolean NOTWAITFORMODERATOR = false;

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public String joinMeeting(OutputStream out, EntityView view, EntityReference ref, Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("joinMeeting");
        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        // get join url
        try {
            String meetingId = ref.getId();
            BBBMeeting meeting = meetingManager.getMeeting(meetingId);
            if (meeting == null) {
                throw new EntityException("This meeting is no longer available.", null, 404);
            }

            String joinUrl = meetingManager.getJoinUrl(meeting);

            if (joinUrl == null) {
                throw new EntityException("You are not allowed to join this meeting.", meeting.getReference(), 403);
            }

            try {
                meetingManager.checkJoinMeetingPreConditions(meeting);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(), meeting.getReference(), 400);
            }

            // log meeting join event
            meetingManager.logMeetingJoin(meetingId);

            //Build the corresponding page for joining
            String html;
            if( meeting.getWaitForModerator() ){
                Participant p = meetingManager.getParticipantFromMeeting(meeting, userDirectoryService.getCurrentUser().getId());
                if( Participant.MODERATOR.equals(p.getRole())) {
                    html = getHtmlForJoining(joinUrl, meetingId, NOTWAITFORMODERATOR);
                } else {
                    Map<String, Object> meetingInfo = meetingManager.getMeetingInfo(meetingId);
                    if( meetingInfo != null && Integer.parseInt((String)meetingInfo.get("moderatorCount")) > 0 ) {
                        html = getHtmlForJoining(joinUrl, meetingId, NOTWAITFORMODERATOR);
                    } else {
                        html = getHtmlForJoining(joinUrl, meetingId, WAITFORMODERATOR);
                    }
                }
            } else {
                html = getHtmlForJoining(joinUrl, meetingId, NOTWAITFORMODERATOR);
            }
            return html;

        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
        // pre-join meeting
    }

    private String getHtmlForJoining(String joinUrl, String meetingId){
        return getHtmlForJoining(joinUrl, meetingId, NOTWAITFORMODERATOR);
    }

    private String getHtmlForJoining(String joinUrl, String meetingId, boolean waitformoderator){
        ResourceLoader toolMessages = new ResourceLoader("ToolMessages");
        Locale locale = (new ResourceLoader()).getLocale();
        toolMessages.setContextLocale(locale);
        String waiting_for_moderator_tooltip = toolMessages.getString("bbb_meetinginfo_waiting_for_moderator_tooltip");

        String commonHtmlHeader = "<html>\n" +
                                  "  <head>\n" +
                                  "    <meta http-equiv='Content-Type' content='text/html; charset=UTF-8'/>\n" +
                                  "    <meta http-equiv='cache-control' content='max-age=0' />\n" +
                                  "    <meta http-equiv='cache-control' content='no-cache' />\n" +
                                  "    <meta http-equiv='expires' content='-1' />\n" +
                                  "    <meta http-equiv='expires' content='Tue, 01 Jan 1980 1:00:00 GMT' />\n" +
                                  "    <meta http-equiv='pragma' content='no-cache' />\n" +
                                  "    <title>BigBlueButton</title>\n" +
                                  "    <link rel='stylesheet' type='text/css' href='/library/skin/tool_base.css' />\n" +
                                  "    <link rel='stylesheet' type='text/css' href='/bbb-tool/css/bbb.css' />\n" +
                                  "    <script type='text/javascript' language='JavaScript' src='/bbb-tool/lib/jquery-1.3.2.min.js'></script>\n";
        String commonHtmlFooter = "</html>\n";

        if( waitformoderator ){
            return commonHtmlHeader +
                   "    <script type='text/javascript' language='JavaScript'>\n" +
                   "        (function worker() {\n" +
                   "            var meetingInfo;\n" +
                   "            // Disable caching of AJAX responses\n" +
                   "            // Validates if a moderator has joined\n" +
                   "            jQuery.ajax( {\n" +
                   "                url: '/direct/bbb-tool/" + meetingId + "/getMeetingInfo.json',\n" +
                   "                dataType : 'json',\n" +
                   "                async : false,\n" +
                   "                cache: false,\n" +
                   "                success : function(data) {\n" +
                   "                    meetingInfo = data;\n" +
                   "                },\n" +
                   "                error : function(xmlHttpRequest, status, error) {\n" +
                   "                    return null;\n" +
                   "                },\n" +
                   "                complete : function() {\n" +
                   "                    if( parseInt(meetingInfo.moderatorCount) == 0 ){\n" +
                   "                        setTimeout(worker, 5000);\n" +
                   "                    } else {\n" +
                   "                        if (typeof window.opener != 'undefined') {\n" +
                   "                           window.opener.waitForModeratorRefresh('" + meetingId + "');\n" +
                   "                        }\n" +
                   "                        window.location.reload();\n" +
                   "                    }\n" +
                   "                }\n" +
                   "            });\n" +
                   "        })();\n" +
                   "    </script>\n" +
                   "  </head>\n" +
                   "  <body>\n" +
                   "    <div align='center'>\n" +
                   "      <h3>Waiting for moderator to join the meeting</h3><br/><br/>\n" +
                   "      <img id='joining' src='/bbb-tool/images/2.gif' title='" + waiting_for_moderator_tooltip + "' alt='" + waiting_for_moderator_tooltip + "' />\n" +
                   "    </div>\n" +
                   "  </body>\n" +
                   commonHtmlFooter;
        } else {
            return commonHtmlHeader +
                   "    <script type='text/javascript' language='JavaScript'>\n" +
                   "        window.opener.waitForModeratorRefresh('" + meetingId + "');\n" +
                   "    </script>\n" +
                   "    <meta http-equiv='refresh' content='0; url=" + joinUrl + "' />\n" +
                   "  </head>\n" +
                   "  <body>\n" +
                   "  </body>\n" +
                   commonHtmlFooter;
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getUserSelectionOptions(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getUserSelectionOptions");
        String siteId = (String) params.get("siteId");
        if (siteId == null) {
            throw new IllegalArgumentException("Missing required parameter siteId");
        }

        try {
            Map<String, Object> map = new HashMap<String, Object>();
            Site site = siteService.getSite(siteId);

            // groups
            List<Map<String, String>> groups = new ArrayList<Map<String, String>>();
            for (Group g : site.getGroups()) {
                Map<String, String> m = new HashMap<String, String>();
                m.put("id", g.getId());
                m.put("title", g.getTitle());
                groups.add(m);
            }
            map.put("groups", groups);

            // roles
            List<Map<String, String>> roles = new ArrayList<Map<String, String>>();
            for (Role r : site.getRoles()) {
                Map<String, String> m = new HashMap<String, String>();
                m.put("id", r.getId());
                m.put("title", r.getId());
                roles.add(m);
            }
            map.put("roles", roles);

            // users
            List<Map<String, String>> users = new ArrayList<Map<String, String>>();
            for (Member u : site.getMembers()) {
                String displayName = null;
                try {
                    displayName = userDirectoryService.getUser(u.getUserId())
                            .getDisplayName();
                } catch (UserNotDefinedException e1) {
                    logger.warn("Could not retrieve displayName for userId: " + u.getUserId());
                }

                if (displayName != null) {
                    Map<String, String> m = new HashMap<String, String>();
                    m.put("id", u.getUserId());
                    m.put("title", displayName + " (" + u.getUserDisplayId() + ")");
                    users.add(m);
                }
            }
            map.put("users", users);

            // defaults
            Map<String, String> dlfts = new HashMap<String, String>();
            dlfts.put(BBBMeetingManager.CFG_DEFAULT_ALLUSERS,
                    serverConfigurationService.getString(BBBMeetingManager.CFG_DEFAULT_ALLUSERS, "true").toLowerCase());
            dlfts.put(BBBMeetingManager.CFG_DEFAULT_OWNER,
                    serverConfigurationService.getString(BBBMeetingManager.CFG_DEFAULT_OWNER, "moderator").toLowerCase());
            map.put("defaults", dlfts);

            return new ActionReturn(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getNoticeText(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getNoticeText");
        Map<String, String> map = new HashMap<String, String>();
        String noticeText = meetingManager.getNoticeText();
        if (noticeText != null) {
            map.put("text", noticeText);
            map.put("level", meetingManager.getNoticeLevel());
        }
        return new ActionReturn(map);
    }

    // --- Statisticable
    // -------------------------------------------------------------
    public String getAssociatedToolId() {
        return BBBMeetingManager.TOOL_ID;
    }

    public String[] getEventKeys() {
        return BBBMeetingManager.EVENT_KEYS;
    }

    public Map<String, String> getEventNames(Locale locale) {
        Map<String, String> localeEventNames = new HashMap<String, String>();
        ResourceLoader msgs = new ResourceLoader("Events");
        msgs.setContextLocale(locale);
        for (int i = 0; i < BBBMeetingManager.EVENT_KEYS.length; i++) {
            localeEventNames.put(BBBMeetingManager.EVENT_KEYS[i], msgs.getString(BBBMeetingManager.EVENT_KEYS[i]));
        }
        return localeEventNames;
    }

    // --- UTILITY METHODS
    // -----------------------------------------------------------
    private List<Participant> extractParticipants(Map<String, Object> params,
            String meetingOwnerId) {
        List<Participant> participants = new ArrayList<Participant>();
        for (String key : params.keySet()) {
            String selectionType = null;
            String selectionId = null;
            String role = null;

            if (key.startsWith("all_")) {
                selectionType = Participant.SELECTION_ALL;
                selectionId = Participant.SELECTION_ALL;
                role = (String) params.get("all-role_" + params.get(key));

            } else if (key.startsWith("group_")) {
                selectionType = Participant.SELECTION_GROUP;
                selectionId = (String) params.get(key);
                role = (String) params.get("group-role_" + selectionId);

            } else if (key.startsWith("role_")) {
                selectionType = Participant.SELECTION_ROLE;
                selectionId = (String) params.get(key);
                role = (String) params.get("role-role_" + selectionId);

            } else if (key.startsWith("user_")) {
                selectionType = Participant.SELECTION_USER;
                selectionId = (String) params.get(key);
                role = (String) params.get("user-role_" + selectionId);
            }

            if (selectionType != null && selectionId != null && role != null) {
                participants.add(new Participant(selectionType, selectionId,
                        role));
            }
        }
        return participants;
    }

    /** Generate a random password */
    private String generatePassword() {
        Random randomGenerator = new Random(System.currentTimeMillis());
        return Long.toHexString(randomGenerator.nextLong());
    }

}
