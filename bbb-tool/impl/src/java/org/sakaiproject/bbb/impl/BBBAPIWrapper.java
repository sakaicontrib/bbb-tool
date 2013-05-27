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

    /** A mapping of BBB proxies onto their BBB server urls */
    private Map<String, BBBAPI> bbbProxyMap = new HashMap<String, BBBAPI>();

    private String[] bbbUrls = {};
    private String[] bbbSalts = {};
    private List<String> liveUrls = null;

    private Timer allocatorTimer = null;

    // -----------------------------------------------------------------------
    // --- Initialization related methods ------------------------------------
    // -----------------------------------------------------------------------
    public void start() {
        if (logger.isDebugEnabled()) logger.debug("init()");

        String bbbUrlString = config.getString(BBBMeetingManager.CFG_URL, DEFAULT_BBB_URL);
        if (bbbUrlString == ""){
            logger.warn("No BigBlueButton servers specified. The bbb.url property in sakai.properties must be either set to a single url or a comma separated list of urls. There should be a corresponding list of salts in the bbb.salt property.");
            return;
        }

        String bbbSaltString = config.getString(BBBMeetingManager.CFG_SALT, DEFAULT_BBB_SALT);
        if (bbbSaltString == ""){
            logger.warn("BigBlueButton salt key was not specified! Use 'bbb.salt = your_bbb_key' in sakai.properties.");
            return;
        }

        bbbUrls = bbbUrlString.split(",");
        bbbSalts = bbbSaltString.split(",");
        if (bbbUrls.length != bbbSalts.length){
            logger.warn("The number of BigBlueButton salts does not match the number of BigBlueButton urls! Check your bbb.salt and bbb.url properties in sakai.properties.");
            return;
        }

        //Clean Urls
        for (int i = 0; i < bbbUrls.length; i++){
            if( bbbUrls[i].substring(bbbUrls[i].length()-1, bbbUrls[i].length()).equals("/") )
                bbbUrls[i] = bbbUrls[i].substring(0, bbbUrls[i].length()-1);
        }
        
        liveUrls = new ArrayList<String>(bbbUrls.length);
        if( doLoadBBBProxyMap() ) {
        	if (bbbUrls.length > 0) {
        		api = bbbProxyMap.get(bbbUrls[0]);
        	}
        }


        // let's make sure that our meetings are all set up at least with a configured hosts
        List<BBBMeeting> meetings = storageManager.getAllMeetings();
        for (BBBMeeting meeting : meetings) {
            String hostUrl = meeting.getHostUrl();
            if (!bbbProxyMap.containsKey(hostUrl)) {
                // The host for this meeting is not alive. Try and move the meeting.
                logger.warn("'" + hostUrl + "', the host of meeting '" + meeting.getId()
                          + "', was not available. The meeting will be moved to the first available host ...");
            	
                //Assign the first configured URL
            	storageManager.setMeetingHost(meeting.getId(), bbbUrls[0]);
            		
            }
        }

        bbbAutorefreshMeetings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHMEETINGS, (int) bbbAutorefreshMeetings);
        bbbAutorefreshRecordings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHRECORDINGS, (int) bbbAutorefreshRecordings);
        bbbGetSiteRecordings = (boolean) config.getBoolean(BBBMeetingManager.CFG_GETSITERECORDINGS, bbbGetSiteRecordings);
        bbbRecording = (boolean) config.getBoolean(BBBMeetingManager.CFG_RECORDING, bbbRecording);
        bbbDescriptionMaxLength = (int) config.getInt(BBBMeetingManager.CFG_DESCRIPTIONMAXLENGTH, bbbDescriptionMaxLength);

    }

    public void destroy() {
        /*
         * if(bbbVersionCheckThreadEnabled) { stopBBBVersionCheckThread(); }
         */
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
        
        if( bbbProxyMap.size() == 0 && !doLoadBBBProxyMap() ) 
            throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, "No BigBlueButton server has been properly initialized" );
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);

        if (hostProxy == null)	
            return false;
        else
            return hostProxy.isMeetingRunning(meetingID);
    }

    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        //if (logger.isDebugEnabled()) logger.debug("getMeetingInfo()");

        String hostUrl = storageManager.getMeetingHost(meetingID);
        Map<String, Object> meetingInfoResponse = new HashMap<String, Object>();
        
        try{
            if( bbbProxyMap.size() == 0 && !doLoadBBBProxyMap() ) 
                throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, "No BigBlueButton server has been properly initialized" );
            BBBAPI hostProxy = bbbProxyMap.get(hostUrl);

            meetingInfoResponse = hostProxy.getMeetingInfo(meetingID, password); 
            
        } catch ( BBBException e){
        	if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) || BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ){
        	    doRestartBBBProxyMap();
        	    meetingInfoResponse = responseError(e.getMessageKey(), e.getMessage() );
        	}
        } catch ( Exception e){
            doRestartBBBProxyMap();
            meetingInfoResponse = responseError(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage() );
        }

        return meetingInfoResponse;

    }

    public String getJoinMeetingURL(String meetingID, User user, String password)
            throws BBBException {
        //if (logger.isDebugEnabled()) logger.debug("getJoinMeetingURL()");

        String hostUrl = storageManager.getMeetingHost(meetingID);
        String joinMeetingURLResponse = "";

        try{
            if( bbbProxyMap.size() == 0 && !doLoadBBBProxyMap() ) 
                throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, "No BigBlueButton server has been properly initialized" );
            BBBAPI hostProxy = bbbProxyMap.get(hostUrl);

            joinMeetingURLResponse = hostProxy.getJoinMeetingURL(meetingID, user, password); 
            
        } catch ( BBBException e){
            if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) || BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ){
                doRestartBBBProxyMap();
            }
        } catch ( Exception e){
            doRestartBBBProxyMap();
        }

        return joinMeetingURLResponse;

    }
    
    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        //if (logger.isDebugEnabled()) logger.debug("getRecordings()");
        
        String hostUrl = storageManager.getMeetingHost(meetingID);
        Map<String, Object> recordingsResponse = new HashMap<String, Object>();
        
        try{
            if( bbbProxyMap.size() == 0 && !doLoadBBBProxyMap() ) 
                throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, "No BigBlueButton server has been properly initialized" );
            BBBAPI hostProxy = bbbProxyMap.get(hostUrl);

            recordingsResponse = hostProxy.getRecordings(meetingID); 
            
        } catch ( BBBException e){
            if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) || BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) ){
                doRestartBBBProxyMap();
            }
            recordingsResponse = responseError(e.getMessageKey(), e.getMessage() );
            logger.debug("getRecordings.BBBException: message=" + e.getMessage());
        } catch ( Exception e){
        	recordingsResponse = responseError(BBBException.MESSAGEKEY_GENERALERROR, e.getMessage() );
            logger.debug("getRecordings.Exception: message=" + e.getMessage());
        }

        return recordingsResponse;
    }

    public Map<String, Object> getSiteRecordings(String meetingIDs)
            throws BBBException {

        if (logger.isDebugEnabled()) logger.debug("getSiteRecordings(): for meetingIDs=" + meetingIDs);

        String hostUrl = this.bbbUrls[0];
        logger.debug("getSiteRecordings(): hostUrl=[" + hostUrl + "]");
    	Map<String, Object> siteRecordingsResponse = new HashMap<String, Object>();

        try{
            if( bbbProxyMap.size() == 0 && !doLoadBBBProxyMap() ) 
                throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, "No BigBlueButton server has been properly initialized" );
            BBBAPI hostProxy = bbbProxyMap.get(hostUrl);

            if (hostProxy == null) {
            	siteRecordingsResponse = responseError("noProxyFound", "No proxy found for host '" + hostUrl + "'. Returning [FAILED] for the getSiteRecordings.");
            } else {
                if( meetingIDs != null && !meetingIDs.trim().equals("") ) {
                	if( bbbGetSiteRecordings ){
                		logger.debug("getting site recordings all in one call for [" + meetingIDs + "]");
                		siteRecordingsResponse = hostProxy.getRecordings(meetingIDs);
                	} else {
                		logger.debug("getting site recordings one by one");
                	    String[] MeetingIdArray = {};
                	    MeetingIdArray = meetingIDs.split(",");
                        if (MeetingIdArray.length >= 0 ){
                        	for(int i=0; i < MeetingIdArray.length; i++){
                        		siteRecordingsResponse = getMergedMap(siteRecordingsResponse, hostProxy.getRecordings(MeetingIdArray[i]));
                        	}
                        }
                	}
                } else {
                	//siteRecordingsResponse = responseError(BBBException.MESSAGEKEY_NOTFOUND, "No recordings were found for this Site" );
                	siteRecordingsResponse.put("recordings", new Object());
                	siteRecordingsResponse.put("returncode", "SUCCESS");
                }
                	
            }
            
            
        } catch ( BBBException e){
            if( BBBException.MESSAGEKEY_UNREACHABLE.equals(e.getMessageKey()) || BBBException.MESSAGEKEY_HTTPERROR.equals(e.getMessageKey()) )
                doRestartBBBProxyMap();
            siteRecordingsResponse = responseError(e.getMessageKey(), e.getMessage() );
            logger.debug("getRecordings.BBBException: message=" + e.getMessage());
        } catch ( Exception e){
        	siteRecordingsResponse = responseError(BBBException.MESSAGEKEY_GENERALERROR, e.getMessage() );
            logger.debug("getRecordings.Exception: message=" + e.getMessage());
        }

        return siteRecordingsResponse;

    }
    
    private Map<String, Object> getMergedMap(Map<String, Object> target, Map<String, Object> source) {

    	Map<String, Object> responseMap = new HashMap<String, Object>();
    	
    	if( source.containsValue("noRecordings") ){
        	if( !target.containsKey("returncode") ){
        		responseMap = source;
        	} else {
        		responseMap = target;
        	}
    	} else {
        	if( !target.containsKey("returncode") ){
        		responseMap = source;
        	} else if( target.containsValue("noRecordings") ){
        		target.remove("messageKey");
        		target.remove("message");
        		target.remove("recordings");
        		target.put("recordings", source.get("recordings"));
        		responseMap = target;
        	} else {
            	ArrayList<Object> targetRecordingList = (ArrayList<Object>) target.get("recordings");
            	ArrayList<Object> sourceRecordingList = (ArrayList<Object>) source.get("recordings");

            	Object[] elements = sourceRecordingList.toArray();
                for(int i=0; i < elements.length ; i++)        
            		targetRecordingList.add(elements[i]);

                target.remove("recordings");
        		target.put("recordings", targetRecordingList);

        		responseMap = target;
        	}
    	}
    	
    	return responseMap;
    	
    }
    
    public Map<String, Object> getAllRecordings()
    		throws BBBException {

        String hostUrl = this.bbbUrls[0];
        
    	BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        if (hostProxy == null && !doLoadBBBProxyMap() )
            return responseError("noProxyFound", "No proxy found for host '" + hostUrl + ". Returning [FAILED] for the getAllRecordings.");

    	return hostProxy.getRecordings("");
    }

    public boolean endMeeting(String meetingID, String password)
            throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        if (hostProxy == null && !doLoadBBBProxyMap() ) return false;
        
        return hostProxy.endMeeting(meetingID, password);
    }

    public boolean publishRecordings(String meetingID, String recordingID, String publish) 
    		throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        if (hostProxy == null && !doLoadBBBProxyMap() ) return false;

        return hostProxy.publishRecordings(meetingID, recordingID, publish);
    }

    public boolean deleteRecordings(String meetingID, String recordingID)
            throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);

        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        if (hostProxy == null && !doLoadBBBProxyMap() ) return false;
        
        return hostProxy.deleteRecordings(meetingID, recordingID);
    }

    public void makeSureMeetingExists(BBBMeeting meeting) 
    		throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meeting.getId());

        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        if (hostProxy == null && !doLoadBBBProxyMap() ) return;
        
        hostProxy.makeSureMeetingExists(meeting);
    }

    // -----------------------------------------------------------------------
    // --- BBB API Version Check thread related methods ----------------------
    // -----------------------------------------------------------------------
    /** BBB Version Check thread */
    /*
     * public void run(){ try{
     * logger.debug("Started BBB API Version Check thread");
     * while(bbbVersionCheckThreadRunning){
     * 
     * for(BBBAPI bbbApi : bbbProxyMap.values()) { // do the check
     * bindAPIClassToBBBVersion(bbbApi); }
     * 
     * // sleep if no work to do if(!bbbVersionCheckThreadRunning) break; try{
     * synchronized (bbbVersionCheckThreadSemaphore){
     * bbbVersionCheckThreadSemaphore.wait(bbbVersionCheckInterval); }
     * }catch(InterruptedException e){
     * logger.warn("Failed to sleep BBB API Version Check thread",e); } }
     * }catch(Throwable t){
     * logger.debug("Failed to executeBBB API Version Check thread",t);
     * }finally{ if(bbbVersionCheckThreadRunning){ // thread was stopped by an
     * unknown error: restartlogger.debug(
     * "BBB API Version Check thread was stoped by an unknown error: restarting..."
     * ); startBBBVersionCheckThread(); }else
     * logger.debug("Finished BBB API Version Check thread"); } }
     */

    /** Start the BBB Version Check thread */
    /*
     * private void startBBBVersionCheckThread(){ bbbVersionCheckThreadRunning =
     * true; bbbVersionCheckThread = null; bbbVersionCheckThread = new
     * Thread(this, this.getClass().getName()); bbbVersionCheckThread.start(); }
     */

    /** Stop the BBB Version Check thread */
    /*
     * private void stopBBBVersionCheckThread(){ bbbVersionCheckThreadRunning =
     * false; synchronized (bbbVersionCheckThreadSemaphore){
     * bbbVersionCheckThreadSemaphore.notifyAll(); } }
     */

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
                liveUrls.add(proxy.getUrl());
            } else {
                logger.debug("Error checking BigBlueButton version. Striking '" + proxy.getUrl() + " from the map ...");
                bbbProxyMap.remove(proxy.getUrl());
                liveUrls.remove(proxy.getUrl());
                return;
            }

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
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_MINIMUM, proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber < 0.70f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_063, proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber < 0.80f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_070, proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber == 0.80f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_080, proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber == 0.81f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_081, proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else {
                    BBBAPI newProxy = getAPI(defaultVersion, proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                }

            } catch (NumberFormatException e) {
                // invalid version => bind to latest
                logger.warn("Invalid BigBlueButton version found (" + version + ") => binding to " + defaultVersion, e);
                BBBAPI newProxy = getAPI(defaultVersion, proxy.getUrl(), proxy.getSalt());
                bbbProxyMap.put(proxy.getUrl(), newProxy);
            }
        } catch (Exception e) {
            logger.error("Unable to check BigBlueButton version ", e);
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

    private class AllocatorTimerTask extends TimerTask {

        private Random random = new Random();
        private int liveAtStart = 0;
        private int attempt = 1;

        public void run() {
            List<String> testUrls = new ArrayList<String>(liveUrls);
            liveAtStart = testUrls.size();
            attempt = 1;
            allocate(testUrls);
        }

        private void allocate(List<String> testUrls) {

            if (logger.isDebugEnabled()) logger.debug("Allocation attempt #" + attempt);

            String newUrl = testUrls.get(random.nextInt(testUrls.size()));

            if (logger.isDebugEnabled()) logger.debug("BBB allocator picked " + newUrl);

            BBBAPI bbbProxy = bbbProxyMap.get(newUrl);

            try {
                if (bbbProxy != null) {
                    bbbProxy.getMeetings();
                    synchronized (api) {
                        api = bbbProxy;
                    }
                } else {
                    logger.error("No proxy mapped onto '" + newUrl + "'");
                }
            } catch (Exception e) {
                logger.error("Failed to allocate a BigBlueButton instance. The getMeetings call failed against "
                             + bbbProxy.getUrl() + ". It will be removed from the testUrls list for this allocation run.", e);
                testUrls.remove(bbbProxy.getUrl());

                // Allow as many attempts as there are live urls
                if (attempt < liveAtStart) {
                    attempt++;
                    allocate(testUrls);
                }
            }
        }
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

    private boolean doLoadBBBProxyMap() {

        if (logger.isDebugEnabled()) logger.debug("determine API version running on BBB server...");
        
        try {
            for (int i = 0; i < bbbUrls.length; i++) {
                BaseBBBAPI baseBBBAPI = new BaseBBBAPI(bbbUrls[i], bbbSalts[i]);
                bindAPIClassToBBBVersion(baseBBBAPI);
            }
        } catch(Exception e) {
            doRestartBBBProxyMap();
        }

        if( bbbProxyMap.size() == 0 ){
            logger.debug("No BigBlueButton server has been properly initialized...");
            return false;
        } else
            return true;
    }

    private void doRestartBBBProxyMap(){
        bbbProxyMap = new HashMap<String, BBBAPI>();
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
