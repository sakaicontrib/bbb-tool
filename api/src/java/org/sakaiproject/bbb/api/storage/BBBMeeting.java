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

package org.sakaiproject.bbb.api.storage;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import org.hibernate.annotations.Cascade;
//import org.hibernate.annotations.CascadeType;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import lombok.Getter;
import lombok.Setter;

/**
 * Sakai model object for a BigBlueButton meeting.
 *
 * @author Adrian Fish
 */
@Entity
@Table(name = "BBB_MEETING")
@Getter @Setter
public class BBBMeeting {

    @Id
    @Column(name = "MEETING_ID", length = 36, nullable = false)
	private String id;

    @Column(name = "NAME", length = 255, nullable = false)
	private String name;

    @Column(name = "HOST_URL", length = 255)
	private String hostUrl;

    @Column(name = "SITE_ID", length = 99, nullable = false)
	private String siteId;

    @Column(name = "ATTENDEE_PW", length = 99, nullable = false)
	private String attendeePassword;

    @Column(name = "MODERATOR_PW", length = 99, nullable = false)
	private String moderatorPassword;

    @Column(name = "OWNER_ID", length = 99, nullable = false)
	private String ownerId = null;

    @Column(name = "START_DATE")
	private Date startDate;

    @Column(name = "END_DATE")
	private Date endDate = null;

    @Column(name = "WAIT_FOR_MODERATOR")
	private Boolean waitForModerator;

    @Column(name = "MULTIPLE_SESSIONS_ALLOWED")
	private Boolean multipleSessionsAllowed;

    @Column(name = "PRESENTATION", length = 255)
	private String presentation;

    @Column(name = "GROUP_SESSIONS")
	private Boolean groupSessions;

    @Column(name = "DELETED", nullable = false)
	private Boolean deleted = false;

    @Column(name = "RECORDING")
	private Boolean recording;

    @Column(name = "RECORDING_DURATION")
	private Long recordingDuration;

    @ElementCollection
    @MapKeyColumn(name = "NAME")
    @Column(name = "VALUE")
    @CollectionTable(name = "BBB_MEETING_PROPERTIES", joinColumns = @JoinColumn(name = "MEETING_ID"))
	private Map<String, String> properties;

    @Column(name = "VOICE_BRIDGE")
	private Integer voiceBridge;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL)
    private List<BBBMeetingParticipant> participants;

    @Transient
    private String joinUrl;

    @Transient
    private String ownerDisplayName;

    @Transient
    private Map<String, String> meta = new HashMap<>();
}
