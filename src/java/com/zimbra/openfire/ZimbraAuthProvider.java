package com.zimbra.openfire;

import java.lang.reflect.InvocationTargetException;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zimbra.common.soap.SoapHttpTransport;
import com.zimbra.soap.account.message.AuthRequest;
import com.zimbra.soap.account.type.AuthToken;
import com.zimbra.soap.type.AccountSelector;
import com.zimbra.soap.util.ZimbraSoapUtils;
/**
 * @author Greg Solovyev
 */
public class ZimbraAuthProvider implements AuthProvider {
    private static final Logger Log = LoggerFactory.getLogger(ZimbraAuthProvider.class);
    private String soapServerProto = "http";
    private String soapServerPort = "7070";
    private String soapServerHost = "localhost";
    private String soapServerPath = "/service/soap/";
    private SoapHttpTransport mTransport;
    private String fallbackProviderClass = null;
    private boolean fallback = true;
    private AuthProvider fallbackProvider;

    public ZimbraAuthProvider() {
        Log.info("Initialized ZimbraAuthProvider ");
        soapServerPort = JiveGlobals.getProperty("zimbraAuthProvider.port", "443");
        soapServerHost = JiveGlobals.getProperty("zimbraAuthProvider.host");
        soapServerPath = JiveGlobals.getProperty("zimbraAuthProvider.path", "/service/soap/");
        soapServerProto = JiveGlobals.getProperty("zimbraAuthProvider.protocol", "https");
        String zimbraURL = soapServerProto.concat("://").concat(soapServerHost)
                .concat(":").concat(soapServerPort).concat(soapServerPath);
        mTransport = new SoapHttpTransport(zimbraURL);
        fallback = JiveGlobals.getBooleanProperty("zimbraAuthProvider.fallback", true);
        if(fallback) {
            fallbackProviderClass = JiveGlobals.getProperty("zimbraAuthProvider.fallbackProviderClass",
                    "org.jivesoftware.openfire.auth.DefaultAuthProvider");
            try {
                Class<?> providerClass = Class.forName(fallbackProviderClass);
                fallbackProvider = (AuthProvider) providerClass.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                Log.error("Failed to instantiate fallback provider", e);
            }
        }
        Log.info("Initialized ZimbraAuthProvider with connection string: " + zimbraURL);
    }

    public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException,
            InternalUnauthenticatedException {
        Log.debug("Authenticating " + username + "/" + password);
        if (username == null || password == null) {
            throw new UnauthorizedException("Empty username or password");
        }
        String zimbraName;
        if(username.indexOf("@") < 0) {
            zimbraName = username + "@" + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        } else {
            zimbraName = username;
        }
        AuthRequest req = new AuthRequest();
        if (password.length() > 100
                && password.substring(0, 10).equalsIgnoreCase("__zmauth__")) {
            // auth with auth token
            req.setAuthToken(new AuthToken(password.substring(10), true));
        } else {
            // auth with password
            req.setPassword(password);
        }
        req.setAccount(AccountSelector.fromName(zimbraName));
        try {
            // if auth fails, this will throw an exception
            ZimbraSoapUtils.invokeJaxb(mTransport, req);
            Log.debug("Successfully authenticated user " + zimbraName + " to Zimbra SOAP API");
        } catch (Exception e) {
            if(fallback && fallbackProvider != null) {
                Log.error("Failed to authenticate " + zimbraName + " via Zimbra SOAP API. Falling back to " + fallbackProviderClass);
                fallbackProvider.authenticate(username, password);
            } else {
                throw new UnauthorizedException("Failed to authenticate " + username + " via Zimbra SOAP API.", e);
            }
        }
    }

    public void authenticate(String arg0, String arg1, String arg2)
            throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
        if(fallback && fallbackProvider != null) {
            Log.debug("Digest authentication not supported by Zimbra SOAP API. Falling back to " + fallbackProviderClass);
            fallbackProvider.authenticate(arg0, arg1,arg2);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public String getPassword(String arg0) throws UserNotFoundException,
            UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public boolean isDigestSupported() {
        return false;
    }

    public boolean isPlainSupported() {
        return true;
    }

    public void setPassword(String arg0, String arg1)
            throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}
