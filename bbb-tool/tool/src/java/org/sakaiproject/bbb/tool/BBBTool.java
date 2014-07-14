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

package org.sakaiproject.bbb.tool;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Bootstraps the bbb tool by redirecting to the start page, bbb.html and
 * supplying some basic stuff like site id and language via url params.
 * 
 * @author Adrian Fish
 */
public class BBBTool extends HttpServlet {
    private static final long serialVersionUID = 2801227086525605150L;

    private Logger logger = Logger.getLogger(BBBTool.class);

    private transient SakaiProxy sakaiProxy;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        if (logger.isDebugEnabled())
            logger.debug("init");

        try {
            sakaiProxy = SakaiProxy.getInstance();
        } catch (Throwable t) {
            throw new ServletException("Failed to initialise BBBTool servlet.", t);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (logger.isDebugEnabled())
            logger.debug("doGet()");

        // check if Sakai proxy was successfully initialized
        if (sakaiProxy == null)
            throw new ServletException("sakaiProxy MUST be initialized.");

        // check if user is logged in
        if (sakaiProxy.getCurrentUser() == null)
            throw new ServletException("You must be logged in to use this tool.");

        // check site permissions
        sakaiProxy.checkPermissions();

        // parameters
        String state = request.getParameter("state");
        String meetingId = request.getParameter("meetingId");
        if (state == null)
            state = "currentMeetings";

        // build url
        StringBuilder url = new StringBuilder("/bbb-tool/bbb.html?");
        url.append("&language=").append(sakaiProxy.getUserLanguageCode());
        url.append("&siteId=").append(sakaiProxy.getCurrentSiteId());
        url.append("&skin=").append(sakaiProxy.getSakaiSkin());
        url.append("&state=").append(state);
        url.append("&timestamp=").append(sakaiProxy.getServerTimeInUserTimezone());
        url.append("&timezoneoffset=").append(sakaiProxy.getUserTimezoneOffset());
        url.append("&timezone=").append(sakaiProxy.getUserTimezone());
        url.append("&version=").append(sakaiProxy.getSakaiVersion());
        if (meetingId != null)
            url.append("&meetingId=").append(meetingId);

        logger.debug("doGet(): " + url.toString());
        
        // redirect...
        response.sendRedirect(url.toString());
    }
}
