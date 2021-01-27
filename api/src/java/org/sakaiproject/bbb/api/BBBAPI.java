package org.sakaiproject.bbb.api;

import java.util.Map;

import org.sakaiproject.user.api.User;
import org.sakaiproject.bbb.api.storage.BBBMeeting;

public interface BBBAPI {

    String getAPIVersion();

    String getBaseUrl();

    String getSalt();

    BBBMeeting createMeeting(BBBMeeting meeting, boolean autoclose, boolean recordingenabled, boolean recordingreadynotification, boolean preuploadpresentation)
            throws BBBException;

    boolean isMeetingRunning(String meetingID) throws BBBException;

    /**
     * Get detailed live meeting information from BBB server
     **/
    Map<String, Object> getMeetingInfo(String meetingID, String password) throws BBBException;

    Map<String, Object> getRecordings(String meetingID) throws BBBException;

    boolean endMeeting(String meetingID, String password) throws BBBException;

    boolean deleteRecordings(String recordID) throws BBBException;

    boolean publishRecordings(String recordID, String publish) throws BBBException;

    boolean protectRecordings(String recordID, String protect) throws BBBException;

    String getJoinMeetingURL(String meetingID, String userId, String userDisplayName, String password);

    void makeSureMeetingExists(BBBMeeting meeting, boolean autoclose, boolean recordingenabled, boolean recordingreadynotification, boolean preuploadpresentation)
            throws BBBException;
}
