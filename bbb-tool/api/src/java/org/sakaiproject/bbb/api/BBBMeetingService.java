package org.sakaiproject.bbb.api;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityProducer;

public interface BBBMeetingService extends EntityProducer {
   
    public static final String APPLICATION_ID = "sakai:bbb";
    
    public static final String REFERENCE_ROOT = Entity.SEPARATOR + "bbb-tool";

}
