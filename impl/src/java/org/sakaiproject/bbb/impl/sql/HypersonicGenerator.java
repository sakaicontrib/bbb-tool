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

import java.util.HashMap;
import java.util.Map;

/**
 * HSQLDB Database implementation for Sakai BigBlueButton persistence.
 * @author Adrian Fish, Nuno Fernandes
 */
public class HypersonicGenerator extends DefaultSqlGenerator
{
	public HypersonicGenerator()
	{
		CHAR		= "CHAR";
		VARCHAR    	= "VARCHAR";
		TIMESTAMP  	= "TIMESTAMP";
		TEXT       	= "LONGVARCHAR";
		BOOL 		= "BOOLEAN";
		MEDIUMTEXT 	= "LONGVARCHAR";
	}
	
	public String getShowTableStatement(String table)
	{
		return "select TABLE_NAME from INFORMATION_SCHEMA.SYSTEM_TABLES where TABLE_NAME='" + table + "'";
	}
	
    public String getShowColumnStatement(String tableName, String columnName)
    {
        return "select COLUMN_NAME from INFORMATION_SCHEMA.SYSTEM_COLUMNS where TABLE_NAME='" + tableName + "' AND COLUMN_NAME='" + columnName + "'";
    }
	
    public Map<String, String> getUpdateStatements() {
        Map<String, String> statements = new HashMap<String, String>();

        statements.put("BBB_MEETING:HOST_URL:ADD", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN HOST_URL " + VARCHAR + "(255) NOT NULL AFTER NAME;");
        statements.put("BBB_MEETING:RECORDING:ADD", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN RECORDING " + BOOL + " BEFORE PROPERTIES;"); 
        statements.put("BBB_MEETING:RECORDING_DURATION:ADD", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN RECORDING_DURATION " + INT + " BEFORE PROPERTIES;");
        statements.put("BBB_MEETING:DELETED:ADD", 
        		"ALTER TABLE BBB_MEETING ADD COLUMN DELETED " + INT + " DEFAULT 0 NOT NULL;");
        statements.put("BBB_MEETING_PARTICIPANT:DELETED:DROP", 
        		"ALTER TABLE BBB_MEETING_PARTICIPANT DROP COLUMN DELETED;");
        statements.put("BBB_MEETING:VOICE_BRIDGE:ADD", 
                "ALTER TABLE BBB_MEETING ADD COLUMN VOICE_BRIDGE " + INT + " BEFORE PROPERTIES;"); 
        
        return statements;
    }
}
