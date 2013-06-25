package org.sakaiproject.bbb.api;

import java.util.List;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityProducer;

public interface BBBMeetingService extends EntityProducer {
    
    /** This string can be used to find the service in the service manager. */
    public static final String APPLICATION_ID = "sakai:bbb-meeting";
    
    public static final String EVENT_BBBMEETING_POST_NEW = "bbb-meeting.post.new";
    
    public static final String EVENT_BBBMEETING_POST_CHANGE = "bbb-meeting.post.change";
    
    //for adding more logging info and not send out email notification
    public static final String EVENT_BBBMEETING_DELETE_POST = "bbb-meeting.delete";
    
    public static final String EVENT_BBBMEETING_READ = "bbb-meeting.read";

    public static final String EVENT_BBBMEETING_DRAFT_NEW = "bbb-meeting.draft.new";
    
    public static final String EVENT_BBBMEETING_DRAFT_CHANGE = "bbb-meeting.draft.change";

    public static final String REFERENCE_ROOT = Entity.SEPARATOR + "bbb-meeting";
    
    public static final String BBBMEETING_SERVICE_NAME = "org.sakaiproject.bbb.api.BBBMeetingService";
    
    //permission convert
    public static final String PERMISSION_UPDATE = "bbb-meeting.update"; 
    
/*
    public void postNewSyllabus(SyllabusData data);
    
    public void postChangeSyllabus(SyllabusData data);
    
    public void deletePostedSyllabus(SyllabusData data);
    
    public void readSyllabus(SyllabusData data);
    
    public void draftNewSyllabus(SyllabusData data);
    
    public void draftChangeSyllabus(SyllabusData data);
    
    public List getMessages(String id);
    
    public void importEntities(String fromSiteId, String toSiteId, List resourceIds);
    
    //permission convert
    public String getEntityReference(SyllabusData sd, String thisSiteId);
    
    public String getSyllabusApplicationSiteReference(String thisSiteId);
    
    public boolean checkPermission(String lock, String reference);
*/

}
