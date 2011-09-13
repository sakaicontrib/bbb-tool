package org.sakaiproject.bbb.impl.bbbapi;

import java.util.Map;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.user.api.User;

public interface BBBAPI {

	public String getAPIVersion();

	public String getUrl();

	public String getSalt();

	public BBBMeeting createMeeting(BBBMeeting meeting) throws BBBException;

	public boolean isMeetingRunning(String meetingID) throws BBBException;

	public Map<String, Object> getMeetingInfo(String meetingID, String password) throws BBBException;

	public boolean endMeeting(String meetingID, String password) throws BBBException;

	public String getJoinMeetingURL(String meetingID, User user, String password);

	public void makeSureMeetingExists(BBBMeeting meeting) throws BBBException;
	
	public Map<String,Object> getMeetings() throws BBBException;

	public Map<String,Object> getRecordings() throws BBBException;

}
