package org.sakaiproject.bbb.impl;

import lombok.Getter;
import lombok.Setter;

import org.apache.log4j.Logger;

import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.api.BBBMeetingService;

public class BBBMeetingServiceImpl implements BBBMeetingService { //, EntityTransferrer, EntityTransferrerRefMigrator {

    private Logger logger = Logger.getLogger(BBBMeetingServiceImpl.class);

    @Setter @Getter
    private BBBMeetingManager meetingManager;
    
    public void init()
    {
        logger.debug("init");

    }

    public void destroy()
    {
        logger.debug("destroy");

    }

}
