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

package org.sakaiproject.bbb.impl.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.log4j.Logger;

import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.Participant;
import org.sakaiproject.bbb.impl.util.XmlUtil;

/**
 * Default Database implementation for Sakai BigBlueButton persistence.
 * 
 * @author Adrian Fish, Nuno Fernandes
 */
public class DefaultSqlGenerator implements SqlGenerator {
	
    protected final Logger logger = Logger.getLogger(getClass());

    // DB Data Types
    protected String CHAR = "CHAR";
    protected String VARCHAR = "VARCHAR";
    protected String TIMESTAMP = "DATETIME";
    protected String TEXT = "TEXT";
    protected String MEDIUMTEXT = "MEDIUMTEXT";
    protected String BOOL = "BOOL";
    protected String INT = "INT";
    
    protected String NODELETED = "0";

    public Map<String, String> getSetupStatements() {
        Map<String, String> statements = new HashMap<String, String>();

        statements.put("BBB_MEETING", "CREATE TABLE BBB_MEETING (MEETING_ID " + CHAR + "(36) NOT NULL," +
                "NAME " + VARCHAR + "(255) NOT NULL, " +
                "HOST_URL " + VARCHAR + "(255)," +
                "SITE_ID " + VARCHAR + "(99) NOT NULL, " +
                "ATTENDEE_PW " + VARCHAR + "(99) NOT NULL, " +
                "MODERATOR_PW " + VARCHAR + "(99) NOT NULL, " +
                "OWNER_ID " + VARCHAR + "(99) NOT NULL, " +
                "START_DATE " + TIMESTAMP + ", " +
                "END_DATE " + TIMESTAMP + ", " +
                "RECORDING " + BOOL + ", " +
				"RECORDING_READY_NOTIFICATION " + BOOL + ", " +
                "RECORDING_DURATION " + INT + ", " +
                "VOICE_BRIDGE " + INT + ", " +
                "WAIT_FOR_MODERATOR " + BOOL + ", " +
                "MULTIPLE_SESSIONS_ALLOWED " + BOOL + ", " +
				"ONE_SESSION_PER_GROUP " + BOOL + ", " +
                "PROPERTIES " + TEXT + ", " +
                "DELETED " + INT + " DEFAULT 0 NOT NULL," +
                "CONSTRAINT bbb_meeting_pk PRIMARY KEY (MEETING_ID))");

        statements.put("BBB_MEETING_PARTICIPANT", "CREATE TABLE BBB_MEETING_PARTICIPANT (" +
        	"MEETING_ID " + CHAR + "(36) NOT NULL, " +
        	"SELECTION_TYPE " + VARCHAR + "(99) NOT NULL, " +
        	"SELECTION_ID " + VARCHAR + "(99), " +
        	"ROLE " + VARCHAR + "(32) NOT NULL," + 
        	"CONSTRAINT bbb_meeting_participant_pk PRIMARY KEY (MEETING_ID,SELECTION_TYPE,SELECTION_ID))");

        return statements;
    }

    public String getShowTableStatement(String table) {
        return "SHOW TABLES like '" + table + "'";
    }

    // 
    //Code for automatic updates to the database
    //This is for updating from 1.0.6 to 1.0.7 however for next updates 
    //only add the table and the code that needs to be updated
    //                              JFederico
    public Map<String, String> getUpdateStatements() {
        Map<String, String> statements = new LinkedHashMap<String, String>();

        statements.put("BBB_MEETING:HOST_URL:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN HOST_URL " + VARCHAR + "(255) AFTER NAME;");
        statements.put("BBB_MEETING:RECORDING:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN RECORDING " + BOOL + " AFTER END_DATE;"); 
        statements.put("BBB_MEETING:RECORDING_READY_NOTIFICATION:ADD",
				"ALTER TABLE BBB_MEETING ADD COLUMN RECORDING_READY_NOTIFICATION " + BOOL + " AFTER RECORDING;");
		statements.put("BBB_MEETING:RECORDING_DURATION:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN RECORDING_DURATION " + INT + " AFTER RECORDING;");
        statements.put("BBB_MEETING:DELETED:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN DELETED " + INT + " DEFAULT 0 NOT NULL AFTER PROPERTIES;");
        statements.put("BBB_MEETING_PARTICIPANT:DELETED:DROP", 
                "ALTER TABLE BBB_MEETING_PARTICIPANT DROP COLUMN DELETED;");
        statements.put("BBB_MEETING:VOICE_BRIDGE:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN VOICE_BRIDGE " + INT + " AFTER RECORDING_DURATION;"); 
        statements.put("BBB_MEETING:WAIT_FOR_MODERATOR:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN WAIT_FOR_MODERATOR " + BOOL + " AFTER VOICE_BRIDGE;"); 
        statements.put("BBB_MEETING:MULTIPLE_SESSIONS_ALLOWED:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN MULTIPLE_SESSIONS_ALLOWED " + BOOL + " AFTER WAIT_FOR_MODERATOR;"); 
        statements.put("BBB_MEETING:ONE_SESSION_PER_GROUP:ADD",
				"ALTER TABLE BBB_MEETING ADD COLUMN ONE_SESSION_PER_GROUP " + BOOL + " AFTER MULTIPLE_SESSIONS_ALLOWED;");
		statements.put("BBB_MEETING:HOST_URL:CHANGE",
                "ALTER TABLE BBB_MEETING CHANGE COLUMN HOST_URL HOST_URL " + VARCHAR + "(255);"); 

        return statements;
    }

    public String getShowColumnStatement(String tableName, String columnName) {
        return "SHOW COLUMNS FROM " + tableName + " LIKE '" + columnName + "'";
    }

    // 
    //Code for automatic updates to the database ends
    //
    
    public List<PreparedStatement> getStoreMeetingStatements(BBBMeeting meeting, Connection connection) 
    		throws Exception {
        
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        PreparedStatement meetingST = connection.prepareStatement("INSERT INTO BBB_MEETING " +
                "(MEETING_ID, NAME, HOST_URL, SITE_ID, ATTENDEE_PW, MODERATOR_PW, OWNER_ID, START_DATE, END_DATE, RECORDING, RECORDING_READY_NOTIFICATION, RECORDING_DURATION, VOICE_BRIDGE, WAIT_FOR_MODERATOR, MULTIPLE_SESSIONS_ALLOWED, PREUPLOAD_PRESENTATION, PRESENTATION, ONE_SESSION_PER_GROUP, PROPERTIES, DELETED)" +
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        meetingST.setString(1, meeting.getId());
        meetingST.setString(2, meeting.getName());
        meetingST.setString(3, meeting.getHostUrl());
        meetingST.setString(4, meeting.getSiteId());
        meetingST.setString(5, meeting.getAttendeePassword());
        meetingST.setString(6, meeting.getModeratorPassword());
        meetingST.setString(7, meeting.getOwnerId());
        meetingST.setTimestamp(8, meeting.getStartDate() == null ? null: new Timestamp(meeting.getStartDate().getTime()) );
        meetingST.setTimestamp(9, meeting.getEndDate() == null ? null: new Timestamp(meeting.getEndDate().getTime()) );
        meetingST.setBoolean(10, meeting.getRecording());
		meetingST.setBoolean(11, meeting.getRecordingReadyNotification());
        meetingST.setLong(12, meeting.getRecordingDuration() == null ? 0L: meeting.getRecordingDuration());
        meetingST.setLong(13, meeting.getVoiceBridge() );
        meetingST.setBoolean(14, meeting.getWaitForModerator());
        meetingST.setBoolean(15, meeting.getMultipleSessionsAllowed());
		meetingST.setBoolean(16, meeting.getPreuploadPresentation());
		meetingST.setString(17, meeting.getPresentation());
		meetingST.setBoolean(18, meeting.getOneSessionPerGroup());
        meetingST.setString(19, XmlUtil.convertPropsToXml(meeting.getProps()));
        meetingST.setString(20, NODELETED);

        statements.add(meetingST);

        List<Participant> participants = meeting.getParticipants();

        for (Participant participant : participants) {
            PreparedStatement pST = connection.prepareStatement("INSERT INTO BBB_MEETING_PARTICIPANT " +
            		"(MEETING_ID, SELECTION_TYPE, SELECTION_ID, ROLE)" +
            		" VALUES(?,?,?,?)");
            pST.setString(1, meeting.getId());
            pST.setString(2, participant.getSelectionType());
            pST.setString(3, participant.getSelectionId());
            pST.setString(4, participant.getRole());
            statements.add(pST);
        }

        return statements;
    }

    public List<PreparedStatement> getUpdateMeetingStatements(BBBMeeting meeting, Connection connection) 
    		throws Exception {
        
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        PreparedStatement meetingST = connection
                .prepareStatement("UPDATE BBB_MEETING SET NAME=?, SITE_ID=?, HOST_URL = ?, ATTENDEE_PW=?, MODERATOR_PW=?, OWNER_ID=?, START_DATE=?, END_DATE=?, RECORDING=?, RECORDING_READY_NOTIFICATION=?, RECORDING_DURATION=?, VOICE_BRIDGE=?, WAIT_FOR_MODERATOR=?, MULTIPLE_SESSIONS_ALLOWED=?, PREUPLOAD_PRESENTATION=?, PRESENTATION=?, ONE_SESSION_PER_GROUP=?, PROPERTIES=? WHERE MEETING_ID=?");
        meetingST.setString(1, meeting.getName());
        meetingST.setString(2, meeting.getSiteId());
        meetingST.setString(3, meeting.getHostUrl());
        meetingST.setString(4, meeting.getAttendeePassword());
        meetingST.setString(5, meeting.getModeratorPassword());
        meetingST.setString(6, meeting.getOwnerId());
        meetingST.setTimestamp(7, meeting.getStartDate() == null ? null: new Timestamp(meeting.getStartDate().getTime()) );
        meetingST.setTimestamp(8, meeting.getEndDate() == null ? null: new Timestamp(meeting.getEndDate().getTime()) );
        meetingST.setBoolean(9, meeting.getRecording());
		meetingST.setBoolean(10, meeting.getRecordingReadyNotification());
        meetingST.setLong(11, meeting.getRecordingDuration() == null ? 0L: meeting.getRecordingDuration());
        meetingST.setLong(12, meeting.getVoiceBridge() );
        meetingST.setBoolean(13, meeting.getWaitForModerator());
        meetingST.setBoolean(14, meeting.getMultipleSessionsAllowed());
		meetingST.setBoolean(15, meeting.getPreuploadPresentation());
		meetingST.setString(16, meeting.getPresentation());
		meetingST.setBoolean(17, meeting.getOneSessionPerGroup());
        meetingST.setString(18, XmlUtil.convertPropsToXml(meeting.getProps()));
        meetingST.setString(19, meeting.getId());

        statements.add(meetingST);

        return statements;
    }

    public List<PreparedStatement> getUpdateMeetingParticipantsStatements(BBBMeeting meeting, Connection connection) 
    		throws Exception {
        
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();

        PreparedStatement participantsST = connection.prepareStatement("DELETE FROM BBB_MEETING_PARTICIPANT WHERE MEETING_ID = ?");
        participantsST.setString(1, meeting.getId());
        statements.add(participantsST);

        List<Participant> participants = meeting.getParticipants();
        for (Participant participant : participants) {
            PreparedStatement pST = connection.prepareStatement("INSERT INTO BBB_MEETING_PARTICIPANT " +
                    		"(MEETING_ID, SELECTION_TYPE, SELECTION_ID, ROLE)" +
                    		" VALUES(?,?,?,?)");
            pST.setString(1, meeting.getId());
            pST.setString(2, participant.getSelectionType());
            pST.setString(3, participant.getSelectionId());
            pST.setString(4, participant.getRole());
            statements.add(pST);
        }

        return statements;
    }

    public PreparedStatement getSelectSiteMeetingsStatement(String siteId, boolean includeDeletedMeetings, Connection connection)
            throws Exception {

        PreparedStatement statement = null;

        String sql = "SELECT * FROM BBB_MEETING WHERE SITE_ID = ?";
        if( !includeDeletedMeetings ) {
            sql += " AND DELETED != 1";
        }
        statement = connection.prepareStatement(sql);
        statement.setString(1, siteId);

        return statement;
    }

    public PreparedStatement getSelectMeetingParticipantsStatement(String meetingId, Connection connection)
            throws Exception {

        PreparedStatement statement = null;

        statement = connection.prepareStatement("SELECT * FROM BBB_MEETING_PARTICIPANT WHERE MEETING_ID = ?");
        statement.setString(1, meetingId);

        return statement;
    }

    public PreparedStatement getSelectMeetingStatement(String meetingId, Connection connection)
            throws Exception {

        PreparedStatement statement = null;

        statement = connection.prepareStatement("SELECT * FROM BBB_MEETING WHERE MEETING_ID = ?");
        statement.setString(1, meetingId);

        return statement;
    }

    public List<PreparedStatement> getDeleteMeetingStatements(String meetingId, Connection connection) 
    		throws Exception {

        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();

        PreparedStatement meetingST = connection.prepareStatement("DELETE FROM BBB_MEETING WHERE MEETING_ID = ?");
        meetingST.setString(1, meetingId);
        statements.add(meetingST);
        PreparedStatement participantsST = connection.prepareStatement("DELETE FROM BBB_MEETING_PARTICIPANT WHERE MEETING_ID = ?");
        participantsST.setString(1, meetingId);
        statements.add(participantsST);

        return statements;
    }

    public List<PreparedStatement> getMarkMeetingAsDeletedStatements(String meetingId, Connection connection) 
    		throws Exception {

        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();

        PreparedStatement meetingST = connection.prepareStatement("UPDATE BBB_MEETING SET DELETED = 1 WHERE MEETING_ID = ?");
        meetingST.setString(1, meetingId);
        statements.add(meetingST);

        return statements;
    }
    
    
    public PreparedStatement getSelectMeetingHostStatement(String meetingID, Connection connection)
            throws Exception {

        PreparedStatement statement = null;

        statement = connection.prepareStatement("SELECT HOST_URL FROM BBB_MEETING WHERE MEETING_ID = ?");
        statement.setString(1, meetingID);

        return statement;
    }

    public String getSelectAllMeetingsStatement() {
        return "SELECT * FROM BBB_MEETING";
    }

    public PreparedStatement getUpdateHostForMeetingStatement(String meetingId, String url, Connection connection)
    		throws Exception {

        PreparedStatement st = connection.prepareStatement("UPDATE BBB_MEETING SET HOST_URL = ? WHERE MEETING_ID = ?");

        st.setString(1, url);
        st.setString(2, meetingId);

        return st;
    }
}
