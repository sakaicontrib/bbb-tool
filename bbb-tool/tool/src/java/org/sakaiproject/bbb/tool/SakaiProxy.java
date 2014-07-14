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

package org.sakaiproject.bbb.tool;

import java.util.Locale;
import java.util.Map;

import org.apache.log4j.Logger;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;

/**
 * Proxy class for interacting with multiple Sakai APIs.
 * @author Adrian Fish
 */
class SakaiProxy
{
	private Logger 						logger = Logger.getLogger(SakaiProxy.class);
	private static SakaiProxy			sakaiProxy;

	private BBBMeetingManager			bbbMeetingManager;
	private UserDirectoryService 		userDirectoryService;
	private SiteService 				siteService;
	private ToolManager 				toolManager;
	private ServerConfigurationService	serverConfigurationService;
	
	// -----------------------------------------------------------------------
	// --- Spring related methods --------------------------------------------
	// -----------------------------------------------------------------------
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	public void setSiteService(SiteService siteService)
	{
		this.siteService = siteService;
	}

	public void setToolManager(ToolManager toolManager)
	{
		this.toolManager = toolManager;
	}

	public void setBBBMeetingManager(BBBMeetingManager bbbMeetingManager)
	{
		this.bbbMeetingManager = bbbMeetingManager;
	}

	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService)
	{
		this.serverConfigurationService = serverConfigurationService;
	}
	
	public void init()
	{
		SakaiProxy.sakaiProxy = this;
	}
	
	private SakaiProxy()
	{}
	
	public static SakaiProxy getInstance()
	{
		return sakaiProxy;
	}
	
	
	// -----------------------------------------------------------------------
	// --- Sakai related methods ---------------------------------------------
	// -----------------------------------------------------------------------
	public User getCurrentUser()
	{
		try
		{
			return userDirectoryService.getCurrentUser();
		}
		catch(Throwable t)
		{
			logger.error("Exception caught whilst getting current user.",t);
			if(logger.isDebugEnabled()) logger.debug("Returning null ...");
			return null;
		}
	}

	public String getCurrentSiteId()
	{
		Placement placement = toolManager.getCurrentPlacement();
		if(placement == null)
		{
			logger.warn("Current tool placement is null.");
			return null;
		}
		
		return placement.getContext();
	}

	public String getCurrentToolId()
	{
		Placement placement = toolManager.getCurrentPlacement();
		if(placement == null)
		{
			logger.warn("Current tool placement is null.");
			return null;
		}
		
		return placement.getId();
	}

	public String getUserLanguageCode()
	{
	    Locale locale = (new ResourceLoader()).getLocale();
	    String languageCode = (locale.toString() == null || "".equals(locale.toString()))? "en": locale.toString();
	    return languageCode;
	}

    private static final String SAKAI_VERSION_25 = "2.5";
    private static final String SAKAI_VERSION_26 = "2.6";
    private static final String SAKAI_VERSION_27 = "2.7";
    private static final String SAKAI_VERSION_28 = "2.8";
    private static final String SAKAI_VERSION_29 = "2.9";
    private static final String SAKAI_VERSION_MIN = "SAKAI_VERSION_25";

    public String getSakaiVersion()
    {
        String sakaiVersion = serverConfigurationService.getString("version.sakai");
        return sakaiVersion;
    }

	public String getSakaiSkin()
	{
		String skin = serverConfigurationService.getString("skin.default");
		String siteSkin = siteService.getSiteSkin(getCurrentSiteId());
		return siteSkin != null ? siteSkin : (skin != null ? skin : "default");
	}

	public long getServerTimeInUserTimezone()
	{
	    Map<String, Object> serverTimeInUserTimezone = bbbMeetingManager.getServerTimeInUserTimezone();
		return Long.parseLong( (String) serverTimeInUserTimezone.get("timestamp"));
	}
	
    public long getUserTimezoneOffset()
    {
        Map<String, Object> serverTimeInUserTimezone = bbbMeetingManager.getServerTimeInUserTimezone();
        return Long.parseLong( (String) serverTimeInUserTimezone.get("timezoneOffset"));
    }

    public String getUserTimezone()
    {
        Map<String, Object> serverTimeInUserTimezone = bbbMeetingManager.getServerTimeInUserTimezone();
        return (String) serverTimeInUserTimezone.get("timezone");
    }
    
    public void checkPermissions()
	{
		bbbMeetingManager.checkPermissions(getCurrentSiteId());
	}
	
}
