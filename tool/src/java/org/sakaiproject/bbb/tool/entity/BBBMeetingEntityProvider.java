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

package org.sakaiproject.bbb.tool.entity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.codec.binary.Base64;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.api.Participant;
import org.sakaiproject.bbb.api.storage.BBBMeeting;
import org.sakaiproject.bbb.api.storage.BBBMeetingParticipant;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
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
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.InconsistentException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.id.api.IdManager;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.util.Validator;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * BBBMeetingEntityProvider is the EntityProvider class that implements several
 * EntityBroker capabilities.
 *
 * @author Adrian Fish, Nuno Fernandes
 */
@Slf4j
@Setter
public class BBBMeetingEntityProvider extends AbstractEntityProvider implements
        CoreEntityProvider, AutoRegisterEntityProvider, Inputable, Outputable,
        Createable, Updateable, Resolvable, Describeable, Deleteable,
        CollectionResolvable, ActionsExecutable, Statisticable {

    private BBBMeetingManager meetingManager;
    private UserDirectoryService userDirectoryService;
    private SiteService siteService;
    private IdManager idManager;
    private ServerConfigurationService serverConfigurationService;
    private ContentHostingService contentHostingService;
    private SecurityService securityService;

    public String[] getHandledOutputFormats() {
        return new String[] { Formats.HTML, Formats.JSON, Formats.TXT };
    }

    public String[] getHandledInputFormats() {
        return new String[] { Formats.HTML, Formats.JSON, Formats.FORM };
    }

    public String getEntityPrefix() {
        return BBBMeetingManager.ENTITY_PREFIX;
    }

    public Object getEntity(EntityReference ref) {

        log.debug("getEntity(" + ref.getId() + ")");

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

        log.debug("entityExists(" + id + ")");

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

        log.debug("createMeeting");

        log.debug("EntityReference:" + ref.toString() + ", Entity:" + entity.toString() + ", params:" + params.toString());

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

        //preuploaded presentation
        String presentationUrl = (String) params.get("presentation");
        meeting.setPresentation(presentationUrl);

        // groupSessions flag
        String groupSessionsStr = (String) params.get("groupSessions");
        boolean groupSessions = (groupSessionsStr != null &&
                (groupSessionsStr.toLowerCase().equals("on") || groupSessionsStr.toLowerCase().equals("true")));
        meeting.setGroupSessions(Boolean.valueOf(groupSessions));

        // participants
        String meetingOwnerId = meeting.getOwnerId();
        List<BBBMeetingParticipant> participants = extractParticipants(params, meetingOwnerId);
        participants.forEach(p -> p.setMeeting(meeting));
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
        log.debug("voiceBridgeStr:" + voiceBridgeStr);
        if (voiceBridgeStr == null || voiceBridgeStr.equals("")
                || Integer.parseInt(voiceBridgeStr) == 0) {
            Integer voiceBridge = 70000 + new Random().nextInt(10000);
            meeting.setVoiceBridge(voiceBridge);
        } else {
            meeting.setVoiceBridge(Integer.valueOf(voiceBridgeStr));
        }

        try {
            if (!meetingManager.createMeeting(meeting, notifyParticipants, addToCalendar, iCalAttached, iCalAlarmMinutes))
                throw new EntityException("Unable to store meeting in DB", meeting.getId(), 400);
        } catch (BBBException e) {
            throw new EntityException(e.getPrettyMessage(), meeting.getId(), 400);
        }

        return meeting.getId();
    }

    public void updateEntity(EntityReference ref, Object entity, Map<String, Object> params) {

        log.debug("updateMeeting");

        BBBMeeting newMeeting = (BBBMeeting) entity;

        try {
            BBBMeeting meeting = meetingManager.getMeeting(ref.getId());
            if (meeting == null) {
                throw new IllegalArgumentException("Could not locate meeting to update");
            }
            // update name
            String nameStr = (String) params.get("name");
            nameStr = StringEscapeUtils.escapeHtml(nameStr);
            if (nameStr != null) {
                meeting.setName(nameStr);
            }

            // update description
            String welcomeMessageStr = (String) params.get("properties.welcomeMessage");
            if (welcomeMessageStr != null) {
                meeting.getProperties().put("welcomeMessage", welcomeMessageStr);
            }

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

            // update default presentation if preuploadPresentation flag is true
            String presentationUrl = (String) params.get("presentation");
            if (presentationUrl != null && presentationUrl != "") {
                meeting.setPresentation(presentationUrl);
            } else {
                meeting.setPresentation("");
            }

            // update groupSessions flag
            String groupSessionsStr = (String) params.get("groupSessions");
            boolean groupSessions = (groupSessionsStr != null &&
                    (groupSessionsStr.toLowerCase().equals("on") || groupSessionsStr.toLowerCase().equals("true")));
            meeting.setGroupSessions(Boolean.valueOf(groupSessions));

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
            List<BBBMeetingParticipant> participants = extractParticipants(params, meetingOwnerId);
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
                if (!meetingManager.updateMeeting(meeting, notifyParticipants, addToCalendar, iCalAttached, iCalAlarmMinutes, false))
                    throw new EntityException("Unable to update meeting in DB", meeting.getId(), 400);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(), meeting.getId(), 400);
            }
        } catch (SecurityException se) {
            throw new EntityException(se.getMessage(), ref.getReference(), 400);
        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
    }

    public List<BBBMeeting> getEntities(EntityReference ref, Search search) {

        log.debug("getEntities");

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

        log.debug("deleteEntity");

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

        log.debug("getSettings");

        Map<String, Object> settings = new LinkedHashMap<>();

        String siteId = params.containsKey("siteId")? (String) params.get("siteId"): null;
        User user = userDirectoryService.getCurrentUser();
        Map<String, Object> currentUser = getCurrentUser(user);
        currentUser.put("role", meetingManager.getUserRoleInSite((String)currentUser.get("id"), siteId));
        currentUser.put("permissions", getUserPermissionsInSite((String)currentUser.get("id"), siteId));
        settings.put("currentUser", currentUser);

        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("autorefreshInterval", getAutorefreshInterval());
        config.put("addUpdateFormParameters", getAddUpdateFormConfigParameters());
        config.put("serverTimeInDefaultTimezone", meetingManager.getServerTimeInDefaultTimezone());
        config.put("serverTimeInUserTimezone", meetingManager.getServerTimeInUserTimezone());
        config.put("recordingFormatFilterEnabled", meetingManager.isRecordingFormatFilterEnabled());
        settings.put("config", config);
        settings.put("toolVersion", meetingManager.getToolVersion());
        return new ActionReturn(settings);
    }

    private Map<String, Object>getCurrentUser(User user) {

        Map<String, Object> currentUser = new LinkedHashMap<>();
        currentUser.put("id", user.getId());
        currentUser.put("displayId", user.getDisplayId());
        currentUser.put("displayName", user.getDisplayName());
        currentUser.put("eid", user.getEid());
        currentUser.put("email", user.getEmail());
        return currentUser;
    }

    private List<String> getUserPermissionsInSite(String userId, String siteId) {

        List<String> permissions = new ArrayList<>();
        if(meetingManager.isUserAllowedInLocation(userId, "site.viewRoster", siteId)) {
            permissions.add("site.viewRoster");
        }
        if(meetingManager.isUserAllowedInLocation(userId, "site.upd", siteId)) {
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
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_VIEW, siteId) )
            permissions.add(meetingManager.FN_RECORDING_VIEW);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_EDIT_OWN, siteId) )
            permissions.add(meetingManager.FN_RECORDING_EDIT_OWN);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_EDIT_ANY, siteId) )
            permissions.add(meetingManager.FN_RECORDING_EDIT_ANY);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_DELETE_OWN, siteId) )
            permissions.add(meetingManager.FN_RECORDING_DELETE_OWN);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_DELETE_ANY, siteId) )
            permissions.add(meetingManager.FN_RECORDING_DELETE_ANY);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_EXTENDEDFORMATS_OWN, siteId) )
            permissions.add(meetingManager.FN_RECORDING_EXTENDEDFORMATS_OWN);
        if( meetingManager.isUserAllowedInLocation(userId, meetingManager.FN_RECORDING_EXTENDEDFORMATS_ANY, siteId) )
            permissions.add(meetingManager.FN_RECORDING_EXTENDEDFORMATS_ANY);
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

        Map<String, String> interval = new LinkedHashMap<>();
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

        Map<String, Object> map = new LinkedHashMap<>();
        //UX settings for 'recording' checkbox
        Boolean recordingEnabled = Boolean.parseBoolean(meetingManager.isRecordingEnabled());
        if (recordingEnabled != null) {
            map.put("recordingEnabled", recordingEnabled);
        }
        Boolean recordingEditable = Boolean.parseBoolean(meetingManager.isRecordingEditable());
        if (recordingEditable != null) {
            map.put("recordingEditable", recordingEditable);
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
        Boolean waitmoderatorEditable = Boolean.parseBoolean(meetingManager.isWaitModeratorEditable());
        if (waitmoderatorEditable != null) {
            map.put("waitmoderatorEditable", waitmoderatorEditable);
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
        Boolean multiplesessionsallowedEditable = Boolean.parseBoolean(meetingManager.isMultipleSessionsAllowedEditable());
        if (multiplesessionsallowedEditable != null) {
            map.put("multiplesessionsallowedEditable", multiplesessionsallowedEditable);
        }
        Boolean multiplesessionsallowedDefault = Boolean.parseBoolean(meetingManager.getMultipleSessionsAllowedDefault());
        if (multiplesessionsallowedDefault != null) {
            map.put("multiplesessionsallowedDefault", multiplesessionsallowedDefault);
        }
        //UX settings for 'preupload presentation' box
        Boolean preuploadpresentationEnabled = Boolean.parseBoolean(meetingManager.isPreuploadPresentationEnabled());
        if (preuploadpresentationEnabled != null) {
            map.put("preuploadpresentationEnabled", preuploadpresentationEnabled);
        }
        //UX settings for 'group sessions' box
        Boolean groupsessionsEnabled = Boolean.parseBoolean(meetingManager.isGroupSessionsEnabled());
        if (groupsessionsEnabled != null) {
            map.put("groupsessionsEnabled", groupsessionsEnabled);
        }
        Boolean groupsessionsEditable = Boolean.parseBoolean(meetingManager.isGroupSessionsEditable());
        if (groupsessionsEditable != null) {
            map.put("groupsessionsEditable", groupsessionsEditable);
        }
        Boolean groupsessionsDefault = Boolean.parseBoolean(meetingManager.getGroupSessionsDefault());
        if (groupsessionsDefault != null) {
            map.put("groupsessionsDefault", groupsessionsDefault);
        }
        //UX settings for 'description' box
        String descriptionMaxLength = meetingManager.getMaxLengthForDescription();
        if (descriptionMaxLength != null) {
            map.put("descriptionMaxLength", descriptionMaxLength);
        }
        String descriptionType = meetingManager.getTextBoxTypeForDescription();
        if (descriptionType != null) {
            map.put("descriptionType", descriptionType);
        }
        return map;
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String isMeetingRunning(Map<String, Object> params) {

        log.debug("isMeetingRunning");

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

        log.debug("endMeeting");

        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException("Missing required parameter [meetingID]");
        }
        String groupId = (String) params.get("groupId");
        String endAll = (String) params.get("endAll");
        try {
            if (groupId != null) {
                return Boolean.toString(meetingManager.endMeeting(meetingID, groupId, false));
            } else if (endAll != null) {
                return Boolean.toString(meetingManager.endMeeting(meetingID, "", true));
            }

            return Boolean.toString(meetingManager.endMeeting(meetingID, "", false));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX + Entity.SEPARATOR + meetingID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public ActionReturn getMeetingInfo(OutputStream out, EntityView view, EntityReference ref, Map<String, Object> params) {

        log.debug("getMeetingInfo");

        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        try {
            String groupId = (String) params.get("groupId");
            Map<String, Object> meetingInfoResponse = meetingManager.getMeetingInfo(ref.getId(), groupId);
            return new ActionReturn(meetingInfoResponse);
        } catch (BBBException e) {
            return new ActionReturn(new HashMap<String, String>());
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public ActionReturn getRecordings(EntityReference ref, Map<String, Object> params) {

        log.debug("getRecordings");

        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        String groupId = (String) params.get("groupId");
        String siteId = (String) params.get("siteId");
        try {
            Map<String, Object> recordingsResponse = meetingManager.getRecordings(ref.getId(), groupId, siteId);
            return new ActionReturn(recordingsResponse);
        } catch (BBBException e) {
            return new ActionReturn(new HashMap<String, String>());
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getSiteRecordings(Map<String, Object> params) {

        log.debug("getSiteRecordings");

        String siteId = (String) params.get("siteId");

        if (!meetingManager.getCanViewSiteRecordings(siteId)) {
            throw new SecurityException("You are not allowed to view recordings");
        }

        try {
            Map<String, Object> recordingsResponse = meetingManager.getSiteRecordings(siteId);
            return new ActionReturn(recordingsResponse);
        } catch (Exception e) {
            log.error("Failed to retrieve site recordings", e);
            return new ActionReturn(new HashMap<String, String>());
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String publishRecordings(Map<String, Object> params) {

        log.debug("publishRecordings");

        String recordID = (String) params.get("recordID");
        if (recordID == null) {
            throw new IllegalArgumentException("Missing required parameter [recordID]");
        }
        String publish = (String) params.get("publish");
        if (publish == null) {
            throw new IllegalArgumentException("Missing required parameter [publish]");
        }

        try {
            return Boolean.toString(meetingManager.publishRecordings(recordID, publish));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX + Entity.SEPARATOR + recordID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String protectRecordings(Map<String, Object> params) {

        log.debug("protectRecordings");

        String recordID = (String) params.get("recordID");
        if (recordID == null) {
            throw new IllegalArgumentException("Missing required parameter [recordID]");
        }
        String protect = (String) params.get("protect");
        if (protect == null) {
            throw new IllegalArgumentException("Missing required parameter [protect]");
        }

        try {
            return Boolean.toString(meetingManager.protectRecordings(recordID, protect));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX
                    + Entity.SEPARATOR + recordID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String deleteRecordings(Map<String, Object> params) {

        log.debug("deleteRecordings");

        String recordID = (String) params.get("recordID");
        if (recordID == null) {
            throw new IllegalArgumentException("Missing required parameter [recordID]");
        }

        try {
            return Boolean.toString(meetingManager.deleteRecordings(recordID));
        } catch (BBBException e) {
            String ref = Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX
                    + Entity.SEPARATOR + recordID;
            throw new EntityException(e.getPrettyMessage(), ref, 400);
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_SHOW)
    public String getJoinMeetingUrl(OutputStream out, EntityView view, EntityReference ref) {

        log.debug("getJoinUrl");

        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        try {
            User user = userDirectoryService.getCurrentUser();
            String meetingId = ref.getId();
            BBBMeeting meeting = meetingManager.getMeeting(meetingId);
            if (meeting == null) {
                throw new EntityException("This meeting is no longer available.", null, 404);
            }
            //Unescape Meeting name
            String nameStr = meeting.getName();
            meeting.setName(StringEscapeUtils.unescapeHtml(nameStr));

            String joinUrl = meetingManager.getJoinUrl(meeting, user);

            if (joinUrl == null) {
                throw new EntityException("You are not allowed to join this meeting.", meeting.getId(), 403);
            }

            try {
                meetingManager.checkJoinMeetingPreConditions(meeting);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(), meeting.getId(), 400);
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

        log.debug("joinMeeting");

        if (ref == null) {
            throw new EntityNotFoundException("Meeting not found", null);
        }

        // get join url
        try {
            User user = userDirectoryService.getCurrentUser();
            String meetingId = ref.getId();
            BBBMeeting meeting = meetingManager.getMeeting(meetingId);
            if (meeting == null) {
                throw new EntityException("This meeting is no longer available.", null, 404);
            }

            //group sessions
            String groupId = (String) params.get("groupId");
            if (groupId != null && meeting.getGroupSessions()) {
                meeting.setId(meeting.getId() + "[" + groupId + "]");
            } else {
                meeting.setId(meeting.getId());
            }

            //Unescape Meeting name
            String groupTitle = (String) params.get("groupTitle");
            String nameStr = meeting.getName();
            if (groupTitle != null){
                meeting.setName(StringEscapeUtils.unescapeHtml(nameStr) + " (" + groupTitle + ")");
            } else {
                meeting.setName(StringEscapeUtils.unescapeHtml(nameStr));
            }

            String joinUrl = meetingManager.getJoinUrl(meeting, user);

            if (joinUrl == null) {
                throw new EntityException("You are not allowed to join this meeting.", meeting.getId(), 403);
            }

            // log meeting join event
            meetingManager.logMeetingJoin(meetingId);

            //Build the corresponding page for joining
            //If the user is not a moderator and WaitForModerator is enabled, do not create the meeting
            Boolean waitmoderatorEnabled = Boolean.parseBoolean(meetingManager.isWaitModeratorEnabled());
            if (waitmoderatorEnabled == null)
                waitmoderatorEnabled = true;
            String html;
            if( waitmoderatorEnabled && meeting.getWaitForModerator() ){
                BBBMeetingParticipant p = meetingManager.getParticipantFromMeeting(meeting, userDirectoryService.getCurrentUser().getId());
                if( !(Participant.MODERATOR).equals(p.getRole())) {
                    Map<String, Object> meetingInfo = null;
                    if(groupId != null && meeting.getGroupSessions())
                        meetingInfo = meetingManager.getMeetingInfo(meetingId, groupId);
                    else
                        meetingInfo = meetingManager.getMeetingInfo(meetingId, "");

                    if( meetingInfo == null || meetingInfo.isEmpty() || Integer.parseInt((String)meetingInfo.get("moderatorCount")) <= 0 ) {
                        //check for group session
                        if (groupId != null && meeting.getGroupSessions())
                            html = getHtmlForJoining(joinUrl, meetingId, WAITFORMODERATOR, groupId);
                        else
                            html = getHtmlForJoining(joinUrl, meetingId, WAITFORMODERATOR, "");
                        return html;
                    }
                }
            }
            //Else if the user is a moderator / WaitForModerator is not enabled, create the meeting
            try {
                meetingManager.checkJoinMeetingPreConditions(meeting);
            } catch (BBBException e) {
                throw new EntityException(e.getPrettyMessage(), meeting.getId(), 400);
            }

            //check for group session
            if (groupId != null && meeting.getGroupSessions())
                html = getHtmlForJoining(joinUrl, meetingId, NOTWAITFORMODERATOR, groupId);
            else
                html = getHtmlForJoining(joinUrl, meetingId);
            return html;

        } catch (Exception e) {
            throw new EntityException(e.getMessage(), ref.getReference(), 400);
        }
        // pre-join meeting
    }

    private String getHtmlForJoining(String joinUrl, String meetingId){
        return getHtmlForJoining(joinUrl, meetingId, NOTWAITFORMODERATOR, "");
    }

    private String getHtmlForJoining(String joinUrl, String meetingId, boolean waitformoderator, String groupId){
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
                   "                url: '/direct/bbb-tool/" + meetingId + "/getMeetingInfo.json" + (groupId.equals("") ? "" : "?groupId=" + groupId) + "',\n" +
                   "                dataType : 'json',\n" +
                   "                async : false,\n" +
                   "                cache: false,\n" +
                   "                success : function(data) {\n" +
                   "                    meetingInfo = data;\n" +
                   "                    if (JSON.stringify(meetingInfo) === JSON.stringify({}))\n" +
                   "                        meetingInfo.moderatorCount = 0;\n" +
                   "                },\n" +
                   "                error : function(xmlHttpRequest, status, error) {\n" +
                   "                    return null;\n" +
                   "                },\n" +
                   "                complete : function() {\n" +
                   "                    if( parseInt(meetingInfo.moderatorCount) == 0 ){\n" +
                   "                        setTimeout(worker, 5000);\n" +
                   "                    } else {\n" +
                   "                        if (typeof window.opener != 'undefined') {\n" +
                   "                           window.opener.setTimeout(\"meetings.utils.checkOneMeetingAvailability('" + meetingId + "'" + (groupId.equals("") ? "" : ", '" + groupId + "'") + ")\", 15000 );\n" +
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
                   //"    <script type='text/javascript' language='JavaScript'>\n" +
                   //"        window.opener.setTimeout(\"meetings.utils.checkOneMeetingAvailability('" + meetingId + "'" + (groupId.equals("") ? "" : ", '" + groupId + "'") + ")\", 15000 );\n" +
                   //"    </script>\n" +
                   "    <meta http-equiv='refresh' content='0; url=" + joinUrl + "' />\n" +
                   "  </head>\n" +
                   "  <body>\n" +
                   "  </body>\n" +
                   commonHtmlFooter;
        }
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getGroups(Map<String, Object> params) {

        log.debug("getGroups");

        String meetingID = (String) params.get("meetingID");
        if (meetingID == null) {
            throw new IllegalArgumentException("Missing required parameter [meetingID]");
        }

        //Get meeting
        BBBMeeting meeting = null;
        try {
            meeting = meetingManager.getMeeting(meetingID);
        } catch (Exception e) {
            return null;
        }

        Site site;
        try {
            site = siteService.getSite(meeting.getSiteId());
        } catch (IdUnusedException e) {
            log.error("Unable to get groups in '" + meeting.getName() + "'.", e);
            return null;
        }

        //Get user's group ids
        List<String> groupIds = new ArrayList<String>();
        if (meetingManager.getCanEdit(meeting.getSiteId(), meeting)) {
            for(Group g : site.getGroups())
                groupIds.add(g.getId());
        } else {
            groupIds = meetingManager.getUserGroupIdsInSite(userDirectoryService.getCurrentUser().getId(), meeting.getSiteId());
        }

        Map<String, Object> groups = new HashMap<String, Object>();

        for(int i = 0; i < groupIds.size(); i++){
            Map<String, String> groupInfo = new HashMap<String, String>();
            Group group = site.getGroup(groupIds.get(i));

            groupInfo.put("groupId", groupIds.get(i));
            groupInfo.put("groupTitle", group.getTitle());
            groups.put("group" + i, groupInfo);
        }

        return new ActionReturn(groups);
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public ActionReturn getUserSelectionOptions(Map<String, Object> params) {

        log.debug("getUserSelectionOptions");

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
                    log.warn("Could not retrieve displayName for userId: " + u.getUserId());
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

        log.debug("getNoticeText");

        Map<String, String> map = new HashMap<String, String>();
        String noticeText = meetingManager.getNoticeText();
        if (noticeText != null) {
            map.put("text", noticeText);
            map.put("level", meetingManager.getNoticeLevel());
        }
        return new ActionReturn(map);
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_NEW)
    public ActionReturn recordingReady(Map<String, Object> params) {
        String bbbSaltString = serverConfigurationService.getString(BBBMeetingManager.CFG_SALT);
        String bbbSalt = new String(Base64.encodeBase64(bbbSaltString.getBytes()));
        Claims claims = Jwts.parser().setSigningKey(bbbSalt).parseClaimsJws(params.get("signed_parameters").toString()).getBody();
        String meeting_id = claims.get("meeting_id").toString();

        boolean notified = meetingManager.recordingReady(meeting_id);

        ActionReturn response = null;
        if (notified) {
            response = new ActionReturn("OK");
            response.setResponseCode(200);
        } else {
            response = new ActionReturn("Gone");
            response.setResponseCode(410);
        }
        return response;
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_NEW)
    public String doUpload(Map<String, Object> params) {

        log.debug("Uploading File");

        String url = "";
        String siteId = (String) params.get("siteId");
        FileItem file = (FileItem) params.get("file");

        try {
            String filename = Validator.getFileName(file.getName());
            String contentType = file.getContentType();

            InputStream fileContentStream = file.getInputStream();
            InputStreamReader reader = new InputStreamReader(fileContentStream);

            if (fileContentStream != null) {
                String name = Validator.getFileName(filename);
                String resourceId = Validator.escapeResourceName(name);

                ResourcePropertiesEdit props = contentHostingService.newResourceProperties();
                props.addProperty(ResourceProperties.PROP_DISPLAY_NAME, name);
                props.addProperty(ResourceProperties.PROP_DESCRIPTION, filename);
                SecurityAdvisor sa = uploadFileSecurityAdvisor();
                try {
                    if (siteId != null) {
                        ContentResource attachment = contentHostingService.addAttachmentResource(resourceId, siteId, "Meetings", contentType, fileContentStream, props);
                        securityService.pushAdvisor(sa);
                        // Make sure the resource is closed to public.
                        contentHostingService.setPubView(attachment.getId(), false);
                        url = attachment.getUrl();
                    } else {
                        log.debug("Upload failed; Site not found");
                    }
                } catch(IdInvalidException e) {
                    log.debug(e.getMessage());
                } catch(InconsistentException e) {
                    log.debug(e.getMessage());
                } catch(IdUsedException e) {
                    log.debug(e.getMessage());
                } catch(PermissionException e) {
                    log.debug(e.getMessage());
                } catch(OverQuotaException e) {
                    log.debug(e.getMessage());
                } catch(ServerOverloadException e) {
                    log.debug(e.getMessage());
                }
            }
        } catch(IOException e) {
            log.debug("Failed to upload file");
            log.debug(e.getMessage());
        }
        return url;
    }

    private SecurityAdvisor uploadFileSecurityAdvisor() {
        return (userId, function, reference) -> {
            //Needed to be able to add or modify their own
            if (function.equals(contentHostingService.AUTH_RESOURCE_ADD) ||
              function.equals(contentHostingService.AUTH_RESOURCE_WRITE_OWN) ||
              function.equals(contentHostingService.AUTH_RESOURCE_HIDDEN) ) {
                return SecurityAdvisor.SecurityAdvice.ALLOWED;
            } else if (function.equals(contentHostingService.AUTH_RESOURCE_WRITE_ANY)) {
                log.info(userId + " requested ability to write to any content on " + reference +
                    " which we didn't expect, this should be investigated");
                return SecurityAdvisor.SecurityAdvice.ALLOWED;
            }
			      return SecurityAdvisor.SecurityAdvice.PASS;
        };
    }

    @EntityCustomAction(viewKey = EntityView.VIEW_LIST)
    public String removeUpload(Map<String, Object> params) {

        log.debug("Removing File");

        String resourceId = (String) params.get("url");
        String meetingId = (String) params.get("meetingId");

        SecurityAdvisor sa = removeUploadSecurityAdvisor();
        try {
            securityService.pushAdvisor(sa);
            contentHostingService.removeResource(resourceId);
        } catch (PermissionException e) {
            log.debug(e.getMessage());
            return Boolean.toString(false);
        } catch (IdUnusedException e) {
            log.debug(e.getMessage());
            return Boolean.toString(false);
        } catch (TypeException e) {
            log.debug(e.getMessage());
            return Boolean.toString(false);
        } catch (InUseException e) {
            log.debug(e.getMessage());
            return Boolean.toString(false);
        }

        if(meetingId != null && meetingId != ""){
            try {
                BBBMeeting meeting = meetingManager.getMeeting(meetingId);
                if (meeting != null) {
                    meeting.setPresentation("");
                    try {
                        //Update meeting presentation value
                        if (!meetingManager.updateMeeting(meeting, false, false, false, 0L, true))
                            throw new EntityException("Unable to update meeting in DB", meeting.getId(), 400);
                    } catch (BBBException e) {
                        throw new EntityException(e.getPrettyMessage(), meeting.getId(), 400);
                    }
                }
            } catch (SecurityException se) {
                log.debug(se.getMessage());
                return Boolean.toString(false);
            } catch (Exception e) {
                log.debug(e.getMessage());
                return Boolean.toString(false);
            }
        }
        return Boolean.toString(true);
    }

    private SecurityAdvisor removeUploadSecurityAdvisor() {
        return (userId, function, reference) -> {
            if (function.equals(contentHostingService.AUTH_RESOURCE_REMOVE_OWN) ||
              function.equals(contentHostingService.AUTH_RESOURCE_HIDDEN) ) {
                return SecurityAdvisor.SecurityAdvice.ALLOWED;
            } else if (function.equals(contentHostingService.AUTH_RESOURCE_REMOVE_ANY)) {
                log.info(userId + " requested ability to remove any content on " + reference +
                    " which we didn't expect, this should be investigated");
                return SecurityAdvisor.SecurityAdvice.ALLOWED;
            }
            return SecurityAdvisor.SecurityAdvice.PASS;
        };
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
    private List<BBBMeetingParticipant> extractParticipants(Map<String, Object> params,
            String meetingOwnerId) {

        List<BBBMeetingParticipant> participants = new ArrayList<>();
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
                BBBMeetingParticipant p = new BBBMeetingParticipant();
                p.setSelectionType(selectionType);
                p.setSelectionId(selectionId);
                p.setRole(role);
                participants.add(p);
                //participants.add(new Participant(selectionType, selectionId,role));
            }
        }
        return participants;
    }

    /** Generate a random password */
    private String generatePassword() {
        return Long.toHexString(new Random(System.currentTimeMillis()).nextLong());
    }
}
