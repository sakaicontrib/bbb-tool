package org.sakaiproject.bbb.impl;

import java.util.Collection;
import java.util.Vector;

import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.api.BBBMeetingService;

import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.Edit;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.EntityTransferrerRefMigrator;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.NotificationEdit;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.event.cover.EventTrackingService;
import org.sakaiproject.exception.IdUnusedException;
//permission convert
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;

import org.sakaiproject.site.cover.SiteService;
/*
import org.sakaiproject.site.tool.SiteAction;
*/
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.BaseResourcePropertiesEdit;
import org.sakaiproject.util.Validator;

//permission convert
import org.sakaiproject.authz.cover.SecurityService;


public class BBBMeetingServiceImpl implements BBBMeetingService { //, EntityTransferrer, EntityTransferrerRefMigrator {

    private static final String BBBMEETING = "bbb-meeting";
    private static final String BBBMEETING_ID = "id";
    private static final String BBBMEETING_USER_ID = "userID";
    private static final String BBBMEETING_REDIRECT_URL = "redirectUrl";
    private static final String BBBMEETING_CONTEXT_ID = "contextId";
    
    
    private static final String BBBMEETING_DATA = "syllabus_data";
    private static final String BBBMEETING_DATA_TITLE = "title";
    private static final String BBBMEETING_DATA_POSITION = "position";
    private static final String BBBMEETING_DATA_VIEW = "view";
    private static final String BBBMEETING_DATA_ID = "syllabus_id";
    private static final String BBBMEETING_DATA_EMAIL_NOTIFICATION = "emailNotification";
    private static final String BBBMEETING_DATA_STATUS = "status";
    private static final String BBBMEETING_DATA_ASSET = "asset";
    private static final String BBBMEETING_ATTACHMENT = "attachment";
    
    private static final String PAGE_ARCHIVE = "pageArchive";
    private static final String SITE_NAME = "siteName";
    private static final String SITE_ID = "siteId";
    private static final String SITE_ARCHIVE = "siteArchive";
    private static final String PAGE_NAME = "pageName";
    private static final String PAGE_ID = "pageId";
    /** Dependency: a BBBMeetingManager. */
    private BBBMeetingManager bbbMeetingManager;
   
    /** Dependency: a logger component. */
    //private Log logger = LogFactory.getLog(SyllabusServiceImpl.class);
     
    protected NotificationService notificationService = null;
    protected String m_relativeAccessPoint = null;
    
  //sakai2 -- add init and destroy methods  
      public void init()
      {
        
        m_relativeAccessPoint = REFERENCE_ROOT;
        
        NotificationEdit edit = notificationService.addTransientNotification();
        
        edit.setFunction(EVENT_BBBMEETING_POST_NEW);
        edit.addFunction(EVENT_BBBMEETING_POST_CHANGE);
        edit.addFunction(EVENT_BBBMEETING_DELETE_POST);
        edit.addFunction(EVENT_BBBMEETING_READ);
        edit.addFunction(EVENT_BBBMEETING_DRAFT_NEW);
        edit.addFunction(EVENT_BBBMEETING_DRAFT_CHANGE);
        
        //edit.setResourceFilter(getAccessPoint(true));
        
        //edit.setAction(new SiteEmailNotificationSyllabus());

        EntityManager.registerEntityProducer(this, REFERENCE_ROOT);   
      }

      public void destroy()
      {
      }

      
      
      /**
       * {@inheritDoc}
       */
      public boolean willArchiveMerge()
      {
          return true;
      }

      /**
       * {@inheritDoc}
       */
      public boolean willImport()
      {
          return true;
      }

      /**
       * {@inheritDoc}
       */
      public HttpAccess getHttpAccess()
      {
          return null;
      }

      /**
       * {@inheritDoc}
       */
      public boolean parseEntityReference(String reference, Reference ref)
      {
          if (reference.startsWith(REFERENCE_ROOT))
          {
              // /syllabus/siteid/syllabusid
              String[] parts = split(reference, Entity.SEPARATOR);

              String subType = null;
              String context = null;
              String id = null;
              String container = null;

              // the first part will be null, then next the service, the third will be "calendar" or "event"
              if (parts.length > 2)
              {
                  // the site/context
                  context = parts[2];

                  // the id
                  if (parts.length > 3)
                  {
                      id = parts[3];
                  }
              }

              ref.set(APPLICATION_ID, subType, id, container, context);

              return true;
          }

          return false;
      }

      /**
       * {@inheritDoc}
       */
      public String getEntityDescription(Reference ref)
      {
          return null;
      }
      
      /**
       * {@inheritDoc}
       */
      public ResourceProperties getEntityResourceProperties(Reference ref)
      {
          return null;
      }

      /**
       * {@inheritDoc}
       */
      public Entity getEntity(Reference ref)
      {
          return null;
      }

      /**
       * {@inheritDoc}
       */
      public Collection getEntityAuthzGroups(Reference ref, String userId)
      {
      //permission convert
          Collection rv = new Vector();

          try
          {
              if (BBBMEETING.equals(ref.getSubType()))
              {
                  rv.add(ref.getReference());
                  
                  ref.addSiteContextAuthzGroup(rv);
              }
          }
          catch (Exception e) 
          {
              logger.error("SyllabusServiceImpl:getEntityAuthzGroups - " + e);
              e.printStackTrace();
          }

          return rv;

      }

      /**
       * {@inheritDoc}
       */
      public String getEntityUrl(Reference ref)
      {
          return null;
      }
      
}
