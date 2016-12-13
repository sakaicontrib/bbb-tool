# BigBlueButton Sakai Meeting Tool
BigBlueButton is an open source web conferencing system that enables universities and colleges to deliver a high-quality learning experience to remote students.

These instructions describe how to install the BigBlueButton Meeting Tool for Sakai 11.

#### *With this plugin you can*
- Control meetings - create/edit/update/delete BBB meetings from Sakai
- Meeting access - define meeting access by selective all users, groups, roles or individual users in site
- Tool access - define who can do what on the Sakai tool
- Scheduling - optionally, define meeting start dates and/or end dates and add it to site Calender
- Notification - optionally, send an email notification to meeting participants
- Simplicity - the user interface is designed to be simple
- Fast - the Ajax driven interface (Javascript + JSON + Trimpath templates) provides good end-user experience and low server load
- RESTful - full RESTful support via EntityBroker
- Statistics - the tool logs information automatically processed by the Site Stats tool

## Prerequisites
You need:

1. A server running Sakai 2.5+
2. A BigBlueButton 0.8 (or later) server running on a separate server (not on the same server as your Sakai site)
	
Blindside Networks provides you a test BigBlueButton server for testing this plugin.  To use this test server, just accept the default settings when configuring the activity module.  The default settings are
```
url: http://test-install.blindsidenetworks.com/bigbluebutton/
salt: 8cd8ef52e8e101574e400365b55e11a6
```
For information on how to setup your own BigBlueButton server see

http://bigbluebutton.org/
   
## Obtaining the source
This GitHub repostiory at

https://github.com/sakaicontrib/bbb-tool

contains the latest source.  If you want to use the latest packaged snapshot, you can download it from the corresponding branch.

https://github.com/sakaicontrib/bbb-tool/tree/11.x

## Installation
These instructions assume your Sakai server is installed at /opt/tomcat.

1.  Meet requirements

  1.1. For Sakai 2.5 Add contextId (siteId) field to Event. You can follow these instructions https://confluence.sakaiproject.org/display/STAT/SiteStats+and+SAK-10801

  1.2. Upgrade to EntityBroker 1.3.9 or higher (Sakai 2.5.x and Sakai 2.6.x only)

  1.3. Upgrade Maven to 2.2.1 or higher


2.  Install BigBlueButton Sakai Meeting Tool

  2.1. Download the tool source code
  ```
    cd ~/YOUR-SAKAI-SRC         
    git clone git://github.com/sakaicontrib/bbb-tool.git
  ```

  2.2. Compile & deploy to Tomcat
  ```
    cd bbb-tool
    mvn -Dmaven.tomcat.home={tomcat_folder} clean install sakai:deploy
  ```

  2.3. Add/review the following entries to the end of your sakai.properties file placed in the /opt/tomcat/sakai/ directory
  ```
    bbb.url=http://<server>/bigbluebutton
    bbb.salt=<salt>
  ```   
  To determine these values for your BigBlueButton server, enter the command
  ```
    bbb-conf --salt
  ```
  If you want to use the public test server for BigBlueButton, use the following settings 
  ```
    bbb.url=http://test-install.blindsidenetworks.com/bigbluebutton   
    bbb.salt=8cd8ef52e8e101574e400365b55e11a6
  ```

  2.4. Create the database schema

  Run the schema creation script that corresponds to your database (MySQL or Oracle)
    
  or
    
  Set up `auto.ddl=true` into sakai.properties and restart your server  


At this point, you can activate the meeting tool to any site and start adding meetings.
