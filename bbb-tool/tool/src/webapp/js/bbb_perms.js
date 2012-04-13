/*
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

function BBBPermissions(data) {
	if(data) {
		for(var i=0,j=data.length;i<j;i++) {
			
			// BBB specific permissions
			if('bbb.admin' === data[i]) {
				this.bbbAdmin = true;
			}else if('bbb.create' === data[i]) {
				this.bbbCreate = true;
			}else if('bbb.edit.own' === data[i]) {
				this.bbbEditOwn = true;
				this.bbbViewMeetingList = true;
			}else if('bbb.edit.any' === data[i]) {
				this.bbbEditAny = true;
				this.bbbViewMeetingList = true;
			}else if('bbb.delete.own' === data[i]) {
				this.bbbDeleteOwn = true;
				this.bbbViewMeetingList = true;
			}else if('bbb.delete.any' === data[i]) {
				this.bbbDeleteAny = true;
				this.bbbViewMeetingList = true;
			}else if('bbb.participate' === data[i]) {
				this.bbbParticipate = true;
				this.bbbViewMeetingList = true;
			}
			
			// Sakai permissions
			// Site Info:
			else if('site.upd' === data[i]) {
				this.siteUpdate = true;
			}else if('site.viewRoster' === data[i]) {
				this.siteViewRoster = true;
			// Calendar:
			}else if('calendar.new' === data[i]) {
				this.calendarNew = true;
			}else if('calendar.revise.own' === data[i]) {
				this.calendarReviseOwn = true;
			}else if('calendar.revise.any' === data[i]) {
				this.calendarReviseAny = true;
			}else if('calendar.delete.own' === data[i]) {
				this.calendarDeleteOwn = true;
			}else if('calendar.delete.any' === data[i]) {
				this.calendarDeleteAny = true;
			}
		}
	}	
}