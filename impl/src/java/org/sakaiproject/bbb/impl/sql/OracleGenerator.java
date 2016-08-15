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


/**
 * Oracle Database implementation for Sakai BigBlueButton persistence.
 * @author Adrian Fish, Nuno Fernandes
 */
public class OracleGenerator extends DefaultSqlGenerator
{
	public OracleGenerator()
	{
		CHAR		= "CHAR";
		VARCHAR 	= "VARCHAR2";
		TIMESTAMP 	= "TIMESTAMP";
		TEXT 		= "VARCHAR2(4000)";
		BOOL 		= "CHAR(1)";
		MEDIUMTEXT 	= "VARCHAR2";
	}
	
	public String getShowTableStatement(String table)
	{
		//return "select TNAME from tab where TABTYPE='TABLE' and TNAME='" + table + "'";
		return "select * from user_objects where OBJECT_TYPE='TABLE' and OBJECT_NAME='" + table + "'";
	}

    public String getShowColumnStatement(String tableName, String columnName) {
        return "SELECT column_name FROM user_tab_cols WHERE table_name = '" + tableName + "' and column_name like '"+columnName+"'";
    }
}
