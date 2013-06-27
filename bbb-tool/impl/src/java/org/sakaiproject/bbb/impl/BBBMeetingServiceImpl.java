package org.sakaiproject.bbb.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.Map.Entry;

import lombok.Getter;
import lombok.Setter;

import org.apache.log4j.Logger;

import org.sakaiproject.bbb.api.BBBMeetingManager;
import org.sakaiproject.bbb.api.BBBMeetingService;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.EntityTransferrerRefMigrator;
import org.sakaiproject.entity.cover.EntityManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


public class BBBMeetingServiceImpl implements BBBMeetingService, EntityTransferrer, EntityTransferrerRefMigrator {

    private static final String BBBMEETING = "bbb-tool";
    
    private Logger logger = Logger.getLogger(BBBMeetingServiceImpl.class);

    @Setter @Getter
    private BBBMeetingManager meetingManager;
    
    public void init()
    {
        logger.debug("DEBUGTAG: " + "init.1");
        
        EntityManager.registerEntityProducer(this, REFERENCE_ROOT);   

    }

    public void destroy()
    {
        logger.debug("DEBUGTAG: " + "destroy");

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
    //permission convert
        Collection rv = new Vector();

        try
        {
            logger.info("DEBUGTAG: " + ref.getSubType());
            
            if (BBBMEETING.equals(ref.getSubType()))
            {
                rv.add(ref.getReference());
                
                ref.addSiteContextAuthzGroup(rv);
            }
        }
        catch (Exception e) 
        {
            logger.error("BBBMeetingServiceImpl:getEntityAuthzGroups - " + e);
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
        if (reference.startsWith(REFERENCE_ROOT))
        {
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
     * from StringUtil.java
     */
    protected String[] split(String source, String splitter)
    {
        // hold the results as we find them
        Vector rv = new Vector();
        int last = 0;
        int next = 0;
        do
        {
            // find next splitter in source
            next = source.indexOf(splitter, last);
            if (next != -1)
            {
                // isolate from last thru before next
                rv.add(source.substring(last, next));
                last = next + splitter.length();
            }
        }
        while (next != -1);
        if (last < source.length())
        {
            rv.add(source.substring(last, source.length()));
        }

        // convert to array
        return (String[]) rv.toArray(new String[rv.size()]);

    } // split

    /*
     * (non-Javadoc)
     * 
     * @see org.sakaiproject.service.legacy.entity.ResourceService#merge(java.lang.String,
     *      org.w3c.dom.Element, java.lang.String, java.lang.String, java.util.Map, java.util.HashMap,
     *      java.util.Set)
     */
    public String merge(String siteId, Element root, String archivePath,
            String fromSiteId, Map attachmentNames, Map userIdTrans,
            Set userListAllowImport)
    {
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
        return null;
        
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sakaiproject.service.legacy.entity.ResourceService#getLabel()
     */
    public String getLabel()
    {
      return "bbb-tool";
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
        String[] toolIds = { "sakai.bbb-tool" };
        return toolIds;
    }
    
    /**
     * {@inheritDoc}
     */
    public void transferCopyEntities(String fromContext, String toContext, List ids){
        logger.debug("bbb-tool transferCopyEntities");
        logger.debug("transferCopyEntities(String fromContext, String toContext, List ids)");


        //transferCopyEntitiesRefMigrator(fromContext, toContext, ids);
    }
    
    public Map<String, String> transferCopyEntitiesRefMigrator(String fromContext, String toContext, List<String> ids) 
    {
        Map<String, String> transversalMap = new HashMap<String, String>();

        return transversalMap;
    }

    /**
     * {@inheritDoc}
     */
    public void transferCopyEntities(String fromContext, String toContext, List ids, boolean cleanup){
        logger.debug("bbb-tool transferCopyEntities");
        logger.debug("transferCopyEntities(String fromContext, String toContext, List ids, boolean cleanup)");
        
        //transferCopyEntitiesRefMigrator(fromContext, toContext, ids, cleanup);
    }

    public Map<String, String> transferCopyEntitiesRefMigrator(String fromContext, String toContext, List<String> ids, boolean cleanup)
    {   
        Map<String, String> transversalMap = new HashMap<String, String>();
        
        return transversalMap;
    }


    //EntityTransferrerRefMigrator implementation
    /**
     * {@inheritDoc}
     */
    public void updateEntityReferences(String toContext, Map<String, String> transversalMap){         
        if(transversalMap != null && transversalMap.size() > 0){
            Set<Entry<String, String>> entrySet = (Set<Entry<String, String>>) transversalMap.entrySet();       
        }
    }


}
