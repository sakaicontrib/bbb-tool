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
import org.sakaiproject.bbb.impl.bbbapi.BBBAPI_063;
import org.sakaiproject.bbb.impl.bbbapi.BBBAPI_070;
import org.sakaiproject.bbb.impl.bbbapi.BBBAPI_080;
import org.sakaiproject.bbb.impl.bbbapi.BBBAPI_081;
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

        if ( api == null && !doLoadBBBApi() )
            return false;

        return api.isMeetingRunning(meetingID);
    }

    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getMeetingInfo()");

        Map<String, Object> meetingInfoResponse = new HashMap<String, Object>();

        if ( api != null || doLoadBBBApi() ) {
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

        if ( api != null || doLoadBBBApi() ) {
            try{
                joinMeetingURLResponse = api.getJoinMeetingURL(meetingID, user, password); 
            } catch ( Exception e){
            }
        }

        return joinMeetingURLResponse;
    }
    
    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("getRecordings()");

        Map<String, Object> recordingsResponse = new HashMap<String, Object>();
        
        if ( api != null || doLoadBBBApi() ) {
            try{
                recordingsResponse = api.getRecordings(meetingID);
            } catch ( BBBException e){
                if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) || BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ){
                }
                recordingsResponse = responseError(e.getMessageKey(), e.getMessage() );
                logger.debug("getRecordings.BBBException: message=" + e.getMessage());
            } catch ( Exception e){
                recordingsResponse = responseError(BBBException.MESSAGEKEY_GENERALERROR, e.getMessage() );
                logger.debug("getRecordings.Exception: message=" + e.getMessage());
            }
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

        if ( api != null || doLoadBBBApi() ) {
            endMeetingResponse = api.endMeeting(meetingID, password);
        }
        
        return endMeetingResponse;
    }

    public boolean publishRecordings(String meetingID, String recordingID, String publish) 
    		throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("publishRecordings()");

        boolean publishRecordingsResponse = false;

        if ( api != null || doLoadBBBApi() ) {
            publishRecordingsResponse = api.publishRecordings(meetingID, recordingID, publish);
        }

        return publishRecordingsResponse;
    }

    public boolean deleteRecordings(String meetingID, String recordingID)
            throws BBBException {
        if (logger.isDebugEnabled()) logger.debug("publishRecordings()");

        boolean deleteRecordingsResponse = false;

        if ( api != null || doLoadBBBApi() ) {
            deleteRecordingsResponse = api.deleteRecordings(meetingID, recordingID);
        }
        
        return deleteRecordingsResponse;
    }

    public void makeSureMeetingExists(BBBMeeting meeting) 
    		throws BBBException {
        api.makeSureMeetingExists(meeting);
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

    protected void bindAPIClassToBBBVersion(final BBBAPI proxy) 
            throws BBBException {
        logger.debug("Checking BBB API version...");

        try {
            // get BBB API version
            String defaultVersion = BaseBBBAPI.APIVERSION_LATEST;
            String returnedVersion = proxy.getAPIVersion();

            // We have a live one, add it to the live list
            if (returnedVersion != null) {
                // convert to numeric & bind
                try {
                    String _version = returnedVersion;
                    // remove -SNAPSHOT, -FINAL, ...
                    if (_version.indexOf("-") != -1) {
                        String stringPart = _version.substring(_version.indexOf("-") + 1);
                        versionSnapshot = "SNAPSHOT".equalsIgnoreCase(stringPart.trim());
                        _version = _version.substring(0, _version.indexOf("-"));
                    }

                    // version should be like x.x or x.xx
                    float versionNumber = Float.parseFloat(_version);
                    if (versionNumber < 0.63f) {
                        api = getAPI(BaseBBBAPI.APIVERSION_MINIMUM, proxy.getUrl(), proxy.getSalt());
                    } else if (versionNumber < 0.70f) {
                        api = getAPI(BaseBBBAPI.APIVERSION_063, proxy.getUrl(), proxy.getSalt());
                    } else if (versionNumber < 0.80f) {
                        api = getAPI(BaseBBBAPI.APIVERSION_070, proxy.getUrl(), proxy.getSalt());
                    } else if (versionNumber == 0.80f) {
                        api = getAPI(BaseBBBAPI.APIVERSION_080, proxy.getUrl(), proxy.getSalt());
                    } else if (versionNumber == 0.81f) {
                        api = getAPI(BaseBBBAPI.APIVERSION_081, proxy.getUrl(), proxy.getSalt());
                    } else {
                        api = getAPI(defaultVersion, proxy.getUrl(), proxy.getSalt());
                    }

                } catch (NumberFormatException e) {
                    // invalid version => bind to latest
                    logger.warn("Invalid BigBlueButton version found (" + version + ") => binding to " + defaultVersion, e);
                    api = getAPI(defaultVersion, proxy.getUrl(), proxy.getSalt());
                }

            } else {
                logger.debug("Error checking BigBlueButton version. Striking '" + proxy.getUrl() + " from the map ...");
                api = null;
            }

        } catch (Exception e) {
            logger.error("Unable to check BigBlueButton version ", e);
            api = null;
        }
    }

    private BBBAPI getAPI(String _version, String url, String salt) {

        BBBAPI newProxy = null;
        // <= 0.64
        if (BaseBBBAPI.APIVERSION_063.equals(_version)) {
            newProxy = new BBBAPI_063(url, salt);

            // >= 0.70
        } else if (BaseBBBAPI.APIVERSION_070.equals(_version)) {
            newProxy = new BBBAPI_070(url, salt);

            // >= 0.80
        } else if (BaseBBBAPI.APIVERSION_080.equals(_version)) {
            newProxy = new BBBAPI_080(url, salt);
            
            // >= 0.81
        } else if (BaseBBBAPI.APIVERSION_081.equals(_version)) {
            newProxy = new BBBAPI_081(url, salt);
        }

        logger.debug("Sakai BigBlueButton Tool bound to API: " + newProxy.getClass().getSimpleName());

        return newProxy;
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

    private boolean doLoadBBBApi() {
        if (logger.isDebugEnabled()) logger.debug("determine API version running on BBB server...");
        
        try {
            BaseBBBAPI baseBBBAPI = new BaseBBBAPI(bbbUrl, bbbSalt);
            bindAPIClassToBBBVersion(baseBBBAPI);
        } catch(Exception e) {
            api = null;;
        }

        if( api == null ){
            logger.debug("The BigBlueButton server has not been properly initialized...");
            return false;
        } else
            return true;
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
