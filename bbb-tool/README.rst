BigBlueButton Sakai Meeting Tool for Sakai 2.5/2.6/2.7/2.8/2.9
==============================================================
BigBlueButton is an open source web conferencing system that enables universities and colleges to deliver a high-quality learning experience to remote students.  

These instructions describe how to install the BigBlueButton Meeting Tool for Sakai 2.5/2.6/2.7/2.8/2.9.

With this plugin you can
    - Control meetings - create/edit/update/delete BBB meetings from Sakai.
    - Meeting access - define meeting access by selecting all users, groups, roles or individual users in site.
    - Tool access - define who can do what on the Sakai tool.
    - Scheduling - optionally, define meeting start dates and/or end dates and add it to site Calendar.
    - Notification - optionally, send an email notification to meeting participants.
    - Simplicity - the user interface is designed to be simple.
    - Fast - the Ajax driven interface (Javascript + JSON + Trimpath templates) provides good end-user experience and low server load.
    - RESTful - full RESTful support via EntityBroker.
    - Statistics - the tool logs information automatically processed by the Site Stats tool.

Prerequisites
=============
You need:

	1.  A server running Sakai 2.5+ with entitybroker 1.3.9+
	2.  A BigBlueButton 0.8-beta-4 (or later) server running on a separate server (not on the same server as your Sakai site)
	
Blindside Networks provides you a test BigBlueButton server for testing this plugin.  To use this test server, just accept the default settings when configuring the activity module.  The default settings are

	url: http://test-install.blindsidenetworks.com/bigbluebutton/

	salt: 8cd8ef52e8e101574e400365b55e11a6

For information on how to setup your own BigBlueButton server see

   http://bigbluebutton.org/
   
Obtaining the source
====================
This GitHub repostiory at

  https://github.com/blindsidenetworks/bigbluebutton-sakai

contains the latest source.  If you want to use the latest packaged snapshot, you can download it from the corresponding branch.

  https://github.com/blindsidenetworks/bigbluebutton-sakai/tree/1.0.7-rc1-SNAPSHOT


Installation
============

These instructions assume your Sakai server is installed at /opt/tomcat.

1.  Meet requirements

  1.1. For Sakai 2.5 Add contextId (siteId) field to Event. You can follow these instructions https://confluence.sakaiproject.org/display/STAT/SiteStats+and+SAK-10801

  1.2. Upgrade to EntityBroker 1.3.9 or higher (Sakai 2.5.x and Sakai 2.6.x only)

  1.3. Upgrade Maven to 2.2.1 or higher


2.  Install BigBlueButton Sakai Meeting Tool

  2.1. Download the tool source code

         cd ~/
         
         git clone git://github.com/blindsidenetworks/bigbluebutton-sakai.git


  2.2. Compile & deploy to Tomcat
 
         cd bigbluebutton-sakai/bbb-tool
         
         mvn -Dmaven.tomcat.home={tomcat_folder} clean install sakai:deploy
    

  2.3. Add/review the following entries to the end of your sakai.properties file placed in the /opt/tomcat/sakai/ directory

         bbb.url=http://<server>/bigbluebutton
         
         bbb.salt=<salt>
       
       To determine these values for your BigBlueButton, enter the command
    
         bbb-conf --salt
    
       If you want to use the public test server for BigBlueButton, use the following settings 

         bbb.url=http://test-install.blindsidenetworks.com/bigbluebutton
         
         bbb.salt=8cd8ef52e8e101574e400365b55e11a6 

  2.4. Create the database schema

       Run the schema creation script that corresponds to your database (MySQL or Oracle)
    
       or
    
       Set up auto.ddl=true into sakai.properties and restart your server  


At this point, you can activate the meeting tool to any site and start adding meetings.


