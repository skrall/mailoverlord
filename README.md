# Mailoverlord [![Build Status](https://travis-ci.org/skrall/mailoverlord.png?branch=master)](https://travis-ci.org/skrall/mailoverlord) [ ![Codeship Status for skrall/mailoverlord](https://www.codeship.io/projects/eceee630-0b97-0132-82a9-6695a14f90f5/status)](https://www.codeship.io/projects/32032)

Mailoverlord is a SMTP mail server targeted for use in testing / QA environments.  Using Mailoverlord,
an application can be moved from dev, test, qa, production without using test email addresses,
or flags to disable the sending of email.  Just configure Mailoverlord as your application's SMTP server,
and it will receive the emails, but instead of actually delivering them, it will store them in a database.  The emails
 can later be released to their original recipients, or released to a different email address(es).  This can enable
 the testers to view what your production users would have received.

### Requirements

* Java 7
* A Sql Datastore - Postgres, Mysql, Oracle, any database Hibernate supports.
* Servlet Container - Tomcat, Jetty.  It should also run fine on application servers Glassfish, JBoss etc.
* SMTP Server - to release the emails to.  If you aren't going to release emails - this isn't required.

### Installation

1. Configure the servlet container's JNDI data source.  The datasource should named jdbc/overlord-datasource
2. Configure the servlet containers JNDI Session to send released emails to.  It should be named mail/overlord-mail
3. Install the mailoverlord.war

You should configure your application to send it's emails to the Mailoverlord SMTP server.  The settings would be the
 hostname where the servlet container is running, the port is 2025.

