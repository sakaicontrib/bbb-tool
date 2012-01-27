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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.Participant;
import org.sakaiproject.bbb.impl.util.XmlUtil;

/**
 * Default Database implementation for Sakai BigBlueButton persistence.
 * 
 * @author Adrian Fish, Nuno Fernandes
 */
public class DefaultSqlGenerator implements SqlGenerator {
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
                "HOST_URL " + VARCHAR + "(255) NOT NULL," +
                "SITE_ID " + VARCHAR + "(99) NOT NULL, " +
                "ATTENDEE_PW " + VARCHAR + "(99) NOT NULL, " +
                "MODERATOR_PW " + VARCHAR + "(99) NOT NULL, " +
                "OWNER_ID " + VARCHAR + "(99) NOT NULL, " +
                "START_DATE " + TIMESTAMP + ", " +
                "END_DATE " + TIMESTAMP + ", " +
                "RECORDING " + BOOL + ", " +
                "RECORDING_DURATION " + INT + ", " +
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
    //only add the table and the code that need to be updated
    //                              JFederico
    public Map<String, String> getUpdateStatements() {
        Map<String, String> statements = new HashMap<String, String>();

        statements.put("BBB_MEETING:HOST_URL", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN HOST_URL VARCHAR(255) NOT NULL;");
        statements.put("BBB_MEETING:RECORDING", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN RECORDING " + BOOL + ";"); 
        statements.put("BBB_MEETING:RECORDING_DURATION", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN RECORDING_DURATION " + INT + ";");
        statements.put("BBB_MEETING:DELETED", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN DELETED " + INT + " DEFAULT 0 NOT NULL;");
        
        return statements;
    }

    public String getShowNewColumnStatement(String updateName){
        String statement = null;

        if( updateName.equals("BBB_MEETING:HOST_URL"))
            statement = "SHOW COLUMNS FROM BBB_MEETING LIKE 'HOST_URL'";
        else if( updateName.equals("BBB_MEETING:RECORDING"))
            statement = "SHOW COLUMNS FROM BBB_MEETING LIKE 'RECORDING'";
        else if( updateName.equals("BBB_MEETING:RECORDING_DURATION"))
            statement = "SHOW COLUMNS FROM BBB_MEETING LIKE 'RECORDING_DURATION'";
        else if( updateName.equals("BBB_MEETING:DELETED"))
            statement = "SHOW COLUMNS FROM BBB_MEETING LIKE 'DELETED'";
        
        return statement;
    }
    // 
    //Code for automatic updates to the database ends
    //
    
    public List<PreparedStatement> getStoreMeetingStatements(
            BBBMeeting meeting, Connection connection) throws Exception {
        
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        PreparedStatement meetingST = connection
                .prepareStatement("INSERT INTO BBB_MEETING VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");
        meetingST.setString(1, meeting.getId());
        meetingST.setString(2, meeting.getName());
        meetingST.setString(3, meeting.getHostUrl());
        meetingST.setString(4, meeting.getSiteId());
        meetingST.setString(5, meeting.getAttendeePassword());
        meetingST.setString(6, meeting.getModeratorPassword());
        meetingST.setString(7, meeting.getOwnerId());
        meetingST.setTimestamp(8, meeting.getStartDate() == null ? null: new Timestamp(meeting.getStartDate().getTime()));
        meetingST.setTimestamp(9, meeting.getEndDate() == null ? null: new Timestamp(meeting.getEndDate().getTime()));
        meetingST.setBoolean(10, meeting.getRecording());
        meetingST.setLong(11, meeting.getRecordingDuration() == null ? 0L: meeting.getRecordingDuration());
        meetingST.setString(12, XmlUtil.convertPropsToXml(meeting.getProps()));
        meetingST.setString(13, NODELETED);

        statements.add(meetingST);

        List<Participant> participants = meeting.getParticipants();

        for (Participant participant : participants) {
            PreparedStatement pST = connection.prepareStatement("INSERT INTO BBB_MEETING_PARTICIPANT VALUES(?,?,?,?)");
            pST.setString(1, meeting.getId());
            pST.setString(2, participant.getSelectionType());
            pST.setString(3, participant.getSelectionId());
            pST.setString(4, participant.getRole());
            statements.add(pST);
        }

        return statements;
    }

    public List<PreparedStatement> getUpdateMeetingStatements(
            BBBMeeting meeting, Connection connection) throws Exception {
        
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        PreparedStatement meetingST = connection
                .prepareStatement("UPDATE BBB_MEETING SET NAME=?, SITE_ID=?, HOST_URL = ?, ATTENDEE_PW=?, MODERATOR_PW=?, OWNER_ID=?, START_DATE=?, END_DATE=?, RECORDING=?, RECORDING_DURATION=?, PROPERTIES=? WHERE MEETING_ID=?");
        meetingST.setString(1, meeting.getName());
        meetingST.setString(2, meeting.getSiteId());
        meetingST.setString(3, meeting.getHostUrl());
        meetingST.setString(4, meeting.getAttendeePassword());
        meetingST.setString(5, meeting.getModeratorPassword());
        meetingST.setString(6, meeting.getOwnerId());
        meetingST.setTimestamp(7, meeting.getStartDate() == null ? null: new Timestamp(meeting.getStartDate().getTime()));
        meetingST.setTimestamp(8, meeting.getEndDate() == null ? null: new Timestamp(meeting.getEndDate().getTime()));
        meetingST.setBoolean(9, meeting.getRecording());
        meetingST.setLong(10, meeting.getRecordingDuration() == null ? 0L: meeting.getRecordingDuration());
        meetingST.setString(11, XmlUtil.convertPropsToXml(meeting.getProps()));
        meetingST.setString(12, meeting.getId());

        statements.add(meetingST);

        return statements;
    }

    public List<PreparedStatement> getUpdateMeetingParticipantsStatements(
            BBBMeeting meeting, Connection connection) throws Exception {
        
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();

        PreparedStatement participantsST = connection
                .prepareStatement("DELETE FROM BBB_MEETING_PARTICIPANT WHERE MEETING_ID = ?");
        participantsST.setString(1, meeting.getId());
        statements.add(participantsST);

        List<Participant> participants = meeting.getParticipants();
        for (Participant participant : participants) {
            PreparedStatement pST = connection
                    .prepareStatement("INSERT INTO BBB_MEETING_PARTICIPANT VALUES(?,?,?,?)");
            pST.setString(1, meeting.getId());
            pST.setString(2, participant.getSelectionType());
            pST.setString(3, participant.getSelectionId());
            pST.setString(4, participant.getRole());
            statements.add(pST);
        }

        return statements;
    }

    public String getSelectSiteMeetingsStatement(String siteId) {
        return "SELECT * FROM BBB_MEETING WHERE SITE_ID = '" + siteId + "'";
    }

    public String getSelectMeetingParticipantsStatement(String meetingId) {
        return "SELECT * FROM BBB_MEETING_PARTICIPANT WHERE MEETING_ID = '"
                + meetingId + "'";
    }

    public String getSelectMeetingStatement(String meetingId) {
        return "SELECT * FROM BBB_MEETING WHERE MEETING_ID = '" + meetingId
                + "'";
    }

    public List<PreparedStatement> getDeleteMeetingStatements(String meetingId,
            Connection connection) throws Exception {
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        PreparedStatement meetingST = connection.prepareStatement("DELETE FROM BBB_MEETING WHERE MEETING_ID = ?");
        meetingST.setString(1, meetingId);
        statements.add(meetingST);
        PreparedStatement participantsST = connection.prepareStatement("DELETE FROM BBB_MEETING_PARTICIPANT WHERE MEETING_ID = ?");
        participantsST.setString(1, meetingId);
        statements.add(participantsST);
        return statements;
    }

    public List<PreparedStatement> getMarkMeetingAsDeletedStatements(String meetingId,
            Connection connection) throws Exception {
        List<PreparedStatement> statements = new ArrayList<PreparedStatement>();
        PreparedStatement meetingST = connection.prepareStatement("UPDATE BBB_MEETING SET DELETED = 1 WHERE MEETING_ID = ?");
        meetingST.setString(1, meetingId);
        statements.add(meetingST);
        return statements;
    }
    
    
    public String getSelectMeetingHostStatement(String meetingID) {
        return "SELECT HOST_URL FROM BBB_MEETING WHERE MEETING_ID = '"
                + meetingID + "'";
    }

    public String getSelectAllMeetingsStatement() {
        return "SELECT * FROM BBB_MEETING";
    }

    public PreparedStatement getUpdateHostForMeetingStatement(String meetingId,
            String url, Connection connection) throws Exception {
        PreparedStatement st = connection
                .prepareStatement("UPDATE BBB_MEETING SET HOST_URL = ? WHERE MEETING_ID = ?");
        st.setString(1, url);
        st.setString(2, meetingId);
        return st;
    }
}
