package org.sakaiproject.bbb.tool.entity;

import lombok.Getter;
import lombok.Setter;

import org.apache.log4j.Logger;

import org.sakaiproject.bbb.api.BBBMeetingManager;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.EntityTransferrerRefMigrator;

public class BBBMeetingEntityProducer { //implements EntityTransferrer, EntityTransferrerRefMigrator {

    private Logger logger = Logger.getLogger(BBBMeetingEntityProducer.class);
    //private final Logger logger = Logger.getLogger(getClass());

    //private static BBBMeetingEntityProducer entityProducer = null; 

    @Setter @Getter
    private BBBMeetingManager meetingManager;
    
    public void init()
    {
        logger.debug("init");
        
        //BBBMeetingEntityProducer.entityProducer = this;

    }
    
    public String getLabel()
    {
      return "sakai.bbb";
    }


}
