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

package org.sakaiproject.bbb.api;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Sakai model object for a BigBlueButton meeting.
 * @author Adrian Fish
 */
@Getter @Setter @ToString
public class BBBMeeting implements Entity {
	private String id = null;
	private String name = null;
	private String siteId = null;
	private String attendeePassword = null;
	private String moderatorPassword = null;
	private String ownerId = null;
	private String ownerDisplayName = null;
	private Date startDate = null;
	private Date endDate = null;
	private Boolean waitForModerator = null;
    private Boolean multipleSessionsAllowed = null;
	private Boolean deleted = null;
	private Boolean recording = null; 
	private Long recordingDuration = null;
	private String recordingEmail = null;
	
	private Map<String, String> meta = new HashMap<String, String>();
	
	@Getter (AccessLevel.NONE)
	private Props props = null;
	
	private List<Participant> participants = null;
	private String joinUrl = null;
	private Integer voiceBridge = null;
	private String hostUrl = null;
	
	public void setId(String id) {
		this.id = id;
	}
	public String getId() {
		return id;
	}
	
	public Props getProps() {
		if(props == null)
			props = new Props();
		return props;
	}
	
	public ResourceProperties getProperties() {
		return null;
	}
	
	public String getReference() {
		return Entity.SEPARATOR + BBBMeetingManager.ENTITY_PREFIX + Entity.SEPARATOR + id;
	}
	
	public String getReference(String arg0) {
		return getReference();
	}
	
	public String getUrl() {
		return null;
	}
	
	public String getUrl(String arg0) {
		return getUrl();
	}
	
	public Element toXml(Document arg0, Stack arg1) {
		return null;
	}
	
	public void setWelcomeMessage(String welcomeMessage){
	    this.props.setWelcomeMessage(welcomeMessage);
	}

}
