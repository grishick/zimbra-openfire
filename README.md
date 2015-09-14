Overview
--------

A video walk-through of these instructions is available here:
<https://www.youtube.com/watch?v=kFGahQvL9Gk> These instructions are
part of the set of instructions for setting up ZCS Chat feature:
<http://wiki.eng.zimbra.com/index.php/ZimbraIM/> These instructions were
tested with OpenFire 3.10.2

Download and install OpenFire
-----------------------------

OpenFire is an XMPP server written in Java. It can be downloaded from
Igniterealtime's website
<http://www.igniterealtime.org/projects/openfire/>. Follow OpenFire
installation instructions with all default options.

Install Zimbra OpenFire plugin to OpenFire
------------------------------------------

Authentication against Zimbra SOAP interface is implemented by a custom
java library (ZimbraOpenFire). The source code is in
ZimbraFOSS/ZimbraOpenFire project. You need the following jar files that
are produced by “dist” ANT target in ZimbraOpenFire project:

-   zimbraopenfire.jar
-   zimbrasoap.jar
-   zimbracommon.jar
-   json.jar
-   guava-13.0.1.jar

Copy these jar files to OpenFire's lib folder (on Mac it is
<b>/usr/local/openfire/lib</b>). On Mac, you have to copy these files as
root. Restart OpenFire.

Configure Zimbra authentication for OpenFire
--------------------------------------------

Open OpenFire admin UI on port 9090 (http://your-open-fire-host:9090).
You should be able to log in with default admin credentials that you
created during installation.

### Change provider classes

Navigate to “Server”/“System Properties” and change the following
settings:

-   Set <b>provider.auth.className</b> to
    <i>com.zimbra.openfire.ZimbraAuthProvider</i>
-   Set <b>provider.user.className</b> to
    <i>com.zimbra.openfire.ZimbraUserProvider</i>

This is what System Properties section looks like in OpenFire Admin UI:
![]

### Set custom properties for Zimbra authentication

Still in “System Properties” add the following settings that will be
used by ZimbraAuthProvider and ZimbraUserProvider classes to talk to
Zimbra SOAP interface.

-   Set <b>zimbraAuthProvider.host</b> to the hostname or IP address of
    your ZCS mailbox server
-   Set <b>zimbraAuthProvider.port</b> to your the same port number
    where your Zimbra users access Zimbra Web Client. If your web MTA is
    running in HTTP only mode, set <b>zimbraAuthProvider.port</b> to
    <i>80</i>. If you are not running zmproxy, set
    <b>zimbraAuthProvider.port</b> to <i>7070</i>. If your web MTA
    allows connections over HTTPS, set <b>zimbraAuthProvider.port</b> to
    <i>443</i>.
-   Set <b>zimbraAuthProvider.protocol</b> to <i>http</i> if your Zimbra
    Web Client is running on HTTP. Set
    <b>zimbraAuthProvider.protocol</b> to <i>https</i> if your Zimbra
    Web Client is running on HTTPS.
-   Set <b>zimbraUserProvider.host</b> to the hostname or IP address of
    your ZCS mailbox server

<li>
Set <b>zimbraUserProvider.adminLogin</b> to your ZCS admin's user name.
This does not have to be a global admin. This admin user has to have
permissions to search and load

  []: OpenFireSystemProps.png "fig:OpenFireSystemProps.png"