package org.sakaiproject.bbb.tool.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.Map.Entry;

import lombok.Getter;
import lombok.Setter;

import org.apache.log4j.Logger;

import org.sakaiproject.bbb.api.BBBMeeting;
import org.sakaiproject.bbb.api.BBBMeetingManager;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.entity.api.ContextObserver;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.id.api.IdManager;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.cover.ToolManager;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BBBMeetingEntityProducer implements EntityProducer, EntityTransferrer, ContextObserver {

    private Logger logger = Logger.getLogger(BBBMeetingEntityProducer.class);
    //private final Logger logger = Logger.getLogger(getClass());

    //private static BBBMeetingEntityProducer entityProducer = null; 
    
    private static final String ARCHIVE_VERSION = "1.0.7"; // in case new features are added in future exports
    private static final String VERSION_ATTR = "version";
    private static final String NAME = "name";
    private static final String VALUE = "value";
    
    private static final String PROPERTIES = "properties";
    private static final String PROPERTY = "property";
    public static final String REFERENCE_ROOT = Entity.SEPARATOR + "bbb-tool";
    public static final String APPLICATION_ID = "sakai.bbb-tool";
    public static final String APPLICATION = "bbb-tool";
    public static final String ATTR_TOP_REFRESH = "sakai.vppa.top.refresh";
    

    @Setter @Getter
    private BBBMeetingManager meetingManager;
    
    public void init()
    {
        logger.debug(APPLICATION + " init()");
        
        try {
            EntityManager.registerEntityProducer(this, REFERENCE_ROOT);   
         }
         catch (Exception e) {
            logger.warn("Error registering " + APPLICATION + " Entity Producer", e);
         }

         try {
             ComponentManager.loadComponent("org.sakaiproject.bbb.tool.entity.BBBMeetingEntityProducer", this);
         } catch (Exception e) {
             logger.warn("Error registering " + APPLICATION + " Entity Producer with Spring. " + APPLICATION + " will work, but " + APPLICATION + " tools won't be imported from site archives. This normally happens only if you redeploy " + APPLICATION + ". Suggest restarting Sakai", e);
         }
        
    }

    public void destroy()
    {
        logger.debug("destroy");

    }

    /**
     * Get the service name for this class
     * @return
     */
    protected String serviceName() {
       return BBBMeetingEntityProducer.class.getName();
    }

    
    // EntityProducer implementation
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
    public Collection getEntityAuthzGroups(Reference ref, String userId)
    {
       //TODO implement this
       return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getEntityUrl(Reference ref)
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
    public ResourceProperties getEntityResourceProperties(Reference ref)
    {
        return null;
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
    public boolean parseEntityReference(String reference, Reference ref)
    {
        // not for the moment
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sakaiproject.service.legacy.entity.ResourceService#merge(java.lang.String,
     *      org.w3c.dom.Element, java.lang.String, java.lang.String, java.util.Map, java.util.HashMap,
     *      java.util.Set)
     */
    public String merge(String siteId, Element root, String archivePath, String fromSiteId, Map attachmentNames, Map userIdTrans,
            Set userListAllowImport)
    {
        logger.debug("trying to merge " + APPLICATION);

        return null;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.sakaiproject.service.legacy.entity.ResourceService#archive(java.lang.String,
     *      org.w3c.dom.Document, java.util.Stack, java.lang.String,
     *      org.sakaiproject.service.legacy.entity.ReferenceVector)
     */
    public String archive(String siteId, Document doc, Stack stack, String arg3,
            List attachments)
    {
        //prepare the buffer for the results log
        StringBuilder results = new StringBuilder();

        try {
            Site site = SiteService.getSite(siteId);
            // start with an element with our very own (service) name         
            Element element = doc.createElement(serviceName());
            element.setAttribute(VERSION_ATTR, ARCHIVE_VERSION);
            ((Element) stack.peek()).appendChild(element);
            stack.push(element);

            Element linktool = doc.createElement(APPLICATION);
            Collection<ToolConfiguration> tools = site.getTools(myToolIds());
            if (tools != null && !tools.isEmpty()) {
                for (ToolConfiguration config: tools) {
                    element = doc.createElement(APPLICATION);

                    Attr attr = doc.createAttribute("toolid");
                    attr.setValue(config.getToolId());
                    element.setAttributeNode(attr);

                    attr = doc.createAttribute("name");
                    attr.setValue(config.getContainingPage().getTitle());
                    element.setAttributeNode(attr);

                    Properties props = config.getConfig();
                    if (props == null)
                        continue;

                    String url = props.getProperty("url", null);
                    if (url == null && props != null) {
                        String urlProp = props.getProperty("urlProp", null);
                        if (urlProp != null) {
                            url = ServerConfigurationService.getString(urlProp);
                        }
                    }

                    attr = doc.createAttribute("url");
                    attr.setValue(url);
                    element.setAttributeNode(attr);

                    String height = "600";
                    String heights =  props.getProperty("height", "600");
                    if (heights != null) {
                        heights = heights.trim();
                        if (heights.endsWith("px"))
                            heights = heights.substring(0, heights.length()-2).trim();
                        height = heights;
                    }

                    attr = doc.createAttribute("height");
                    attr.setValue(height);
                    element.setAttributeNode(attr);


                    linktool.appendChild(element);
                }
          
                results.append("archiving " + getLabel() + ": (" + tools.size() + ") " + APPLICATION + " instances archived successfully.\n");
             
            } else {
                results.append("archiving " + getLabel()
                   + ": no " + APPLICATION + " tools.\n");
            }

            ((Element) stack.peek()).appendChild(linktool);
            stack.push(linktool);

            stack.pop();
        } catch (Exception any) {
            logger.warn("archive: exception archiving service: " + serviceName());
        }

        stack.pop();

        return results.toString();
        
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sakaiproject.service.legacy.entity.ResourceService#getLabel()
     */
    public String getLabel()
    {
      return APPLICATION;
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


    //EntityTransferrer implementation
    /**
     * {@inheritDoc}
     */
    public String[] myToolIds()
    {
        String[] toolIds = { APPLICATION_ID, "sakai.bbb" };
        return toolIds;
    }
    
    /**
     * {@inheritDoc}
     */
    public void transferCopyEntities(String fromContext, String toContext, List ids)
    {
        logger.debug("transferCopyEntities");
        try{
            List<BBBMeeting> meetings = meetingManager.getSiteMeetings(fromContext);
            for (BBBMeeting meeting : meetings) {
                meeting.setId(null);
                meeting.setSiteId(toContext);
                meetingManager.databaseStoreMeeting(meeting);
            }
        } catch( Exception e) {
            logger.debug("Exception occurred " + e);

        }
        
    }
    
    /**
     * {@inheritDoc}
     */
    public void transferCopyEntities(String fromContext, String toContext, List ids, boolean cleanup)
    {
        try {
            if(cleanup == true) {
                List<BBBMeeting> meetings = meetingManager.getSiteMeetings(toContext);
                for (BBBMeeting meeting : meetings) {
                    meetingManager.databaseDeleteMeeting(meeting);
                }
                 
            } 
            
            transferCopyEntities(fromContext, toContext, ids);
        
        } catch (Exception e) {
            logger.info("WebContent transferCopyEntities Error" + e);
        }
    }

    /// ContextObserver implementation
    public void contextCreated(String context, boolean toolPlacement){
        
    }

    public void contextUpdated(String context, boolean toolPlacement){
        
    }

    public void contextDeleted(String context, boolean toolPlacement){
        //Delete meetings
        try {
            List<BBBMeeting> meetings = meetingManager.getSiteMeetings(context);
            for (BBBMeeting meeting : meetings) {
                meetingManager.databaseDeleteMeeting(meeting);
            }
        } catch (Exception e) {
            logger.info(APPLICATION + " contextDeleted Error: " + e);
        }
        
    }

}
