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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Data;

/**
 * Model object representing a BigBlueButton meeting participant.
 * @author Adrian Fish,Nuno Fernandes
 */
@Entity
@Table(name = "BBB_MEETING_PARTICIPANT", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "MEETING_ID", "SELECTION_TYPE", "SELECTION_ID" })
})
@Data
public class BBBMeetingParticipant {

    @Id
    @GeneratedValue
    @Column(name = "ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "MEETING_ID")
    private BBBMeeting meeting;

    @Column(name = "SELECTION_ID", length = 99, nullable = false)
	private String selectionId;

    @Column(name = "SELECTION_TYPE", length = 99, nullable = false)
	private String selectionType;

    @Column(name = "ROLE", length = 32, nullable = false)
	private String role;
}
