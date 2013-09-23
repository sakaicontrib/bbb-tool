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

    public void setUserDirectoryService(
            UserDirectoryService userDirectoryService) {
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

    public void setServerConfigurationService(
            ServerConfigurationService serverConfigurationService) {
        this.serverConfigurationService = serverConfigurationService;
    }

    // --- Outputable, Inputable
    // -----------------------------------------------------
    public String[] getHandledOutputFormats() {
        return new String[] { Formats.JSON };
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
                throw new EntityNotFoundException("Meeting not found",
                        ref.getReference());
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

    public String createEntity(EntityReference ref, Object entity,
            Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("createMeeting");
        logger.debug("EntityReference:" + ref.toString() + ", Entity:"
                + entity.toString() + ", params:" + params.toString());

        BBBMeeting meeting = (BBBMeeting) entity;

        // generate uuid
        meeting.setId(idManager.createUuid());

        // owner
        meeting.setOwnerId(userDirectoryService.getCurrentUser().getId());
        meeting.setOwnerDisplayName(userDirectoryService.getCurrentUser()
                .getDisplayName());

        // recording flag
        String recordingStr = (String) params.get("recording");
        boolean recording = (recordingStr != null && (recordingStr
                .toLowerCase().equals("on") || recordingStr.toLowerCase()
                .equals("true")));
        meeting.setRecording(recording ? Boolean.TRUE : Boolean.FALSE);

        // participants
        String meetingOwnerId = meeting.getOwnerId();
        List<Participant> participants = extractParticipants(params,
                meetingOwnerId);
        meeting.setParticipants(participants);

        // store meeting
        String addToCalendarStr = (String) params.get("addToCalendar");
        String notifyParticipantsStr = (String) params
                .get("notifyParticipants");
        boolean addToCalendar = addToCalendarStr != null
                && (addToCalendarStr.toLowerCase().equals("on") || addToCalendarStr
                        .toLowerCase().equals("true"));
        boolean notifyParticipants = notifyParticipantsStr != null
                && (notifyParticipantsStr.toLowerCase().equals("on") || notifyParticipantsStr
                        .toLowerCase().equals("true"));

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
            if (!meetingManager.createMeeting(meeting, notifyParticipants,
                    addToCalendar))
                throw new EntityException("Unable to store meeting in DB",
                        meeting.getReference(), 400);
        } catch (BBBException e) {
            throw new EntityException(e.getPrettyMessage(),
                    meeting.getReference(), 400);
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
                throw new IllegalArgumentException(
                        "Could not locate meeting to update");
            }
            // update name
            String nameStr = (String) params.get("name");
            if (nameStr != null)
                meeting.setName(nameStr);

            // update description
            String welcomeMessageStr = (String) params
                    .get("props.welcomeMessage");
            if (welcomeMessageStr != null)
                meeting.setWelcomeMessage(welcomeMessageStr);

            // update recording
            String recordingStr = (String) params.get("recording");
            boolean recording = (recordingStr != null && (recordingStr
                    .toLowerCase().equals("on") || recordingStr.toLowerCase()
                    .equals("true")));
            meeting.setRecording(Boolean.valueOf(recording));

            // update recordingDuration
            String recordingDurationStr = (String) params
                    .get("recordingDuration");
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
            List<Participant> participants = extractParticipants(params,
                    meetingOwnerId);
            meeting.setParticipants(participants);

            // store meeting
            String addToCalendarStr = (String) params.get("addToCalendar");
            String notifyParticipantsStr = (String) params
                    .get("notifyParticipants");
            boolean addToCalendar = addToCalendarStr != null
                    && (addToCalendarStr.toLowerCase().equals("on") || addToCalendarStr
                            .toLowerCase().equals("true"));
            boolean notifyParticipants = notifyParticipantsStr != null
                    && (notifyParticipantsStr.toLowerCase().equals("on") || notifyParticipantsStr
                            .toLowerCase().equals("true"));

            try {
                if (!meetingManager.updateMeeting(meeting, notifyParticipants,
                        addToCalendar))
                    throw new EntityException("Unable to update meeting in DB",
                            meeting.getReference(), 400);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(),
                        meeting.getReference(), 400);
            }
        } catch (SecurityException se) {
            throw new EntityException(se.getMessage(), ref.getReference(), 400);
        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
    }

    public List<BBBMeeting> getEntities(EntityReference ref, Search search) {
        List<BBBMeeting> meetings = null;

        Restriction locRes = search
                .getRestrictionByProperty(CollectionResolvable.SEARCH_LOCATION_REFERENCE);

        if (locRes != null) {
            String location = locRes.getStringValue();
            String context = null;

            if (location != null
                    && location.startsWith(SiteService.REFERENCE_ROOT)) {
                context = location.substring(SiteService.REFERENCE_ROOT
                        .length() + 1);
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
                throw new EntityException(e.getMessage(), ref.getReference(),
                        400);
            }

        } else {
            throw new IllegalArgumentException("Missing required parameter "
                    + CollectionResolvable.SEARCH_LOCATION_REFERENCE);
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
            throw new EntityException(e.getPrettyMessage(), ref.getReference(),
                    400);
        }
    }

    // --- ActionsExecutable (Custom actions)
    // ----------------------------------------
    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String testMeeting(Map<String, Object> params) {

        if (logger.isDebugEnabled())
            logger.debug("testMeeting");
        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException(
                    "Missing required parameters meetingId");
        }

        try {
            BBBMeeting meeting = meetingManager.getMeeting(meetingID);

            return "startDate="
                    + (new java.sql.Date(meeting.getStartDate().getTime()))
                            .toString()
                    + " "
                    + (new java.sql.Time(meeting.getStartDate().getTime()))
                            .toString()
                    + " timeStamp="
                    + (new java.sql.Timestamp(meeting.getStartDate().getTime())
                            + " startDateUTC=" + meeting.getStartDate()
                            .getTime());

            // return meeting.toString();

        } catch (Exception e) {
            throw new EntityException(e.getMessage(), e.getMessage());
        }

    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String isMeetingRunning(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("isMeetingRunning");
        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException(
                    "Missing required parameters meetingId");
        }

        try {
            return Boolean.toString(meetingManager.isMeetingRunning(meetingID));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX
                    + Entity.SEPARATOR + meetingID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String endMeeting(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("endMeeting");
        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter [meetingID]");
        }

        try {
            return Boolean.toString(meetingManager.endMeeting(meetingID));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX
                    + Entity.SEPARATOR + meetingID;
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
            throw new IllegalArgumentException(
                    "Missing required parameter [meetingID]");
        }
        if (recordID == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter [recordID]");
        }
        if (publish == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter [publish]");
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
            throw new IllegalArgumentException(
                    "Missing required parameter [meetingID]");
        }
        if (recordID == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter [recordID]");
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
    public String joinMeeting(OutputStream out, EntityView view,
            EntityReference ref) {
        if (logger.isDebugEnabled())
            logger.debug("joinMeeting");
        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        // get join url
        try {
            BBBMeeting meeting = meetingManager.getMeeting(ref.getId());

            if (meeting == null) {
                throw new EntityException(
                        "This meeting is no longer available.", null, 404);
            }
            String joinUrl = meeting.getJoinUrl();

            if (joinUrl == null) {
                throw new EntityException(
                        "You are not allowed to join this meeting.",
                        meeting.getReference(), 403);
            }

            try {
                meetingManager.checkJoinMeetingPreConditions(meeting);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(),
                        meeting.getReference(), 400);
            }

            // log meeting join event
            meetingManager.logMeetingJoin(ref.getId());
            return joinUrl;

        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
        // pre-join meeting
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getUserSelectionOptions(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getUserSelectionOptions");
        String siteId = (String) params.get("siteId");
        if (siteId == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter siteId");
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
                    logger.warn("Could not retrieve displayName for userId: "
                            + u.getUserId());
                }

                if (displayName != null) {
                    Map<String, String> m = new HashMap<String, String>();
                    m.put("id", u.getUserId());
                    m.put("title", displayName + " (" + u.getUserDisplayId()
                            + ")");
                    users.add(m);
                }
            }
            map.put("users", users);

            // defaults
            Map<String, String> dlfts = new HashMap<String, String>();
            dlfts.put(
                    BBBMeetingManager.CFG_DEFAULT_ALLUSERS,
                    serverConfigurationService.getString(
                            BBBMeetingManager.CFG_DEFAULT_ALLUSERS, "true")
                            .toLowerCase());
            dlfts.put(
                    BBBMeetingManager.CFG_DEFAULT_OWNER,
                    serverConfigurationService.getString(
                            BBBMeetingManager.CFG_DEFAULT_OWNER, "moderator")
                            .toLowerCase());
            map.put("defaults", dlfts);

            return new ActionReturn(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getServerTimeInUserTimezone(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getServerTimeInUserTimezone");

        Map<String, Object> map = new HashMap<String, Object>();
        map = meetingManager.getServerTimeInUserTimezone();

        return new ActionReturn(map);

    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getServerTimeInDefaultTimezone(
            Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getServerTimeInDefaultTimezone");

        Map<String, Object> map = new HashMap<String, Object>();
        map = meetingManager.getServerTimeInDefaultTimezone();

        return new ActionReturn(map);

    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getToolVersion(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getToolVersion");

        Map<String, Object> map = new HashMap<String, Object>();
        map = meetingManager.getToolVersion();

        return new ActionReturn(map);

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

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getAutorefreshInterval(Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getAutorefreshInterval");
        Map<String, String> map = new HashMap<String, String>();
        String autorefreshMeetings = meetingManager.getAutorefreshForMeetings();
        if (autorefreshMeetings != null) {
            map.put("meetings", autorefreshMeetings);
        }
        String autorefreshRecordings = meetingManager
                .getAutorefreshForRecordings();
        if (autorefreshRecordings != null) {
            map.put("recordings", autorefreshRecordings);
        }
        return new ActionReturn(map);
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getAddUpdateFormConfigParameters(
            Map<String, Object> params) {
        if (logger.isDebugEnabled())
            logger.debug("getAddUpdateFormConfiguration");
        Map<String, String> map = new HashMap<String, String>();
        String recordingEnabled = meetingManager.isRecordingEnabled();
        if (recordingEnabled != null) {
            map.put("recording", recordingEnabled);
        }
        String descriptionMaxLength = meetingManager
                .getMaxLengthForDescription();
        if (descriptionMaxLength != null) {
            map.put("descriptionMaxLength", descriptionMaxLength);
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
            localeEventNames.put(BBBMeetingManager.EVENT_KEYS[i],
                    msgs.getString(BBBMeetingManager.EVENT_KEYS[i]));
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
