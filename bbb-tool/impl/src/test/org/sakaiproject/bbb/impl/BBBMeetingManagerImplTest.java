package org.sakaiproject.bbb.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.log4j.lf5.util.StreamUtils;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;

public class BBBMeetingManagerImplTest extends BBBMeetingManagerImpl {
	TimeZone DEFAULT_ZONE = TimeZone.getDefault();

	static final boolean DEBUG = false;

	static final boolean INTENSIVE = false;

	CalendarBuilder builder = new CalendarBuilder();

	@Test
	public void test() throws Exception {
		// Create a new meeting with fixed times
		BBBMeeting meeting = Mockito.mock(BBBMeeting.class);

		Date startDate = new Date(1443798000000L); //  2015-10-02 18:00:00 CET
		Date endDate = new Date(1443801600000L); //  2015-10-02 18:00:00 CET

		// Mock everything to make the code run...
		Mockito.when(meeting.getStartDate()).thenReturn(startDate);
		Mockito.when(meeting.getEndDate()).thenReturn(endDate);
		Mockito.when(meeting.getName()).thenReturn("test");
		SiteService siteService = Mockito.mock(SiteService.class);
		Site site = Mockito.mock(Site.class);
		Mockito.when(site.getId()).thenReturn(UUID.randomUUID().toString());
		ToolConfiguration toolConfiguration = Mockito.mock(ToolConfiguration.class);
		Mockito.when(toolConfiguration.getId()).thenReturn(UUID.randomUUID().toString());
		Mockito.when(site.getToolForCommonId(Mockito.anyString())).thenReturn(toolConfiguration);
		Mockito.when(siteService.getSite(Mockito.anyString())).thenReturn(site);
		this.setSiteService(siteService);
		ServerConfigurationService serverConfigurationService = Mockito.mock(ServerConfigurationService.class);
		Mockito.when(serverConfigurationService.getServerUrl()).thenReturn("http://sakai.edia.nl/");
		this.setServerConfigurationService(serverConfigurationService);
		testForTimezone(meeting, startDate, endDate, DEFAULT_ZONE);
		// Let's take it somewhere nice.
		testForTimezone(meeting, startDate, endDate, TimeZone.getTimeZone("Brazil/East"));
		if (INTENSIVE) { 
			// Get all Timezones
			String[] availableIDs = TimeZone.getAvailableIDs();
			// Get the defined zoend
			TimeZoneRegistry createRegistry = TimeZoneRegistryFactory.getInstance().createRegistry();
			for (String tzId : availableIDs) {
				if (createRegistry.getTimeZone(tzId) != null) {
					// Run only if ical4j knows the zone.
					testForTimezone(meeting, startDate, endDate, TimeZone.getTimeZone(tzId));
				}
			}
		}

	}

	private void testForTimezone(BBBMeeting meeting, Date startDate, Date endDate, TimeZone timezone)
	        throws FileNotFoundException, IOException, ParserException {
		System.out.println("");
		System.out.println(timezone.getDisplayName() + "(" + timezone.getID() + ")");
		String fileName = generateIcalFromMeetingInTimeZone(meeting, 10L, timezone);

		// Now move to the timezone, this is crucial to test this.
		TimeZone.setDefault(timezone);

		FileInputStream fin = new FileInputStream(fileName);

		net.fortuna.ical4j.model.Calendar build = builder.build(fin);
		fin.close();

		ComponentList components = build.getComponents();

		VEvent event = (VEvent) components.get(0);
		org.junit.Assert.assertEquals(startDate, event.getStartDate().getDate());
		org.junit.Assert.assertEquals(endDate, event.getEndDate().getDate());

		if (DEBUG) {
			fin = new FileInputStream(fileName);
			StreamUtils.copy(fin, System.out);
		}
	}

}
