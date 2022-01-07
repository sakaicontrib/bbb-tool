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

package org.sakaiproject.bbb.impl.bbbapi;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Set;
import java.util.Random;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;
import org.sakaiproject.bbb.api.BBBException;
import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.util.ResourceLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Base class for interacting with any BigBlueButton API version.
 *
 * @author Nuno Fernandes
 */
public class BaseBBBAPI implements BBBAPI {
    protected final Logger logger = Logger.getLogger(getClass());

    // Sakai BBB configuration
    /**
     * BBB server url, including bigbluebutton webapp path. Will default to
     * http://localhost/bigbluebutton if not specified
     */
    protected String bbbUrl = "http://127.0.0.1/bigbluebutton";
    /** BBB security salt */
    protected String bbbSalt = null;

    // API Server Path
    protected final static String API_SERVERPATH = "/api/";

    // API Calls
    protected final static String APICALL_CREATE = "create";
    protected final static String APICALL_ISMEETINGRUNNING = "isMeetingRunning";
    protected final static String APICALL_GETMEETINGINFO = "getMeetingInfo";
    protected final static String APICALL_GETMEETINGS = "getMeetings";
    protected final static String APICALL_JOIN = "join";
    protected final static String APICALL_END = "end";
    protected final static String APICALL_VERSION = "";
    protected final static String APICALL_GETRECORDINGS = "getRecordings";
    protected final static String APICALL_PUBLISHRECORDINGS = "publishRecordings";
    protected final static String APICALL_PROTECTRECORDINGS = "updateRecordings";
    protected final static String APICALL_DELETERECORDINGS = "deleteRecordings";

    // API Response Codes
    protected final static String APIRESPONSE_SUCCESS = "SUCCESS";
    protected final static String APIRESPONSE_FAILED = "FAILED";

    // API Versions
    public final static String APIVERSION_063 = "0.63";
    public final static String APIVERSION_064 = "0.64";
    public final static String APIVERSION_070 = "0.70";
    public final static String APIVERSION_080 = "0.80";
    public final static String APIVERSION_081 = "0.81";
    public final static String APIVERSION_MINIMUM = APIVERSION_063;
    public final static String APIVERSION_LATEST = APIVERSION_081;

    protected ServerConfigurationService config;

    private ContentHostingService m_contentHostingService = (ContentHostingService) ComponentManager.get("org.sakaiproject.content.api.ContentHostingService");

    private SecurityService m_securityService = (SecurityService) ComponentManager.get("org.sakaiproject.authz.api.SecurityService");

    protected Random randomGenerator = new Random(System.currentTimeMillis());

    // -----------------------------------------------------------------------
    // --- Initialization related methods ------------------------------------
    // -----------------------------------------------------------------------
    public BaseBBBAPI(String url, String salt) {
        this.bbbUrl = url;

        if (bbbUrl.endsWith("/") && bbbUrl.length() > 0) {
            bbbUrl = bbbUrl.substring(0, bbbUrl.length() - 1);
        }

        this.bbbSalt = salt;

        // read BBB settings from sakai.properties
        config = (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class);
    }

    public String getUrl() {
        return this.bbbUrl;
    }

    public String getSalt() {
        return this.bbbSalt;
    }

    // -----------------------------------------------------------------------
    // --- BBB API implementation methods ------------------------------------
    // -----------------------------------------------------------------------
    /** Create a meeting on BBB server */
    public BBBMeeting createMeeting(final BBBMeeting meeting, boolean autoclose, boolean recordingenabled,
            boolean recordingreadynotification, boolean preuploadpresentation) throws BBBException {

        try {
            // build query
            StringBuilder query = new StringBuilder();
            query.append("meetingID=");
            query.append(URLEncoder.encode(meeting.getId(), getParametersEncoding()));
            query.append("&name=");
            query.append(URLEncoder.encode(meeting.getName(), getParametersEncoding()));
            query.append("&voiceBridge=");
            query.append(meeting.getVoiceBridge());
            query.append("&attendeePW=");
            String attendeePW = meeting.getAttendeePassword();
            query.append(attendeePW);
            query.append("&moderatorPW=");
            String moderatorPW = meeting.getModeratorPassword();
            query.append(moderatorPW);
            if (autoclose) {
                query.append("&logoutURL=");
                StringBuilder logoutUrl = new StringBuilder(config.getServerUrl());
                logoutUrl.append(BBBMeetingManager.TOOL_WEBAPP);
                logoutUrl.append("/bbb-autoclose.html");
                query.append(URLEncoder.encode(logoutUrl.toString(), getParametersEncoding()));
            }

            // BSN: Parameters required for playback recording
            boolean recording = ( recordingenabled && meeting.getRecording() != null && meeting.getRecording().booleanValue() );
            query.append("&record=" + Boolean.toString(recording));
            query.append("&duration=");
            String duration = meeting.getRecordingDuration() != null? meeting.getRecordingDuration().toString(): "0";
            query.append(duration);

            // BSN: Parameters added for monitoring and recording search
            for(Entry<String, String> entry : meeting.getMeta().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                query.append("&meta_" + key + "=");
                query.append(URLEncoder.encode(value, getParametersEncoding()));
            }
            // BSN: Ends

            // Composed Welcome message
            ResourceLoader toolMessages = new ResourceLoader("ToolMessages");
            String welcomeMessage = toolMessages.getFormattedMessage("bbb_welcome_message_opening", new Object[] { "<b>%%CONFNAME%%</b>" } );
            String welcomeDescription = meeting.getProps().getWelcomeMessage();
            if ( !"<br />".equals(welcomeDescription) )
                welcomeMessage += "<br><br>" + welcomeDescription;

            welcomeMessage += "<br><br>" + toolMessages.getFormattedMessage("bbb_welcome_message_general_info", new Object[] {toolMessages.getString("bbb_welcome_message_external_link"), "%%DIALNUM%%", "%%CONFNUM%%"} );

            if (recording)
                welcomeMessage += "<br><br><b>" + toolMessages.getFormattedMessage("bbb_welcome_message_recording_warning", new Object[] {} ) + "</b>";
            if (duration.compareTo("0") > 0)
                welcomeMessage += "<br><br><b>" + toolMessages.getFormattedMessage("bbb_welcome_message_duration_warning", new Object[] { duration });

            if (recording && recordingreadynotification) {
                query.append("&meta_bn-recording-ready-url=");
                StringBuilder recordingReadyUrl = new StringBuilder(config.getServerUrl());
                recordingReadyUrl.append("/direct");
                recordingReadyUrl.append(BBBMeetingManager.TOOL_WEBAPP);
                recordingReadyUrl.append("/recordingReady");
                query.append(URLEncoder.encode(recordingReadyUrl.toString(), getParametersEncoding()));
            }

            query.append("&welcome=");
            query.append(URLEncoder.encode(welcomeMessage, getParametersEncoding()));
            query.append(getCheckSumParameterForQuery(APICALL_CREATE, query.toString()));

            SecurityAdvisor sa = editResourceSecurityAdvisor();
            // Preupload presentation.
            String presentationXML = "";
            String presentation = "";
            String presentationName = meeting.getPresentation();
            if (preuploadpresentation && presentationName != null && !presentationName.isEmpty()) {
                presentation = URLDecoder.decode(presentationName.substring(presentationName.indexOf("/attachment")), "UTF-8");
                m_securityService.pushAdvisor(sa);
                // Open access to resource used as preuploaded presentation.
                m_contentHostingService.setPubView(presentation, true);
                // Set XML body.
                StringBuilder presentationUrl = new StringBuilder(config.getServerUrl());
                presentationUrl.append(presentationName);
                presentationXML = "<?xml version='1.0' encoding='UTF-8'?><modules><module name=\"presentation\"><document url=\"" + presentationUrl + "\" /></module></modules>";
            }
            // Do API call.
            Map<String, Object> response = doAPICall(APICALL_CREATE, query.toString(), presentationXML);
            // Close access to resource used as preuploaded presentation.
            if (presentation != "") {
                m_contentHostingService.setPubView(presentation, false);
            }
        } catch (BBBException e) {
            throw e;
        } catch (UnsupportedEncodingException e) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage(), e);
        }
        return meeting;
    }

    private SecurityAdvisor editResourceSecurityAdvisor() {
        return (userId, function, reference) -> {
            return SecurityAdvisor.SecurityAdvice.ALLOWED;
        };
    }

    /** Check if meeting is running on BBB server. */
    public boolean isMeetingRunning(String meetingID)
            throws BBBException {
        try {
            StringBuilder query = new StringBuilder();
            query.append("meetingID=");
            query.append(URLEncoder.encode(meetingID, getParametersEncoding()));
            query.append(getCheckSumParameterForQuery(APICALL_ISMEETINGRUNNING, query.toString()));

            Map<String, Object> response = doAPICall(APICALL_ISMEETINGRUNNING, query.toString());
            return Boolean.parseBoolean((String) response.get("running"));
        } catch (Exception e) {
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage(), e);
        }
    }

    /** Get live meeting information from BBB server */
    public Map<String, Object> getMeetings()
            throws BBBException {
        try {
            StringBuilder query = new StringBuilder();
            query.append("random=xyz");
            query.append(getCheckSumParameterForQuery(APICALL_GETMEETINGS, query.toString()));

            Map<String, Object> response = doAPICall(APICALL_GETMEETINGS, query.toString());

            // nullify password fields
            //BBB-161
            Map<String, Object>  map;
            for(String key1 : response.keySet()){
                if("meetings".equals(key1)){
                    Object meetings = response.get("meetings");
                    if(meetings != ""){
                        List list =(List) meetings;
                        for(int i = 0; i < list.size(); i++ ){
                            map = (Map) list.get(i);
                            for(String key : map.keySet()) {
                                if ("attendeePW".equals(key) || "moderatorPW".equals(key)){
                                    map.put(key, null);
                                }
                            }
                        }
                    }
                }
            }


            return response;
        } catch (Exception e) {
            logger.debug("Exception: Message=" + e.getMessage() );
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage(), e);
        }
    }

    /** Get detailed live meeting information from BBB server */
    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        try {
            StringBuilder query = new StringBuilder();
            query.append("meetingID=");
            query.append(URLEncoder.encode(meetingID, getParametersEncoding()));
            query.append("&password=");
            query.append(password);
            query.append(getCheckSumParameterForQuery(APICALL_GETMEETINGINFO, query.toString()));
            Map<String, Object> response = doAPICall(APICALL_GETMEETINGINFO, query.toString());
            // nullify password fields
            for (String key : response.keySet()) {
                if ("attendeePW".equals(key) || "moderatorPW".equals(key))
                    response.put(key, null);
            }
            return response;
        } catch (BBBException e) {
            logger.debug("getMeetingInfo.Exception: MessageKey=" + e.getMessageKey() + ", Message=" + e.getMessage() );
            throw new BBBException(e.getMessageKey(), e.getMessage(), e);
        } catch (Exception e) {
            logger.debug("Exception: Message=" + e.getMessage() );
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage(), e);
        }
    }

    /** Get recordings from BBB server */
    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        try {
            // Paginate queries for fetching recordings.
            List<String> meetingIDs = Arrays.asList(meetingID.split("\\s*,\\s*"));
            int pages = meetingIDs.size() / 25 + 1;
            // Fetch recordings in pages.
            List<Object> recordings = new ArrayList<Object>();
            int fromIndex, toIndex;
            for (int page = 1; page <= pages; ++page) {
                fromIndex = (page - 1) * 25;
                toIndex = page * 25;
                if (toIndex > meetingIDs.size()) {
                    toIndex = meetingIDs.size();
                }
                List subMeetingIDs = meetingIDs.subList(fromIndex, toIndex);
                recordings.addAll(getRecordings(subMeetingIDs));
            }
            // Prepare and return response with recordings.
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("returncode", "SUCCESS");
            response.put("recordings", recordings);
            return response;
        } catch (BBBException e) {
            logger.debug("getRecordings.Exception: MessageKey=" + e.getMessageKey() + ", Message=" + e.getMessage() );
            throw new BBBException(e.getMessageKey(), e.getMessage(), e);
        }
    }

    /** Get recordings from BBB server */
    protected List<Object> getRecordings(List meetingIDs)
            throws BBBException {
    	try {
            if (!meetingIDs.isEmpty()) {
                String meetingID = String.join(",", meetingIDs);
                StringBuilder query = new StringBuilder();
                query.append("meetingID=");
                query.append(URLEncoder.encode(meetingID, getParametersEncoding()));
                query.append(getCheckSumParameterForQuery(APICALL_GETRECORDINGS, query.toString()));
                Map<String, Object> response = doAPICall(APICALL_GETRECORDINGS, query.toString());
                // Make sure that the date retrived is a unix timestamp.
                if (response.get("returncode").equals("SUCCESS") && response.get("messageKey") == null) {
                    for (Object recordingEntry : (List<Object>)response.get("recordings")) {
                        Map<String, String> items = (Map<String, String>)recordingEntry;
                        items.put("startTime", getDateAsStringTimestamp(items.get("startTime")) );
                        items.put("endTime", getDateAsStringTimestamp(items.get("endTime")) );
                    }
                    return (List<Object>)response.get("recordings");
                }
            }
        } catch (BBBException e) {
            logger.debug("getRecordings.Exception: MessageKey=" + e.getMessageKey() + ", Message=" + e.getMessage() );
            throw new BBBException(e.getMessageKey(), e.getMessage(), e);
        } catch (Exception e) {
            logger.debug("Exception: Message=" + e.getMessage() );
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage(), e);
        }
        return new ArrayList<Object>();
    }

    /** End/delete a meeting on BBB server */
    public boolean endMeeting(String meetingID, String password)
            throws BBBException {
        StringBuilder query = new StringBuilder();
        try {
            query.append("meetingID=");
            query.append(URLEncoder.encode(meetingID, getParametersEncoding()));
            query.append("&password=");
            query.append(password);
            query.append(getCheckSumParameterForQuery(APICALL_END, query.toString()));
            doAPICall(APICALL_END, query.toString());
        } catch (BBBException e) {
            if (BBBException.MESSAGEKEY_NOTFOUND.equals(e.getMessageKey())) {
                // we can safely ignore this one: the meeting is not running
                return true;
            }
            throw e;
        } catch (Exception e) {
            logger.debug("Exception: Message=" + e.getMessage() );
            throw new BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage(), e);
        }
        return true;
    }

    /** Delete a recording on BBB server */
    public boolean deleteRecordings(String meetingID, String recordID)
            throws BBBException {
        StringBuilder query = new StringBuilder();
        query.append("recordID=");
        query.append(recordID);
        query.append(getCheckSumParameterForQuery(APICALL_DELETERECORDINGS, query.toString()));

        try {
            doAPICall(APICALL_DELETERECORDINGS, query.toString());

        } catch (BBBException e) {
            throw e;
        }

        return true;
    }

    /** Publish/Unpublish a recording on BBB server */
    public boolean publishRecordings(String meetingID, String recordID, String publish)
            throws BBBException {
        StringBuilder query = new StringBuilder();
        query.append("recordID=");
        query.append(recordID);
        query.append("&publish=");
        query.append(publish);
        query.append(getCheckSumParameterForQuery(APICALL_PUBLISHRECORDINGS, query.toString()));

        try {
            doAPICall(APICALL_PUBLISHRECORDINGS, query.toString());

        } catch (BBBException e) {
            throw e;
        }

        return true;
    }

    /** Protect/Unprotect a recording on BBB server */
    public boolean protectRecordings(String meetingID, String recordID, String protect)
            throws BBBException {
        StringBuilder query = new StringBuilder();
        query.append("recordID=");
        query.append(recordID);
        query.append("&protect=");
        query.append(protect);
        query.append(getCheckSumParameterForQuery(APICALL_PROTECTRECORDINGS, query.toString()));

        try {
            doAPICall(APICALL_PROTECTRECORDINGS, query.toString());

        } catch (BBBException e) {
            throw e;
        }

        return true;
    }

    /** Build the join meeting url based on user role */
    public String getJoinMeetingURL(String meetingID, String userId, String userDisplayName, String password) {
        StringBuilder query = new StringBuilder();
        try {
            query.append("meetingID=");
            query.append(URLEncoder.encode(meetingID, getParametersEncoding()));
            if (userId != null) {
                query.append("&userID=");
                query.append(URLEncoder.encode(userId, getParametersEncoding()));
            }
        } catch (UnsupportedEncodingException e) {
        }
        query.append("&fullName=");
        if (userDisplayName == null) {
            userDisplayName = "user";
        }
        try {
            query.append(URLEncoder.encode(userDisplayName, getParametersEncoding()));
        } catch (UnsupportedEncodingException e) {
            query.append(userDisplayName);
        }
        query.append("&password=");
        query.append(password);
        query.append(getCheckSumParameterForQuery(APICALL_JOIN, query.toString()));

        StringBuilder url = new StringBuilder(bbbUrl);
        if (url.toString().endsWith("/api")) {
            url.append("/");
        } else {
            url.append(API_SERVERPATH);
        }
        url.append(APICALL_JOIN);
        url.append("?");
        url.append(query);

        return url.toString();
    }

    /** Make sure the meeting (still) exists on BBB server */
    public void makeSureMeetingExists(BBBMeeting meeting, boolean autoclose, boolean recordingenabled, boolean recordingreadynotification, boolean preuploadpresentation)
            throws BBBException {
        // (re)create meeting in BBB
        createMeeting(meeting, autoclose, recordingenabled, recordingreadynotification, preuploadpresentation);
    }

    /** Get the BBB API version running on BBB server */
    public final String getAPIVersion() {
        String _version = null;
        try {
            Map<String, Object> response = doAPICall(APICALL_VERSION, null);
            _version = (String) response.get("version");
            _version = _version != null ? _version.trim() : null;
            if (_version == null || Float.valueOf(_version.substring(0, 3)) < 0.0) {
                logger.warn("Invalid BigBlueButton version (" + _version + ")");
                _version = null;
            }
            _version = _version.trim();
        } catch (BBBException e) {
            if (BBBException.MESSAGEKEY_NOACTION.equals(e.getMessageKey())) {
                // we are clearly connecting to BBB < 0.70 => assuming minimum
                // version (0.63)
                _version = APIVERSION_MINIMUM;
            } else {
                // something went wrong => warn user
                logger.warn("Unable to check BigBlueButton version: " + e.getMessage());
                _version = null;
            }
        } catch (Exception e) {
            // something went wrong => warn user
            logger.warn("Unable to check BigBlueButton version: " + e.getMessage());
            _version = null;
        }
        return _version;
    }

    // -----------------------------------------------------------------------
    // --- BBB API utility methods -------------------------------------------
    // -----------------------------------------------------------------------
    /** Compute the query string checksum based on the security salt */
    protected String getCheckSumParameterForQuery(String apiCall,
            String queryString) {
        if (bbbSalt != null)
            return "&checksum=" + DigestUtils.shaHex(apiCall + queryString + bbbSalt);
        else
            return "";
    }

    /** Encoding used when encoding url parameters */
    protected String getParametersEncoding() {
        return "UTF-8";
    }

    protected Map<String, Object> doAPICall(String apiCall, String query)
            throws BBBException {
        return doAPICall(apiCall, query, "");
    }

    /** Make an API call */
    protected Map<String, Object> doAPICall(String apiCall, String query, String presentation)
            throws BBBException {
        StringBuilder urlStr = new StringBuilder(bbbUrl);
        if (urlStr.toString().endsWith("/api")){
            urlStr.append("/");
        } else {
            urlStr.append(API_SERVERPATH);
        }
        urlStr.append(apiCall);
        if (query != null) {
            urlStr.append("?");
            urlStr.append(query);
        }

        try {
            // open connection
            logger.debug("doAPICall.call: " + apiCall + "?" + (query != null ? query : ""));

            URL url = new URL(urlStr.toString());
            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setUseCaches(false);
            httpConnection.setDoOutput(true);
            if(presentation != ""){
                httpConnection.setRequestMethod("POST");
                httpConnection.setRequestProperty("Content-Type", "text/xml");
                httpConnection.setRequestProperty("Content-Length", "" + Integer.toString(presentation.getBytes().length));
                httpConnection.setRequestProperty("Content-Language", "en-US");
                httpConnection.setDoInput(true);

                DataOutputStream wr = new DataOutputStream( httpConnection.getOutputStream() );
                wr.writeBytes (presentation);
                wr.flush();
                wr.close();
            } else {
                httpConnection.setRequestMethod("GET");
            }
            httpConnection.connect();

            int responseCode = httpConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // read response
                InputStreamReader isr = null;
                BufferedReader reader = null;
                StringBuilder xml = new StringBuilder();
                try {
                    isr = new InputStreamReader(httpConnection.getInputStream(), "UTF-8");
                    reader = new BufferedReader(isr);
                    String line = reader.readLine();
                    while (line != null) {
                    	if( !line.startsWith("<?xml version=\"1.0\"?>"))
                    		xml.append(line.trim());
                        line = reader.readLine();
                    }
                } finally {
                    if (reader != null)
                        reader.close();
                    if (isr != null)
                        isr.close();
                }
                httpConnection.disconnect();

                // parse response
                logger.debug("doAPICall.response: " + xml);
                //Patch to fix the NaN error
                String stringXml = xml.toString();
                stringXml = stringXml.replaceAll(">.\\s+?<", "><");

                Document dom = null;


                // Initialize XML libraries
                DocumentBuilderFactory docBuilderFactory;
                DocumentBuilder docBuilder;
                docBuilderFactory = DocumentBuilderFactory.newInstance();
                try {
                    docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

                    docBuilder = docBuilderFactory.newDocumentBuilder();

                    dom = docBuilder.parse(new InputSource( new StringReader(stringXml)));
                } catch (ParserConfigurationException e) {
                    logger.error("Failed to initialise XML Parser", e);
                }


                Map<String, Object> response = getNodesAsMap(dom, "response");

                String returnCode = (String) response.get("returncode");
                if (APIRESPONSE_FAILED.equals(returnCode)) {
                    throw new BBBException((String) response.get("messageKey"), (String) response.get("message"));
                }

                return response;

            } else {
                throw new BBBException(BBBException.MESSAGEKEY_HTTPERROR, "BBB server responded with HTTP status code " + responseCode);
            }

		} catch(BBBException e) {
		    if( !e.getMessageKey().equals("notFound") )
		        logger.debug("doAPICall.BBBException: MessageKey=" + e.getMessageKey() + ", Message=" + e.getMessage());
			throw new BBBException( e.getMessageKey(), e.getMessage(), e);
        } catch(IOException e) {
            logger.debug("doAPICall.IOException: Message=" + e.getMessage());
            throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage(), e);

        } catch(SAXException e) {
            logger.debug("doAPICall.SAXException: Message=" + e.getMessage());
        	throw new BBBException(BBBException.MESSAGEKEY_INVALIDRESPONSE, e.getMessage(), e);

        } catch(IllegalArgumentException e) {
            logger.debug("doAPICall.IllegalArgumentException: Message=" + e.getMessage());
        	throw new BBBException(BBBException.MESSAGEKEY_INVALIDRESPONSE, e.getMessage(), e);

        } catch(Exception e) {
            logger.debug("doAPICall.Exception: Message=" + e.getMessage());
            throw new BBBException(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage(), e);
        }
    }


    // -----------------------------------------------------------------------
    // --- BBB Other utility methods -----------------------------------------
    // -----------------------------------------------------------------------
    /** Get all nodes under the specified element tag name as a Java map */
    protected Map<String, Object> getNodesAsMap(Document dom, String elementTagName) {
        Node firstNode = dom.getElementsByTagName(elementTagName).item(0);
        return processNode(firstNode);
    }

    protected Map<String, Object> processNode(Node _node) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        NodeList responseNodes = _node.getChildNodes();
        int images = 1; //counter for images (i.e image1, image2, image3)
        for (int i = 0; i < responseNodes.getLength(); i++) {
            Node node = responseNodes.item(i);
            String nodeName = node.getNodeName().trim();
            if (node.getChildNodes().getLength() == 1
                    && ( node.getChildNodes().item(0).getNodeType() == org.w3c.dom.Node.TEXT_NODE || node.getChildNodes().item(0).getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) ) {
                String nodeValue = node.getTextContent();
                if (nodeName == "image" && node.getAttributes() != null){
                    Map<String, String> imageMap = new LinkedHashMap<String, String>();
                    Node heightAttr = node.getAttributes().getNamedItem("height");
                    Node widthAttr = node.getAttributes().getNamedItem("width");
                    Node altAttr = node.getAttributes().getNamedItem("alt");

                    imageMap.put("height", heightAttr.getNodeValue());
                    imageMap.put("width", widthAttr.getNodeValue());
                    imageMap.put("title", altAttr.getNodeValue());
                    imageMap.put("url", nodeValue);
                    map.put(nodeName + images, imageMap);
                    images++;
                } else {
                    map.put(nodeName, nodeValue != null ? nodeValue.trim() : null);
                }
            } else if (node.getChildNodes().getLength() == 0
                    && node.getNodeType() != org.w3c.dom.Node.TEXT_NODE
                    && node.getNodeType() != org.w3c.dom.Node.CDATA_SECTION_NODE) {
                map.put(nodeName, "");

            } else if ( node.getChildNodes().getLength() >= 1
                    && node.getChildNodes().item(0).getChildNodes().item(0).getNodeType() != org.w3c.dom.Node.TEXT_NODE
                    && node.getChildNodes().item(0).getChildNodes().item(0).getNodeType() != org.w3c.dom.Node.CDATA_SECTION_NODE ) {

            	List<Object> list = new ArrayList<Object>();
                for (int c = 0; c < node.getChildNodes().getLength(); c++) {
                    Node n = node.getChildNodes().item(c);
                    list.add(processNode(n));
                }
                if (nodeName == "preview"){
                    Node n = node.getChildNodes().item(0);
                    map.put(nodeName, new ArrayList<Object>(processNode(n).values()));
                }else{
                    map.put(nodeName, list);
                }

            } else {
                map.put(nodeName, processNode(node));
            }
        }
        return map;
    }

    /** Generate a random password */
    protected String generatePassword() {
        return Long.toHexString(randomGenerator.nextLong());
    }

    /** To fix the old format of getRecordings, it parses a data string ant convert it to unix timestamp */
    protected String getDateAsStringTimestamp( String input ) {

    	long timestamp;
        try {
            timestamp = Long.parseLong( input );
            timestamp = timestamp/1000*1000;
            return Long.toString(timestamp);
         }
         catch( Exception e )  {
             try {
                 java.text.DateFormat formatter = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
                 Date date = (Date)formatter.parse(input);
                 timestamp = Long.valueOf(date.getTime());
                 timestamp = timestamp/1000*1000;
                 return Long.toString(timestamp);
              }  catch( Exception e2 )  {
              	return "";
              }
         }
    }
}
