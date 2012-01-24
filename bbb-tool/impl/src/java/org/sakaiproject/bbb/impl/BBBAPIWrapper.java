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

    /** BBB API auto refresh interval for meetings (default to 30 sec) */
    private long bbbAutorefreshMeetings = 30000L;
    /** BBB API auto refresh interval for recordings(default to 60 sec) */
    private long bbbAutorefreshRecordings = 60000L;

    /** BBB API */
    private BBBAPI api = null;

    //private static String DEFAULT_BBB_URL = "http://127.0.0.1/bigbluebutton";
    //private static String DEFAULT_BBB_SALT = "";
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
        if (logger.isDebugEnabled())
            logger.debug("init()");

        String bbbUrlString = config.getString(BBBMeetingManager.CFG_URL, DEFAULT_BBB_URL);
        bbbUrls = bbbUrlString.split(",");

        String bbbSaltString = config.getString(BBBMeetingManager.CFG_SALT, DEFAULT_BBB_SALT);
        if (bbbSaltString == "")
            logger.warn("BigBlueButton salt key was not specified! Use 'bbb.salt = your_bbb_key' in sakai.properties.");
        bbbSalts = bbbSaltString.split(",");

        if (bbbUrls.length < 1)
            logger.warn("No BigBlueButton servers specified. The bbb.url property in sakai.properties must be either set to a single url or a comma separated list of urls. There should be a corresponding list of salts in the bbb.salt property.");

        if (bbbUrls.length != bbbSalts.length)
            logger.warn("The number of BigBlueButton salts does not match the number of BigBlueButton urls! Check your bbb.salt and bbb.url properties in sakai.properties.");

        liveUrls = new ArrayList<String>(bbbUrls.length);

        // determine API version running on BBB server...

        // Do an initial round of api mappings synchronously. This will break a
        // Sakai startup if it
        // fails, but at least you know it has broken.
        for (int i = 0; i < bbbUrls.length; i++) {
            bindAPIClassToBBBVersion(new BaseBBBAPI(bbbUrls[i], bbbSalts[i]));
        }

        if (liveUrls != null && liveUrls.size() > 0) {
            api = bbbProxyMap.get(liveUrls.get(0));
        }

        List<BBBMeeting> meetings = storageManager.getAllMeetings();

        // Let's make sure that our meetings are all running on accessible hosts
        for (BBBMeeting meeting : meetings) {
            String hostUrl = meeting.getHostUrl();
            if (!bbbProxyMap.containsKey(hostUrl)) {
                // The host for this meeting is not alive. Try and move the
                // meeting.
                logger.warn("'"
                            + hostUrl
                            + "', the host of meeting '"
                            + meeting.getId()
                            + "', was not available. The meeting will be moved to the first available host '"
                            + api.getUrl() + "' ...");
                try {
                    api.createMeeting(meeting);
                    if (!storageManager.setMeetingHost(meeting.getId(), api.getUrl())) {
                        logger.error("Failed to set the host to '"
                                + api.getUrl() + "' for meeting '"
                                + meeting.getId() + "'");
                    }
                } catch (BBBException e) {
                    logger.error("Failed to move meeting '" + meeting.getId()
                            + "' from '" + meeting.getHostUrl() + "' to '"
                            + api.getUrl() + "'", e);
                }
            }
        }

        if (bbbUrls.length > 1) {
            allocatorTimer = new Timer("BigBlueButton Allocator Timer");
            allocatorTimer.schedule(new AllocatorTimerTask(), 5000L, 30000L);
        }

        bbbAutorefreshMeetings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHMEETINGS, (int) bbbAutorefreshMeetings);
        bbbAutorefreshRecordings = (long) config.getInt(BBBMeetingManager.CFG_AUTOREFRESHRECORDINGS, (int) bbbAutorefreshRecordings);
        /*
         * bbbVersionCheckInterval = (long)
         * config.getInt(BBBMeetingManager.CFG_VERSIONCHECKINTERVAL, (int)
         * bbbVersionCheckInterval); bbbVersionCheckThreadEnabled =
         * bbbVersionCheckInterval > 0; if(bbbVersionCheckThreadEnabled) {
         * startBBBVersionCheckThread(); }
         */

    }

    public void destroy() {
        /*
         * if(bbbVersionCheckThreadEnabled) { stopBBBVersionCheckThread(); }
         */
    }

    public void setServerConfigurationService(
            ServerConfigurationService serverConfigurationService) {
        this.config = serverConfigurationService;
    }

    // -----------------------------------------------------------------------
    // --- BBB API wrapper methods -------------------------------------------
    // -----------------------------------------------------------------------
    public BBBMeeting createMeeting(BBBMeeting meeting) throws BBBException {
        // Synchronized to avoid clashes with the allocator task
        synchronized (api) {
            meeting.setHostUrl(api.getUrl());
            return api.createMeeting(meeting);
        }
    }

    public boolean isMeetingRunning(String meetingID) throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        return hostProxy.isMeetingRunning(meetingID);
    }

    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        return hostProxy.getMeetingInfo(meetingID, password);
    }

    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        return hostProxy.getRecordings(meetingID);
    }

    public Map<String, Object> getAllRecordings()
    		throws BBBException {
    	String hostUrl = this.bbbUrls[0];
    	BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
    	return hostProxy.getRecordings("");
    }

    public boolean endMeeting(String meetingID, String password)
            throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        return hostProxy.endMeeting(meetingID, password);
    }

    public boolean publishRecordings(String meetingID, String recordingID,
            String publish) throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        return hostProxy.publishRecordings(meetingID, recordingID, publish);
    }

    public boolean deleteRecordings(String meetingID, String recordingID)
            throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
        return hostProxy.deleteRecordings(meetingID, recordingID);
    }

    public String getJoinMeetingURL(String meetingID, User user, String password) {
        String hostUrl = storageManager.getMeetingHost(meetingID);
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);

        if (hostProxy == null) {
            logger.warn("No proxy found for host '" + hostUrl
                    + ". Returning \"\" for the join url ...");
            return "";
        }

        return hostProxy.getJoinMeetingURL(meetingID, user, password);
    }

    public void makeSureMeetingExists(BBBMeeting meeting) throws BBBException {
        String hostUrl = storageManager.getMeetingHost(meeting.getId());
        BBBAPI hostProxy = bbbProxyMap.get(hostUrl);
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

    protected void bindAPIClassToBBBVersion(final BBBAPI proxy) {
        logger.debug("Checking BBB API version...");
        try {
            // get BBB API version
            String returnedVersion = proxy.getAPIVersion();
            String defaultVersion = BaseBBBAPI.APIVERSION_LATEST;

            // We have a live one, add it to the live list
            if (returnedVersion != null) {
                liveUrls.add(proxy.getUrl());
            } else {
                logger.debug("Error checking BigBlueButton version. Striking '"
                        + proxy.getUrl() + " from the map ...");
                bbbProxyMap.remove(proxy.getUrl());
                liveUrls.remove(proxy.getUrl());
                return;
            }

            // convert to numeric & bind
            try {
                String _version = returnedVersion;
                // remove -SNAPSHOT, -FINAL, ...
                if (_version.indexOf("-") != -1) {
                    String stringPart = _version.substring(_version
                            .indexOf("-") + 1);
                    versionSnapshot = "SNAPSHOT".equalsIgnoreCase(stringPart
                            .trim());
                    _version = _version.substring(0, _version.indexOf("-"));
                }

                // logger.info("sdfgsdfg");
                // version should be like x.x or x.xx
                float versionNumber = Float.parseFloat(_version);
                if (versionNumber < 0.63f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_MINIMUM,
                            proxy.getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber < 0.70f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_063, proxy
                            .getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber < 0.80f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_070, proxy
                            .getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else if (versionNumber == 0.80f) {
                    BBBAPI newProxy = getAPI(BaseBBBAPI.APIVERSION_080, proxy
                            .getUrl(), proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                } else {
                    BBBAPI newProxy = getAPI(defaultVersion, proxy.getUrl(),
                            proxy.getSalt());
                    bbbProxyMap.put(proxy.getUrl(), newProxy);
                }

            } catch (NumberFormatException e) {
                // invalid version => bind to latest
                logger.warn("Invalid BigBlueButton version found (" + version
                        + ") => binding to " + defaultVersion, e);
                BBBAPI newProxy = getAPI(defaultVersion, proxy.getUrl(), proxy
                        .getSalt());
                bbbProxyMap.put(proxy.getUrl(), newProxy);
            }
        } catch (Exception e) {
            logger.error("Unable to check BigBlueButton version", e);
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
        }

        logger.info("Sakai BigBlueButton Tool bound to API: "
                + newProxy.getClass().getSimpleName());

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

            if (logger.isDebugEnabled())
                logger.debug("Allocation attempt #" + attempt);

            String newUrl = testUrls.get(random.nextInt(testUrls.size()));

            if (logger.isDebugEnabled())
                logger.debug("BBB allocator picked " + newUrl);

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
                logger
                        .error(
                                "Failed to allocate a BigBlueButton instance. The getMeetings call failed against "
                                        + bbbProxy.getUrl()
                                        + ". It will be removed from the testUrls list for this allocation run.",
                                e);

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

}
