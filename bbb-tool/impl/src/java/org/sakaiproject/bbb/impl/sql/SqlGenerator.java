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
import java.util.List;
import java.util.Map;

import org.sakaiproject.bbb.api.BBBMeeting;

/**
 * Database interface class for Sakai BigBlueButton persistence.
 * @author Adrian Fish, Nuno Fernandes
 */
public interface SqlGenerator {

	/** Map of TABLE_NAME -> SQL CREATE STATEMENT */ 
	Map<String, String> getSetupStatements();

	/** SQL Statement to check if table exists in DB (if it produces results, then table is created) */
	String getShowTableStatement(String table);

	/** SQL Statement to store meeting in DB */
	List<PreparedStatement> getStoreMeetingStatements(BBBMeeting meeting,Connection connection) throws Exception;

	/** SQL Statement to update meeting in DB */
	List<PreparedStatement> getUpdateMeetingStatements(BBBMeeting meeting,Connection connection) throws Exception;

	/** SQL Statement to store meeting participants in DB */
	List<PreparedStatement> getUpdateMeetingParticipantsStatements(BBBMeeting meeting,Connection connection) throws Exception;

	/** SQL Statement to list site meetings from DB */
    PreparedStatement getSelectSiteMeetingsStatement(String siteId, boolean includeDeletedMeetings, Connection connection) throws Exception;

	/** SQL Statement to list meeting participants for the specified meeting from DB */
    PreparedStatement getSelectMeetingParticipantsStatement(String meetingId, Connection connection) throws Exception;

	/** SQL Statement to get a meeting from DB */
    PreparedStatement getSelectMeetingStatement(String meetingId, Connection connection) throws Exception;

	String getSelectAllMeetingsStatement();

	/** SQL Statements to delete a meeting and its participants from DB */
	List<PreparedStatement> getDeleteMeetingStatements(String meetingId,Connection connection) throws Exception;

	/** SQL Statements to mark a meeting and its participants as deleted in DB */
    List<PreparedStatement> getMarkMeetingAsDeletedStatements(String meetingId, Connection connection) throws Exception; 

	/** SQL Statement to get the HOST_URL for a specified meeting ID */
	PreparedStatement getSelectMeetingHostStatement(String meetingID, Connection connection) throws Exception;

	PreparedStatement getUpdateHostForMeetingStatement(String meetingId, String url, Connection connection) throws Exception;

	Map<String, String> getUpdateStatements();

	String getShowColumnStatement(String tableName, String columnName);
}
