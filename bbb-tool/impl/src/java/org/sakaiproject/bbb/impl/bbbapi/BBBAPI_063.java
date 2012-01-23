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

package org.sakaiproject.bbb.impl.bbbapi;

import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.sakaiproject.bbb.api.BBBException;

/**
 * Class for interacting with BigBlueButton API 0.63+ version.
 * 
 * @author Nuno Fernandes
 */
public class BBBAPI_063 extends BaseBBBAPI {

    public BBBAPI_063(String url, String salt) {
        super(url, salt);
    }

    protected String getParametersEncoding() {
        return "ISO-8859-1";
    }

    // In 0.63 (<0.70) this is not implemented so, nullify all values!
    public Map<String, Object> getMeetingInfo(String meetingID, String password)
            throws BBBException {
        Map<String, Object> map = super.getMeetingInfo(meetingID, password);
        for (String key : map.keySet()) {
            if ("participantCount".equals(key))
                map.put(key, "-1");
            else
                map.put(key, null);
        }
        return map;
    }

    // In 0.63 (< 0.70), checksum calculation is simpler
    protected String getCheckSumParameterForQuery(String apiCall,
            String queryString) {
        if (bbbSalt != null)
            return "&checksum=" + DigestUtils.shaHex(queryString + bbbSalt);
        else
            return "";
    }

    // In 0.63 (<0.80) this is not implemented so, nullify all values!
    public Map<String, Object> getRecordings(String meetingID)
            throws BBBException {
        Map<String, Object> map = super.getRecordings(meetingID);
        for (String key : map.keySet()) {
            if ("participantCount".equals(key))
                map.put(key, "-1");
            else
                map.put(key, null);
        }
        return map;
    }

    // In 0.63 (<0.80) this is not implemented so, nullify all values!
    public boolean publishRecordings(String meetingID, String recordID,
            String publish) throws BBBException {
        return super.publishRecordings(meetingID, recordID, publish);
    }

    public boolean deleteRecordings(String meetingID, String recordID)
            throws BBBException {
        return super.deleteRecordings(meetingID, recordID);
    }

}
