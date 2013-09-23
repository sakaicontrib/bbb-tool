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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.impl.bbbapi.BBBAPI;
import org.sakaiproject.bbb.impl.bbbapi.BaseBBBAPI;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.user.api.User;

/**
 * BBBAPIWrapper is the class responsible to interact with the BigBlueButton
 * API.
 * 
 * @author Nuno Fernandes
 */
public class BBBAPIWrapper/* implements Runnable */{
    protected final Logger logger = Logger.getLogger(getClass());

    /** BBB API Version (full) */
    private String version = null;
    /** BBB API Version number */
    private float versionNumber = 0;
    /** BBB API Snapshot Version ? */
    private boolean versionSnapshot = false;

    /** BBB API version check interval (default to 5 min) */
    private long bbbVersionCheckInterval = 0;

    /** BBB API auto refresh interval for meetings (default to 0 sec means it is not activated) */
    private long bbbAutorefreshMeetings = 0;
    /** BBB API auto refresh interval for recordings(default to 0 sec means it is not activated) */
    private long bbbAutorefreshRecordings = 0;
    /** BBB API getSiteRecordings active flag (default to true) */
    private boolean bbbGetSiteRecordings = true;
    /** BBB API recording flag to activate recording parameters in the client (default to true) */
    private boolean bbbRecording = true;
    /** BBB API maximum length allowed for meeting description (default 2083) */
    private int bbbDescriptionMaxLength = 2048;

    
    /** BBB API */
    private BBBAPI api = null;

    private static String DEFAULT_BBB_URL = "http://test-install.blindsidenetworks.com/bigbluebutton";
    private static String DEFAULT_BBB_SALT = "8cd8ef52e8e101574e400365b55e11a6";

    /** Sakai configuration service */
    protected ServerConfigurationService config = null;

    private BBBStorageManager storageManager = null;

    public void setStorageManager(BBBStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    // BBB API version check thread and semaphore
    private Thread bbbVersionCheckThread;
    private Object bbbVersionCheckThreadSemaphore = new Object();
    private boolean bbbVersionCheckThreadEnabled = false;
    private boolean bbbVersionCheckThreadRunning = false;

    private String bbbUrl;
    private String bbbSalt;

    // -----------------------------------------------------------------------
    // --- Initialization related methods ------------------------------------
    // -----------------------------------------------------------------------
    public void start() {
        if (logger.isDebugEnabled()) logger.debug("init()");

        String bbbUrlString = config.getString(BBBMeetingManager.CFG_URL, DEFAULT_BBB_URL);
        if (bbbUrlString == ""){
            logger.warn("No BigBlueButton server specified. The bbb.url property in sakai.properties must be set to a single url. There should be a corresponding shared secret value in the bbb.salt property.");
            return;
        }

        String bbbSaltString = config.getString(BBBMeetingManager.CFG_SALT, DEFAULT_BBB_SALT);
        if (bbbSaltString == ""){
            logger.warn("BigBlueButton shared secret was not specified! Use 'bbb.salt = your_bbb_shared_secret' in sakai.properties.");
            return;
        }

        //Clean Url
        bbbUrl = bbbUrlString.substring(bbbUrlString.length()-1, bbbUrlString.length()).equals("/")? bbbUrlString: bbbUrlString + "/";
        bbbSalt = bbbSaltString;
        
        //api will always have a value, except when the url and salt were not configured
        api = new BaseBBBAPI(bbbUrl, bbbSalt);

        bbbAutorefreshMeetings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHMEETINGS, (int) bbbAutorefreshMeetings);
        bbbAutorefreshRecordings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHRECORDINGS, (int) bbbAutorefreshRecordings);
        bbbGetSiteRecordings = (boolean) config.getBoolean(BBBMeetingManager.CFG_GETSITERECORDINGS, bbbGetSiteRecordings);
        bbbRecording = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDING, bbbRecording);
        bbbDescriptionMaxLength = (int) config.getInt(BBBMeetingManager.CFG_DESCRIPTIONMAXLENGTH, bbbDescriptionMaxLength);

    }

    public void destroy() {
    }

    public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        this.config = serverConfigurationService;
    }

    // -----------------------------------------------------------------------
    // --- BBB API wrapper methods -------------------------------------------
    // -----------------------------------------------------------------------
    public BBBMeeting createMeeting(BBBMeeting meeting) 
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("createMeeting()");
        
        // Synchronized to avoid clashes with the allocator task
        synchronized (api) {
            meeting.setHostUrl(api.getUrl());
            return api.createMeeting(meeting);
        }
    }

    public boolean isMeetingRunning(String meetingID) 
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("isMeetingRunning()");

        if ( api == null )
            return false;

        return api.isMeetingRunning(meetingID);
    }

    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getMeetingInfo()");

        Map<String, Object> meetingInfoResponse = new HashMap<String, Object>();

        if ( api != null  ) {
            try{
                meetingInfoResponse = api.getMeetingInfo(meetingID, password); 
            } catch ( BBBException e){
                if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) || BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ){
                    meetingInfoResponse = responseError(e.getMessageKey(), e.getMessage() );
                }
            } catch ( Exception e){
                meetingInfoResponse = responseError(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage() );
            }

        }

        return meetingInfoResponse;
    }

    public String getJoinMeetingURL(String meetingID, User user, String password)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getJoinMeetingURL()");

        String joinMeetingURLResponse = "";

        if ( api != null ) {
            joinMeetingURLResponse = api.getJoinMeetingURL(meetingID, user, password); 
        } else {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }

        return joinMeetingURLResponse;
    }
    
    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getRecordings()");

        Map<String, Object> recordingsResponse = new HashMap<String, Object>();
        
        if ( api != null ) {
            try{
                recordingsResponse = api.getRecordings(meetingID);
            } catch ( BBBException e){
                recordingsResponse = responseError(e.getMessageKey(), e.getMessage() );
                logger.debug("getRecordings.BBBException: message=" + e.getMessage());
            } catch ( Exception e){
                recordingsResponse = responseError(BBBException.MESSAGEKEY_GENERALERROR, e.getMessage() );
                logger.debug("getRecordings.Exception: message=" + e.getMessage());
            }
        } else {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }

        return recordingsResponse;
    }

    public Map<String, Object> getSiteRecordings(String meetingIDs)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getSiteRecordings(): for meetingIDs=" + meetingIDs);

        return getRecordings(meetingIDs);
    }
    
    public Map<String, Object> getAllRecordings()
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getAllRecordings()");

        return getRecordings("");
    }

    public boolean endMeeting(String meetingID, String password)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("endMeeting()");

        boolean endMeetingResponse = false;

        if ( api != null ) {
            endMeetingResponse = api.endMeeting(meetingID, password);
        } else {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        
        return endMeetingResponse;
    }

    public boolean publishRecordings(String meetingID, String recordingID, String publish) 
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("publishRecordings()");

        boolean publishRecordingsResponse = false;

        if ( api != null ) {
            publishRecordingsResponse = api.publishRecordings(meetingID, recordingID, publish);
        } else {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }

        return publishRecordingsResponse;
    }

    public boolean deleteRecordings(String meetingID, String recordingID)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("publishRecordings()");

        boolean deleteRecordingsResponse = false;

        if ( api != null ) {
            deleteRecordingsResponse = api.deleteRecordings(meetingID, recordingID);
        } else {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
        
        return deleteRecordingsResponse;
    }

    public void makeSureMeetingExists(BBBMeeting meeting) 
    		throws BBBException {
        if ( api != null ) {
            api.makeSureMeetingExists(meeting);
        } else {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, "Internal tool configuration error");
        }
    }


    // -----------------------------------------------------------------------
    // --- Utility methods ---------------------------------------------------
    // -----------------------------------------------------------------------
    public String getVersionString() {
        return version;
    }

    public float getVersionNumber() {
        return versionNumber;
    }

    public boolean isVersionSnapshot() {
        return versionSnapshot;
    }

    public long getAutorefreshForMeetings() {
        return bbbAutorefreshMeetings;
    }

    public long getAutorefreshForRecordings() {
        return bbbAutorefreshRecordings;
    }
    
    public boolean isRecordingEnabled(){
        return bbbRecording;
    }
    
    public int getMaxLengthForDescription(){
        return bbbDescriptionMaxLength;
    }

    private Map<String, Object> responseError(String messageKey, String message){
        logger.debug("responseError: " + messageKey + ":" + message);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("returncode", "FAILED");
        map.put("messageKey", messageKey);
        map.put("message", message);
        return map;
        
    }
    
}
