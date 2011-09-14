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

package org.sakaiproject.bbb.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyFactoryRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.UidGenerator;

import org.apache.log4j.Logger;
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
import org.sakaiproject.email.api.AddressValidationException;
import org.sakaiproject.email.api.Attachment;
import org.sakaiproject.email.api.ContentType;
import org.sakaiproject.email.api.EmailAddress;
import org.sakaiproject.email.api.EmailMessage;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.email.api.NoRecipientsException;
import org.sakaiproject.email.api.EmailAddress.RecipientType;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ResourceLoader;

/**
 * BBBMeetingManager is the API for managing BigBlueButton meetings.
 * @author Adrian Fish,Nuno Fernandes
 */
public class BBBMeetingManagerImpl implements BBBMeetingManager
{
	/** Logger */
	protected final Logger				logger						= Logger.getLogger(getClass());

	// --- Spring injected services ---
	private BBBStorageManager			storageManager				= null;
	private BBBAPIWrapper				bbbAPI						= null;
	private UserDirectoryService		userDirectoryService		= null;
	private SiteService					siteService					= null;
	private EmailService				emailService				= null;
	private EventTrackingService		eventTrackingService		= null;
	private SecurityService				securityService				= null;
	private AuthzGroupService			authzGroupService			= null;
	private SessionManager				sessionManager				= null;
	private FunctionManager				functionManager				= null;
	private ServerConfigurationService	serverConfigurationService	= null;
	private PreferencesService			preferencesService			= null;
	private TimeService					timeService					= null;
    
    
    // -----------------------------------------------------------------------
	// --- Initialization/Spring related methods -----------------------------
	// -----------------------------------------------------------------------
	public void init()
	{
		logger.info("init()");
		
		// setup db tables
		storageManager.setupTables();
		
		// register security functions
		try{
			Method registerUserMutableFunctionMethod = functionManager.getClass()
				.getMethod("registerFunction", new Class[]{String.class, boolean.class});
			logger.debug("Using new API call registerFunction(String function, boolean userMutable)");
			for(int f=0; f<FUNCTIONS.length; f++)
			{
				registerUserMutableFunctionMethod.invoke(functionManager, new Object[]{FUNCTIONS[f], true});
			}
		}catch(Exception e){
			logger.debug("API call registerFunction(String function, boolean userMutable) unavailable: falling back to registerFunction(String function)");
			for(int f=0; f<FUNCTIONS.length; f++)
			{
				functionManager.registerFunction(FUNCTIONS[f]);
			}
		}
		
		// This has to be called after the BBB tables have been created
		bbbAPI.start();
	}
	
	public void setStorageManager(BBBStorageManager storageManager) 
	{
		this.storageManager = storageManager;
	}
	
	public void setBbbAPIWrapper(BBBAPIWrapper bbbAPI) 
	{
		this.bbbAPI = bbbAPI;
	}

	public void setUserDirectoryService(UserDirectoryService userDirectoryService)
	{
		this.userDirectoryService = userDirectoryService;
	}
	
	public void setSiteService(SiteService siteService)
	{
		this.siteService = siteService;
	}

	public void setEmailService(EmailService emailService)
	{
		this.emailService = emailService;
	}

	public void setEventTrackingService(EventTrackingService eventTrackingService)
	{
		this.eventTrackingService = eventTrackingService;
	}

	public void setSecurityService(SecurityService securityService) 
	{
		this.securityService = securityService;
	}
	
	public void setAuthzGroupService(AuthzGroupService authzGroupService) 
	{
		this.authzGroupService = authzGroupService;
	}
	
	public void setSessionManager(SessionManager sessionManager) 
	{
		this.sessionManager = sessionManager;
	}
	
	public void setFunctionManager(FunctionManager functionManager)
	{
		this.functionManager = functionManager;
	}

	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService)
	{
		this.serverConfigurationService = serverConfigurationService;
	}
	
	public void setPreferencesService(PreferencesService preferencesService)
	{
		this.preferencesService = preferencesService;
	}
	
	public void setTimeService(TimeService timeService)
	{
		this.timeService = timeService;
	}
	

	// -----------------------------------------------------------------------
	// --- BBB Implementation related methods --------------------------------
	// -----------------------------------------------------------------------
	public BBBMeeting getMeeting(String meetingId) throws SecurityException
	{
		return processMeeting(storageManager.getMeeting(meetingId));
	}

	public List<BBBMeeting> getSiteMeetings(String siteId) throws SecurityException
	{		
		List<BBBMeeting> filteredMeetings = new ArrayList<BBBMeeting>();
		
		// Grab all the meetings for this site
		List<BBBMeeting> meetings = storageManager.getSiteMeetings(siteId);
		for(BBBMeeting meeting : meetings)
		{
			meeting = processMeeting(meeting);
			if(meeting != null)
			{
				// add to meeting list
				filteredMeetings.add(meeting);
			}
		}
		
		return filteredMeetings;
	}
	
	public boolean createMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar) throws SecurityException, BBBException
	{
		if(!getCanCreate(meeting.getSiteId())) {
			throw new SecurityException("You are not allow to create meetings in this site");
		}
		
		// convert dates from user timezone
		meeting.setStartDate(convertDateFromUserTimezone(meeting.getStartDate()));
		meeting.setEndDate(convertDateFromUserTimezone(meeting.getEndDate()));
		
		// create meeting in BBB
		meeting = bbbAPI.createMeeting(meeting);
		
		// store locally, in DB
		if(storageManager.storeMeeting(meeting))
		{
			// send email notifications to participants
			if(notifyParticipants)
			{
				notifyParticipants(meeting);
			}
			
			// add start date to Calendar
			if(addToCalendar && meeting.getStartDate() != null)
			{
				addEditCalendarEvent(meeting);
			}
			
			// set meeting join url (for moderator, which is current user)
			meeting.setJoinUrl( bbbAPI.getJoinMeetingURL(meeting.getId(), userDirectoryService.getCurrentUser(), meeting.getModeratorPassword()) );
			        		
			// log event
			logEvent(EVENT_MEETING_CREATE, meeting);
			
      		return true;
		}
		else
		{
			bbbAPI.endMeeting(meeting.getId(), meeting.getModeratorPassword());
			logger.error("Unable to store BBB meeting in Sakai");
			return false;
		}
	}
	
	public boolean updateMeeting(BBBMeeting meeting, boolean notifyParticipants, boolean addToCalendar) throws SecurityException, BBBException
	{
		if(!getCanEdit(meeting.getSiteId(), meeting)) {
			throw new SecurityException("You are not allow to update this meeting");
		}
		
		// convert dates from user timezone
		meeting.setStartDate(convertDateFromUserTimezone(meeting.getStartDate()));
		meeting.setEndDate(convertDateFromUserTimezone(meeting.getEndDate()));
		
		// store locally, in DB
		if(storageManager.updateMeeting(meeting, true))
		{
			// send email notifications to participants
			if(notifyParticipants)
			{
				notifyParticipants(meeting);
			}
			
			// add start date to Calendar
			if(addToCalendar && meeting.getStartDate() != null)
			{
				addEditCalendarEvent(meeting);
			}
			// or remove it, if 'add to calendar' was unselected
			else if(meeting.getProps().getCalendarEventId() != null && !addToCalendar)
			{
				removeCalendarEvent(meeting);
				storageManager.updateMeeting(meeting, false);
			}
			
			// set meeting join url (for moderator, which is current user)
			meeting.setJoinUrl( bbbAPI.getJoinMeetingURL(meeting.getId(), userDirectoryService.getCurrentUser(), meeting.getModeratorPassword()) );
			        		
			// log event
			logEvent(EVENT_MEETING_EDIT, meeting);
			
      		return true;
		}
		else
		{
			logger.error("Unable to update BBB meeting in Sakai");
			return false;
		}
	}
	
    public boolean isMeetingRunning(String meetingID) throws BBBException
    {
        return bbbAPI.isMeetingRunning(meetingID);
    }
    
    public Map<String,Object> getMeetingInfo(String meetingID) throws BBBException
    {
    	BBBMeeting meeting = storageManager.getMeeting(meetingID);
    	return bbbAPI.getMeetingInfo(meeting.getId(), meeting.getModeratorPassword());
    }
    
    public Map<String,Object> getRecordings(String meetingID) throws BBBException
    {
    	BBBMeeting meeting = storageManager.getMeeting(meetingID);
    	//return bbbAPI.getMeetingInfo(meeting.getId(), meeting.getModeratorPassword());
    	return bbbAPI.getRecordings(meeting.getId(), meeting.getModeratorPassword());
    }

    public void logMeetingJoin(String meetingId)
    {
    	BBBMeeting meeting = storageManager.getMeeting(meetingId);
		logEvent(EVENT_MEETING_JOIN, meeting);
    }
    
	public boolean endMeeting(String meetingId) throws SecurityException, BBBException
	{
		BBBMeeting meeting = storageManager.getMeeting(meetingId);
		
		if(!getCanDelete(meeting.getSiteId(), meeting)) {
			throw new SecurityException("You are not allow to end this meeting");
		}
		
		// end meeting on server, if running
		bbbAPI.endMeeting(meetingId, meeting.getModeratorPassword());
		
		// log event
		logEvent(EVENT_MEETING_END, meeting);
		
		// remove event from Calendar
		removeCalendarEvent(meeting);
		
		// remove from DB, if no exceptions were thrown
		storageManager.deleteMeeting(meetingId);
		return true;
	}
	
	public void checkJoinMeetingPreConditions(BBBMeeting meeting) throws BBBException
	{
		// check if meeting is within dates
		Date now = new Date();
		boolean startOk = meeting.getStartDate() == null || meeting.getStartDate().before(now);
		boolean endOk = meeting.getEndDate() == null || meeting.getEndDate().after(now);
		if(!startOk)
			throw new BBBException(BBBException.MESSAGEKEY_NOTSTARTED, "Meeting has not started yet.");
		if(!endOk)
			throw new BBBException(BBBException.MESSAGEKEY_ALREADYENDED, "Meeting has already ended.");
		
		// check if is running, (re)create it if not
		bbbAPI.makeSureMeetingExists(meeting);
	}
	

	// -----------------------------------------------------------------------
	// --- BBB Security related methods --------------------------------------
	// -----------------------------------------------------------------------
	public boolean isMeetingParticipant(BBBMeeting meeting)
	{
		User currentUser = userDirectoryService.getCurrentUser();
		return getParticipantFromMeeting(meeting, currentUser.getId()) != null;
	}
	
	public boolean getCanCreate(String siteId)
	{
		return isUserAllowedInLocation(
				userDirectoryService.getCurrentUser().getId(), 
				FN_CREATE, 
				siteId);
	}
	
	public boolean getCanEdit(String siteId, BBBMeeting meeting)
	{
		String currentUserId = userDirectoryService.getCurrentUser().getId();
		
		if(meeting != null)
		{
			// check if owns meeting
			if(currentUserId.equals(meeting.getOwnerId()))
			{
				if(isUserAllowedInLocation( currentUserId, FN_EDIT_OWN, siteId))
					return true;
			}
		}
		
		// otherwise, must be able to edit any meeting
		return isUserAllowedInLocation( currentUserId, FN_EDIT_ANY, siteId);
	}
	
	public boolean getCanDelete(String siteId, BBBMeeting meeting)
	{
		String currentUserId = userDirectoryService.getCurrentUser().getId();
		
		if(meeting != null)
		{
			// check if owns meeting
			if(currentUserId.equals(meeting.getOwnerId()))
			{
				if(isUserAllowedInLocation( currentUserId, FN_DELETE_OWN, siteId))
					return true;
			}
		}
		
		// otherwise, must be able to delete any meeting
		return isUserAllowedInLocation( currentUserId, FN_DELETE_ANY, siteId);
	}
	
	public boolean getCanParticipate(String siteId)
	{
		return isUserAllowedInLocation(
				userDirectoryService.getCurrentUser().getId(), 
				FN_PARTICIPATE, 
				siteId);
	}
	
	public void checkPermissions(String siteId)
	{
		try{
			// get site roles & tool permisions
			Set<Role> roles = null;
			boolean noPermsDefined = true;
			// get site
			//final Site site = siteService.getSite(siteId);
			final AuthzGroup siteAuthz = authzGroupService.getAuthzGroup(siteService.siteReference(siteId));
			// get roles
			roles = siteAuthz.getRoles();
			if(roles == null || roles.size() == 0) throw new Exception("No roles defined in site!");
			// check if need to apply defaults (no perms defined)
			for(Role r : roles) {
				Set<String> functions = r.getAllowedFunctions();
				if(functions != null && functions.size() > 0) {
					for(String fn : functions) {
						if(fn.startsWith(FN_PREFIX)) {
							noPermsDefined = false;
							break;
						}
					}
				}
			}
		
			// no perms defined => apply defaults, if any
			boolean permsSet = false;
			if(noPermsDefined) {
				for(Role r : roles) {
					String roleId = r.getId();
					String fns_ = serverConfigurationService.getString(CFG_DEFAULT_PERMS_PRFX + roleId, null);
					if(fns_ != null) {
						String[] fns = fns_.split(",");
						for(int i=0; i<fns.length; i++) {
							r.allowFunction(fns[i].trim());
						}
						permsSet = true;
					}
				}
			}
			
			// apply (new) permissions, if defined
			if(permsSet) {
				AdminExecution exec = new AdminExecution() {			
					@Override
					public Object execution() throws Exception {
						authzGroupService.save(siteAuthz);
						//siteService.save(site);
						return null;
					}
				};
				try{
					exec.execute();
				}catch(Exception e){
					logger.warn("Unable to apply default BBB permissions to site "+siteId+": "+e.getMessage(), e);
				}
			}
			
		}catch(Exception e){
			logger.warn("Unable to check tool permissions on site: "+e.getMessage(), e);
			return;
		}
	}
	
	
	// -----------------------------------------------------------------------
	// --- Public utility methods --------------------------------------------
	// -----------------------------------------------------------------------
	public long getServerTimeInUserTimezone()
	{
		Preferences prefs = preferencesService.getPreferences(userDirectoryService.getCurrentUser().getId());
		TimeZone timeZone = null;
		if(prefs != null) {
			ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
			String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
			timeZone = timeZoneStr != null ? TimeZone.getTimeZone(timeZoneStr) : TimeZone.getDefault();
		}else{
			timeZone = TimeZone.getDefault();
		}
		long timeMs = System.currentTimeMillis();
		return timeMs					 					// server time in millis 
				+ timeZone.getOffset(timeMs)				// user timezone offset 
				- TimeZone.getDefault().getOffset(timeMs);	// server timezone offset
	}
	
	public String getNoticeText() {
		String bbbNoticeText = serverConfigurationService.getString(CFG_NOTICE_TEXT, null);
		if(bbbNoticeText != null && "".equals(bbbNoticeText.trim())) bbbNoticeText = null;
		return bbbNoticeText;
	}
	
	public String getNoticeLevel() {
		return serverConfigurationService.getString(CFG_NOTICE_LEVEL, "info").trim().toLowerCase();
	}
	

	// -----------------------------------------------------------------------
	// --- BBB Private methods -----------------------------------------------
	// -----------------------------------------------------------------------	
	private BBBMeeting processMeeting(BBBMeeting meeting) throws SecurityException
	{
		if(meeting == null) return null;
		
		// determine owner name
		if(meeting.getOwnerId() != null) {
			try{
				String ownerDisplayName = userDirectoryService.getUser(meeting.getOwnerId()).getDisplayName();
				meeting.setOwnerDisplayName(ownerDisplayName);
			}catch(UserNotDefinedException e){
				meeting.setOwnerDisplayName(meeting.getOwnerId());
			}
		}
		
		// convert dates to user timezone
		meeting.setStartDate(convertDateToUserTimezone(meeting.getStartDate()));
		meeting.setEndDate(convertDateToUserTimezone(meeting.getEndDate()));
		
		Participant p = getParticipantFromMeeting(meeting, userDirectoryService.getCurrentUser().getId());
		
		// Case #1: is participant
		if(getCanParticipate(meeting.getSiteId()) && p != null) 
		{							
			// build join url
			boolean isModerator = Participant.MODERATOR.equals(p.getRole());
			String password = isModerator ? meeting.getModeratorPassword() : meeting.getAttendeePassword();
			String joinURL = bbbAPI.getJoinMeetingURL(meeting.getId(), userDirectoryService.getCurrentUser(), password);
			meeting.setJoinUrl(joinURL);
			
			return meeting;
		}
		
		// Case #2: is not a participant but can manage tool
		else if( getCanEdit(meeting.getSiteId(), meeting) || getCanDelete(meeting.getSiteId(), meeting) )
		{				
			// reset join url
			meeting.setJoinUrl(null);
			
			return meeting;
		}
		
		return null;
	}
	
	private void notifyParticipants(BBBMeeting meeting)
	{
		// Site title, url and directtool (universal) url for joining meeting
		Site site;
		try{
			site = siteService.getSite(meeting.getSiteId());
		}catch(IdUnusedException e){
			logger.error("Unable to send notifications for '"+meeting.getName()+"' meeting participants.", e);
			return;
		}
		String siteTitle = site.getTitle();
		String directToolJoinUrl = getDirectToolJoinUrl(meeting);
		
		// Meeting participants
		Map<String, Set<User>> meetingUsers = new HashMap<String, Set<User>>();
		for(Participant p : meeting.getParticipants())
		{
			if(Participant.SELECTION_ALL.equals(p.getSelectionType())) {
				Set<String> siteUserIds = site.getUsers();
				for(String userId : siteUserIds) {
					try{
						String userLocale = getUserLocale(userId);
						Set<User> localeUsers = meetingUsers.get(userLocale);
						if(localeUsers == null) localeUsers = new HashSet<User>();
						localeUsers.add(userDirectoryService.getUser(userId));
						meetingUsers.put(userLocale, localeUsers);
					}catch(UserNotDefinedException e){
						logger.warn("Unable to notify user '"+userId+"' about '"+meeting.getName()+"' meeting.", e);
					}
				}
				break;
			}
			if(Participant.SELECTION_GROUP.equals(p.getSelectionType())) {
				Group group = site.getGroup(p.getSelectionId());
				Set<String> groupUserIds = group.getUsers();
				for(String userId : groupUserIds) {
					try{
						String userLocale = getUserLocale(userId);
						Set<User> localeUsers = meetingUsers.get(userLocale);
						if(localeUsers == null) localeUsers = new HashSet<User>();
						localeUsers.add(userDirectoryService.getUser(userId));
						meetingUsers.put(userLocale, localeUsers);
					}catch(UserNotDefinedException e){
						logger.warn("Unable to notify user '"+userId+"' about '"+meeting.getName()+"' meeting.", e);
					}
				}
			}
			if(Participant.SELECTION_ROLE.equals(p.getSelectionType())) {
				Set<String> roleUserIds = site.getUsersHasRole(p.getSelectionId());
				for(String userId : roleUserIds) {
					try{
						String userLocale = getUserLocale(userId);
						Set<User> localeUsers = meetingUsers.get(userLocale);
						if(localeUsers == null) localeUsers = new HashSet<User>();
						localeUsers.add(userDirectoryService.getUser(userId));
						meetingUsers.put(userLocale, localeUsers);
					}catch(UserNotDefinedException e){
						logger.warn("Unable to notify user '"+userId+"' about '"+meeting.getName()+"' meeting.", e);
					}
				}
			}
			if(Participant.SELECTION_USER.equals(p.getSelectionType())) {
				try{
					String userId = p.getSelectionId();
					String userLocale = getUserLocale(userId);
					Set<User> localeUsers = meetingUsers.get(userLocale);
					if(localeUsers == null) localeUsers = new HashSet<User>();
					localeUsers.add(userDirectoryService.getUser(userId));
					meetingUsers.put(userLocale, localeUsers);
				}catch(UserNotDefinedException e){
					logger.warn("Unable to notify user '"+p.getSelectionId()+"' about '"+meeting.getName()+"' meeting.", e);
				}
			}
		}
		
		// generate an ical to attach to email (if, at least, start date is defined)
		String icalFilename = generateIcalFromMeeting(meeting);
		final File icalFile = icalFilename != null ? new File(icalFilename) : null;
		if(icalFile != null) icalFile.deleteOnExit();
		
		// iterate over all user locales found
		for(String locale : meetingUsers.keySet()) {
			logger.debug("Sending "+ locale + " notifications to "+meetingUsers.get(locale).size()+" users.");
			String sampleUserId = meetingUsers.get(locale).iterator().next().getId();
			ResourceLoader msgs = new ResourceLoader(sampleUserId, "EmailNotification");
		
			// Email message
			final String emailTitle = msgs.getFormattedMessage("email.title", new Object[]{
					siteTitle, 
					meeting.getName()
					});
			StringBuilder msg = new StringBuilder();
			msg.append(msgs.getFormattedMessage("email.header", new Object[] {}));
			msg.append(msgs.getFormattedMessage("email.body", new Object[]{
					siteTitle, 
					serverConfigurationService.getString("ui.institution"), 
					directToolJoinUrl, 
					meeting.getName()
					}));
			String meetingOwnerEid = null;
			try{
				meetingOwnerEid = userDirectoryService.getUserEid(meeting.getOwnerId());
			}catch(UserNotDefinedException e1){
				meetingOwnerEid = meeting.getOwnerId();
			}
			msg.append(msgs.getFormattedMessage("email.body.meeting_details", new Object[]{
					meeting.getName(),
					meeting.getProps().getWelcomeMessage(),
					meeting.getStartDate() == null ? "-" : convertDateToUserTimezone(meeting.getStartDate()),
					meeting.getEndDate() == null ? "-" : convertDateToUserTimezone(meeting.getEndDate()),
					meeting.getOwnerDisplayName() + " (" + meetingOwnerEid + ")"
					}));
			msg.append(msgs.getFormattedMessage("email.footer", new Object[]{
					serverConfigurationService.getString("ui.institution"),
					serverConfigurationService.getServerUrl() + "/portal",
					siteTitle
					}));
			
			// Send (a single) email (per locale)!
			final String emailMessage = msg.toString();
			final Set<User> emailRecipients = meetingUsers.get(locale);
			
			try{
				new Thread(new Runnable() {
					public void run() {
						for (User emailRecipient : emailRecipients) {
							if(emailRecipient.getEmail() != null && !emailRecipient.getEmail().trim().equals("")) {
								EmailMessage email = new EmailMessage();
								email.setFrom(new EmailAddress("no-reply@" + serverConfigurationService.getServerName(), serverConfigurationService.getString("ui.institution")));
								email.setRecipients(RecipientType.TO, Arrays.asList( new EmailAddress(emailRecipient.getEmail(), emailRecipient.getDisplayName()) ));
								email.addHeader("Content-Type", "text/html; charset=ISO-8859-1");
								email.setContentType(ContentType.TEXT_HTML);
								email.setSubject(emailTitle);
								email.setBody(emailMessage);
								if(icalFile != null && icalFile.canRead()) {
									email.addAttachment(new Attachment(icalFile, "Calendar_Event.ics"));
								}
								try{
									emailService.send(email);
								}catch(Exception e){
									logger.warn("Unable to send email notification to "+emailRecipient.getEmail()+" about new BBB meeting", e);
								}
							}
						}
					}
				}).start();			
			}catch(Exception e){
				logger.error("Unable to send "+ locale + " notifications for '"+meeting.getName()+"' meeting participants.", e);
			}
		}
	}
	
	private String getUserLocale(String userId)
	{
		Preferences prefs = preferencesService.getPreferences(userId);
		ResourceProperties locProps = prefs.getProperties(ResourceLoader.APPLICATION_ID);
		String localeString = locProps.getProperty(ResourceLoader.LOCALE_KEY);
		if(localeString == null)
			localeString = Locale.getDefault().toString();
		logger.debug("Locale for user "+userId+" is "+localeString);
		return localeString;
	}
	
	private String getDirectToolJoinUrl(BBBMeeting meeting) 
	{
		try{
			Site site = siteService.getSite(meeting.getSiteId());
			StringBuilder url = new StringBuilder();
			url.append(serverConfigurationService.getServerUrl());
			url.append("/portal");
			url.append("/directtool/");
			url.append(site.getToolForCommonId(TOOL_ID).getId());
			url.append("?state=joinMeeting&meetingId=");
			url.append(meeting.getId());
			return url.toString();
		}catch(IdUnusedException e){
			logger.warn("Unable to determine siteId from meeting with id: "+meeting.getId(), e);
			StringBuilder url = new StringBuilder();
			url.append(serverConfigurationService.getServerUrl());
			url.append("/portal");
			return url.toString();
		}
	}
	
	@SuppressWarnings("deprecation")
	private boolean addEditCalendarEvent(BBBMeeting meeting)
	{
		logger.debug("addEditCalendarEvent");
		String eventId = meeting.getProps().getCalendarEventId();
		boolean newEvent = eventId == null;
		try
		{
			// get CalendarService
			Object calendarService = ComponentManager.get("org.sakaiproject.calendar.api.CalendarService");
			
			// get site calendar
			String calendarRef = (String) calendarService.getClass()
				.getMethod("calendarReference", new Class[]{String.class, String.class})
				.invoke(calendarService, new Object[]{meeting.getSiteId(), SiteService.MAIN_CONTAINER});
			Object calendar = calendarService.getClass()
				.getMethod("getCalendar", new Class[]{String.class})
				.invoke(calendarService, new Object[]{calendarRef});
	        
			// build time range (with dates on user timezone - calendar does conversion)
			Time startTime = timeService.newTime(convertDateToUserTimezone(meeting.getStartDate()).getTime());
			TimeRange range = null;
			if(meeting.getEndDate() != null) {
				Time endTime = timeService.newTime(convertDateToUserTimezone(meeting.getEndDate()).getTime());
				range = timeService.newTimeRange(startTime, endTime);
			}else{
				range = timeService.newTimeRange(startTime);
			}
			
			// add or edit event
			Object event = null;
			if(newEvent) {
				event = calendar.getClass()
					.getMethod("addEvent", new Class[]{})
					.invoke(calendar, new Object[]{});
				event.getClass().getMethod("setCreator", new Class[]{}).invoke(event, new Object[]{});
				eventId = (String) event.getClass().getMethod("getId", new Class[]{}).invoke(event, new Object[]{});
			}else{
				// EVENT_MODIFY_CALENDAR = "calendar.revise"
				String eventModify = (String) calendarService.getClass().getField("EVENT_MODIFY_CALENDAR").get(null);
				event = calendar.getClass()
					.getMethod("getEditEvent", new Class[]{String.class, String.class})
					.invoke(calendar, new Object[]{eventId, eventModify});
			}

			// set event fields
			event.getClass().getMethod("setDisplayName", new Class[]{String.class}).invoke(event, new Object[]{meeting.getName()});
			event.getClass().getMethod("setDescription", new Class[]{String.class}).invoke(event, new Object[]{"Meeting '"+meeting.getName()+"' scheduled."/*meeting.getWelcome()*/});
			event.getClass().getMethod("setType", new Class[]{String.class}).invoke(event, new Object[]{"Meeting"});
			event.getClass().getMethod("setRange", new Class[]{TimeRange.class}).invoke(event, new Object[]{range});
			event.getClass().getMethod("setModifiedBy", new Class[]{}).invoke(event, new Object[]{});
			event.getClass().getMethod("clearGroupAccess", new Class[]{}).invoke(event, new Object[]{});
			event.getClass().getMethod("setField", new Class[]{String.class, String.class}).invoke(event, new Object[]{"meetingId", meeting.getId()});
			
			// commit event
			calendar.getClass()
				.getMethod("commitEvent", new Class[]{Class.forName("org.sakaiproject.calendar.api.CalendarEventEdit")})
				.invoke(calendar, new Object[]{event});
			
			// update calendar eventId locally, in DB
			if(newEvent && eventId != null)
			{
				meeting.getProps().setCalendarEventId(eventId);
				if(!storageManager.updateMeeting(meeting, false))
				{
					logger.error("Unable to update BBB meeting with Calendar.eventId in Sakai");
					return false;
				}
			}
		}
		catch(Exception e)
		{
			logger.warn("Unable to add event to Calendar (no permissions or site has no Calendar tool).");
			return false;
		}
		return true;
	}
	
	private boolean removeCalendarEvent(BBBMeeting meeting)
	{
		logger.debug("removeCalendarEvent");
		String eventId = meeting.getProps().getCalendarEventId();
		if(eventId != null)
		{
			try
			{
				// get CalendarService
				Object calendarService = ComponentManager.get("org.sakaiproject.calendar.api.CalendarService");
				
				// get site calendar
				String calendarRef = (String) calendarService.getClass()
					.getMethod("calendarReference", new Class[]{String.class, String.class})
					.invoke(calendarService, new Object[]{meeting.getSiteId(), SiteService.MAIN_CONTAINER});
				Object calendar = calendarService.getClass()
					.getMethod("getCalendar", new Class[]{String.class})
					.invoke(calendarService, new Object[]{calendarRef});
				
				// remove event
				// EVENT_REMOVE_CALENDAR = "calendar.delete"
				String eventRemove = (String) calendarService.getClass().getField("EVENT_REMOVE_CALENDAR").get(null);
				Object event = calendar.getClass()
					.getMethod("getEditEvent", new Class[]{String.class, String.class})
					.invoke(calendar, new Object[]{eventId, eventRemove});
				
				// commit event
				calendar.getClass()
					.getMethod("removeEvent", new Class[]{Class.forName("org.sakaiproject.calendar.api.CalendarEventEdit")})
					.invoke(calendar, new Object[]{event});	
				
				meeting.getProps().setCalendarEventId(null);
			}
			catch(Exception e)
			{
				logger.warn("Unable to remove event from Calendar (no permissions or site has no Calendar tool).", e);
				return false;
			}
		}
		return true;
	}
	
	private void logEvent(String event, BBBMeeting meeting)
	{
		eventTrackingService.post(
				eventTrackingService.newEvent(
						event,
						meeting.getId(),
						meeting.getSiteId(),
						true,
						NotificationService.NOTI_OPTIONAL
						)
		);
	}
	
	private Participant getParticipantFromMeeting(BBBMeeting meeting, String userId)
	{
		// 1. we want to first check individual user selection as it may override all/group/role selection...
		List<Participant> unprocessed1 = new ArrayList<Participant>();
		for(Participant p : meeting.getParticipants())
		{
			if(Participant.SELECTION_USER.equals(p.getSelectionType()))
			{
				if(userId.equals(p.getSelectionId()))
					return p;
			}
			else
			{
				unprocessed1.add(p);
			}
		}
		
		// 2. ... then with group/role selection types...
		String userRole = getUserRoleInSite(userId, meeting.getSiteId());
		List<String> userGroups = getUserGroupIdsInSite(userId, meeting.getSiteId());
		List<Participant> unprocessed2 = new ArrayList<Participant>();
		for(Participant p : unprocessed1)
		{
			if(Participant.SELECTION_ROLE.equals(p.getSelectionType()))
			{
				if(userRole != null && userRole.equals(p.getSelectionId()))
					return p;
			}
			else if(Participant.SELECTION_GROUP.equals(p.getSelectionType()))
			{
				if(userGroups.contains(p.getSelectionId()))
					return p;
			}
			else
			{
				unprocessed2.add(p);
			}
		}
		
		// 3. ... then go with 'all' selection type
		for(Participant p : unprocessed2)
		{
			if(Participant.SELECTION_ALL.equals(p.getSelectionType()))
				return p;
		}
		
		// 4. If not found, just check if is superuser
		if(securityService.isSuperUser()) {
			return new Participant(Participant.SELECTION_USER, "admin", Participant.MODERATOR);
		}
		
		
		return null;
	}
	
	private Date convertDateFromUserTimezone(Date date)
	{
		if(date ==  null) return null;
		
		Preferences prefs = preferencesService.getPreferences(userDirectoryService.getCurrentUser().getId());
		TimeZone timeZone = null;
		if(prefs != null) {
			ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
			String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
			timeZone = timeZoneStr != null ? TimeZone.getTimeZone(timeZoneStr) : TimeZone.getDefault();
		}else{
			timeZone = TimeZone.getDefault();
		}
		long timeMs = date.getTime();
		Date tzDate = new Date(timeMs - timeZone.getOffset(timeMs));

		return tzDate;
	}
	
	private Date convertDateToUserTimezone(Date date)
	{
		if(date ==  null) return null;
		
		Preferences prefs = preferencesService.getPreferences(userDirectoryService.getCurrentUser().getId());
		TimeZone timeZone = null;
		if(prefs != null) {
			ResourceProperties props = prefs.getProperties(TimeService.APPLICATION_ID);
			String timeZoneStr = props.getProperty(TimeService.TIMEZONE_KEY);
			timeZone = timeZoneStr != null ? TimeZone.getTimeZone(timeZoneStr) : TimeZone.getDefault();
		}else{
			timeZone = TimeZone.getDefault();
		}
		long timeMs = date.getTime();
		Date tzDate = new Date(timeMs + timeZone.getOffset(timeMs));

		return tzDate;
	}
	
	private Date convertDateToTimezone(Date date, TimeZone timeZone)
	{
		if(date ==  null) return null;
		if(timeZone == null) timeZone = TimeZone.getDefault();
		long timeMs = date.getTime();
		Date tzDate = new Date(timeMs + timeZone.getOffset(timeMs));

		return tzDate;
	}
	
	private boolean isUserAllowedInLocation(String userId, String permission, String locationId)
	{
		if(securityService.isSuperUser()) {
			return true;
		}
		if(locationId != null && !locationId.startsWith(SiteService.REFERENCE_ROOT)) {
			locationId = SiteService.REFERENCE_ROOT + Entity.SEPARATOR + locationId;
		}
		if(securityService.unlock(userId, permission, locationId)) {
			return true;
		}
		return false;
	}
	
	private String getUserRoleInSite(String userId, String siteId)
	{
		if(siteId != null) {
			try{
				Site site = siteService.getSite(siteId);
				if(!securityService.isSuperUser()) {
					return site.getUserRole(userId).getId();
				}
			}catch(IdUnusedException e){
				logger.error("No such site while resolving user role in site: "+siteId);
			}catch(Exception e){
				logger.error("Unknown error while resolving user role in site: "+siteId);
			}
		}
		return null;
	}
	
	private List<String> getUserGroupIdsInSite(String userId, String siteId)
	{
		List<String> groupIds = new ArrayList<String>();
		if(siteId != null) {
			try{
				Site site = siteService.getSite(siteId);
				Collection<Group> userGroups = site.getGroupsWithMember(userId);
				for(Group g : userGroups)
					groupIds.add(g.getId());
			}catch(IdUnusedException e){
				logger.error("No such site while resolving user role in site: "+siteId);
			}catch(Exception e){
				logger.error("Unknown error while resolving user role in site: "+siteId);
			}
		}
		return groupIds;
	}
	
	/** Generate an iCal file in tmp dir, an return the file path on the filesystem */
	private String generateIcalFromMeeting(BBBMeeting meeting)
	{
		TimeZone defaultTimezone = TimeZone.getDefault();
		Date startDate = convertDateToTimezone(meeting.getStartDate(), defaultTimezone);
		if(startDate == null) return null;
		Date endDate = convertDateToTimezone(meeting.getEndDate(), defaultTimezone);
		
		// Create a TimeZone
		TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
		net.fortuna.ical4j.model.TimeZone timezone = registry.getTimeZone(defaultTimezone.getID());
		VTimeZone tz = timezone.getVTimeZone();
		
		// Create the event
		String eventName = meeting.getName();
		VEvent vEvent = null;
		if(endDate != null) {
			DateTime start = new DateTime(startDate.getTime());
			DateTime end = new DateTime(endDate.getTime());
			vEvent = new VEvent(start, end, eventName);
			
		}else{
			DateTime start = new DateTime(startDate.getTime());
			vEvent = new VEvent(start, eventName);
		}
		
		// add description & url
		String meetingUrl = getDirectToolJoinUrl(meeting);
		try{
			vEvent.getProperties().add(new Description( meeting.getProps().getWelcomeMessage() ));
		}catch(Exception e1){
			// ignore - no harm
		}
		try{
			vEvent.getProperties().add(new Url(new URI(meetingUrl)));
		}catch(Exception e1){
			// ignore - no harm
		}
		
		// add timezone info..
		vEvent.getProperties().add(tz.getTimeZoneId());

		// generate unique identifier..
		try{
			UidGenerator ug = new UidGenerator("uidGen");
			Uid uid = ug.generateUid();
			vEvent.getProperties().add(uid);
		}catch(SocketException e){
			logger.warn("Unable to generate iCal Event UID.", e);
		}
		
		// create a calendar
		net.fortuna.ical4j.model.Calendar icsCalendar = new net.fortuna.ical4j.model.Calendar();
		icsCalendar.getProperties().add(new ProdId("-//Sakai Calendar//iCal4j 1.0//EN"));
		icsCalendar.getProperties().add(Version.VERSION_2_0);
		icsCalendar.getProperties().add(CalScale.GREGORIAN);

		// add the event
		icsCalendar.getComponents().add(vEvent);
		
		// log ical, if debug
		if(logger.isDebugEnabled()) logger.debug(icsCalendar.toString());
		
		// output to temp file
		String filename = System.getProperty("java.io.tmpdir") + File.separatorChar + meeting.getId()+".ics";
		try{
			FileOutputStream fout = new FileOutputStream(filename);
			CalendarOutputter outputter = new CalendarOutputter();
			outputter.output(icsCalendar, fout);
		}catch(Exception e){
			logger.warn("Unable to write iCal to file: "+filename, e);
		}
		
		return filename;
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
			if(!"admin".equals(currentUserId)) {
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
				
			}else{
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
