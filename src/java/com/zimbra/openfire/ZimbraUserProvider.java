package com.zimbra.openfire;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zimbra.common.account.ZAttrProvisioning;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.SoapHttpTransport;
import com.zimbra.common.util.DateUtil;
import com.zimbra.soap.admin.message.CountAccountRequest;
import com.zimbra.soap.admin.message.CountAccountResponse;
import com.zimbra.soap.admin.message.GetAccountRequest;
import com.zimbra.soap.admin.message.GetAccountResponse;
import com.zimbra.soap.admin.message.GetAllAccountsRequest;
import com.zimbra.soap.admin.message.GetAllAccountsResponse;
import com.zimbra.soap.admin.message.SearchDirectoryRequest;
import com.zimbra.soap.admin.message.SearchDirectoryResponse;
import com.zimbra.soap.admin.type.AccountInfo;
import com.zimbra.soap.admin.type.Attr;
import com.zimbra.soap.admin.type.CosCountInfo;
import com.zimbra.soap.admin.type.DomainSelector;
import com.zimbra.soap.type.AccountSelector;
import com.zimbra.soap.util.ZimbraSoapUtils;
/**
 * @author Greg Solovyev
 */
public class ZimbraUserProvider implements UserProvider {
    private static final Logger Log = LoggerFactory.getLogger(ZimbraUserProvider.class);
    private String soapServerProto = "https";
    private String soapServerPort = "7071";
    private String soapServerHost = "localhost";
    private String soapServerPath = "/service/admin/soap/";
    private String adminLogin = "admin";
    private String adminPassword = "test123";
    private Integer userSearchLimit = 100;
    private String fallbackProviderClass = null;
    private boolean fallback = true;
    private SoapHttpTransport mTransport;
    private UserProvider fallbackProvider; //encapsulate fallback provider

    public ZimbraUserProvider() {
        Log.info("Initialized zimbraUserProvider ");
        soapServerPort = JiveGlobals.getProperty("zimbraUserProvider.adminPort", "7071");
        soapServerHost = JiveGlobals.getProperty("zimbraUserProvider.host");
        soapServerPath = JiveGlobals.getProperty("zimbraUserProvider.path", "/service/admin/soap/");
        soapServerProto = JiveGlobals.getProperty("zimbraUserProvider.adminProtocol", "https");
        adminLogin = JiveGlobals.getProperty("zimbraUserProvider.adminLogin");
        adminPassword = JiveGlobals.getProperty("zimbraUserProvider.adminPassword");
        userSearchLimit = JiveGlobals.getIntProperty("zimbraUserProvider.userSearchLimit", 100);
        String zimbraURL = soapServerProto.concat("://").concat(soapServerHost).concat(":").concat(soapServerPort).concat(soapServerPath);
        mTransport = new SoapHttpTransport(zimbraURL);
        //by default fallback to DefaultUserProvider to avoid locking out OpenFire admin UI
        fallback = JiveGlobals.getBooleanProperty("zimbraUserProvider.fallback", true);
        if(fallback) {
            fallbackProviderClass = JiveGlobals.getProperty("zimbraUserProvider.fallbackProviderClass",
                    "org.jivesoftware.openfire.user.DefaultUserProvider");
            try {
                Class<?> providerClass = Class.forName(fallbackProviderClass);
                fallbackProvider = (UserProvider) providerClass.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                Log.error("Failed to instantiate fallback provider", e);
            }
        }
        Log.info("Initialized ZimbraUserProvider with connection string: " + zimbraURL);
    }

    public User loadUser(String username) throws UserNotFoundException {
        Log.debug("Loading user: " + username);
        if (username == null) {
            throw new UserNotFoundException("Empty username");
        }
        String zimbraName;
        if(username.indexOf("@") < 0) {
            zimbraName = username + "@" + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        } else {
            zimbraName = username;
        }
        try {
            ZimbraSoapUtils.soapAdminAuthenticate(mTransport, adminLogin,
                    adminPassword);
            GetAccountRequest req = new GetAccountRequest(
                    AccountSelector.fromName(zimbraName));
            GetAccountResponse resp = ZimbraSoapUtils.invokeJaxb(mTransport,
                    req);
            Map<String, Object> attrsMap = Attr.collectionToMap(resp.getAccount().getAttrList());
            Date createdDate = DateUtil.parseGeneralizedTime(attrsMap.get(ZAttrProvisioning.A_zimbraCreateTimestamp).toString());
            Object name = attrsMap.get(ZAttrProvisioning.A_displayName);
            String uid = attrsMap.get(ZAttrProvisioning.A_uid).toString();
            // plugging same date for created and modified, because ZCS does not store account modification date in LDAP
            return new User(uid,
                    name == null ? username : name.toString(),
                            attrsMap.get(ZAttrProvisioning.A_mail).toString(),
                    createdDate, createdDate);
        } catch (ServiceException e) {
            if(fallback && fallbackProvider != null) {
                Log.debug("Failed to load " + zimbraName + " via Zimbra SOAP API. Falling back to " + fallbackProviderClass);
                return fallbackProvider.loadUser(username);
            } else if (e.getCode().equals("account.NO_SUCH_ACCOUNT")) {
                throw new UserNotFoundException();
            } else {
                Log.error("Failed to load " + zimbraName + " via Zimbra SOAP API. " + e.getMessage());
                throw new UserNotFoundException(e.getMessage(), e);
            }
        }
    }

    public User createUser(String username, String password, String name,
            String email) throws UserAlreadyExistsException {
        throw new UnsupportedOperationException();
    }

    public void deleteUser(String username) {
        throw new UnsupportedOperationException();
    }

    public int getUserCount() {
        int result = 0;
        try {
            ZimbraSoapUtils.soapAdminAuthenticate(mTransport, adminLogin, adminPassword);
            CountAccountRequest req = new CountAccountRequest(
                    DomainSelector.fromName(XMPPServer.getInstance()
                            .getServerInfo().getXMPPDomain()));
            CountAccountResponse resp = ZimbraSoapUtils.invokeJaxb(mTransport, req);
            for (CosCountInfo cosInfo : resp.getCos()) {
                result += (int)cosInfo.getValue();
            }
        } catch (ServiceException e) {
            Log.error(e.getMessage(), e);
            if(fallback && fallbackProvider != null) {
                Log.debug("Failed to load user count via Zimbra SOAP API. Falling back to " + fallbackProviderClass);
                return fallbackProvider.getUserCount();
            } else {
                return 0;
            }
        }
        return result;
    }

    public Collection<User> getUsers() {
        try {
            ZimbraSoapUtils.soapAdminAuthenticate(mTransport, adminLogin, adminPassword);
            GetAllAccountsRequest req = new GetAllAccountsRequest();
            req.setDomain(DomainSelector.fromName(XMPPServer.getInstance().getServerInfo().getXMPPDomain()));
            GetAllAccountsResponse resp = ZimbraSoapUtils.invokeJaxb(mTransport, req);
            return processAccountInfoList(resp.getAccountList());
        } catch (ServiceException e) {
            Log.error(e.getMessage(), e);
            if(fallback && fallbackProvider != null) {
                Log.debug("Failed to fetch all users from Zimbra SOAP API. Falling back to " + fallbackProviderClass);
                return fallbackProvider.getUsers();
            } else {
                return Collections.emptyList();
            }
        }
    }

    public Collection<String> getUsernames() {
        try {
            ZimbraSoapUtils.soapAdminAuthenticate(mTransport, adminLogin, adminPassword);
            GetAllAccountsRequest req = new GetAllAccountsRequest();
            req.setDomain(DomainSelector.fromName(XMPPServer.getInstance()
                    .getServerInfo().getXMPPDomain()));
            GetAllAccountsResponse resp = ZimbraSoapUtils.invokeJaxb(mTransport, req);
            return processAccountNames(resp.getAccountList());
        } catch (ServiceException e) {
            Log.error(e.getMessage(), e);
            if(fallback && fallbackProvider != null) {
                Log.debug("Failed to fetch all usernames from Zimbra SOAP API. Falling back to " + fallbackProviderClass);
                return fallbackProvider.getUsernames();
            } else {
                return Collections.emptyList();
            }
        }
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        Set<String> s = Collections.emptySet();
        return findUsers(s,"",startIndex,numResults);
    }

    public void setName(String username, String name)
            throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setEmail(String username, String email)
            throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setCreationDate(String username, Date creationDate)
            throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setModificationDate(String username, Date modificationDate)
            throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return new LinkedHashSet<String>(Arrays.asList("Username", "Name",
                "Email"));
    }

    public Collection<User> findUsers(Set<String> fields, String value) {
        return findUsers(fields, value, 0, userSearchLimit);
    }

    public Collection<User> findUsers(Set<String> fields, String value,
            int startIndex, int numResults)
            throws UnsupportedOperationException {

        if (numResults > userSearchLimit) {
            numResults = userSearchLimit;
        }

        StringBuilder sb = new StringBuilder("(&(zimbraAccountStatus=active)(|");
        if(value != null && value.trim().length() > 0) {
            if (fields.contains("Username")) {
                sb.append("(cn=*");
                sb.append(value);
                sb.append("*)");
            }
            if (fields.contains("Name")) {
                sb.append("(cn=*");
                sb.append(value);
                sb.append("*)");

                sb.append("(sn=*");
                sb.append(value);
                sb.append("*)");

                sb.append("(gn=*");
                sb.append(value);
                sb.append("*)");

                sb.append("(displayName=*");
                sb.append(value);
                sb.append("*)");
            }
            if (fields.contains("Email")) {
                sb.append("(mail=*");
                sb.append(value);
                sb.append("*)");

                sb.append("(zimbraMailDeliveryAddress=*");
                sb.append(value);
                sb.append("*)");

                sb.append("(gn=*");
                sb.append(value);
                sb.append("*)");

                sb.append("(zimbraMailAlias=*");
                sb.append(value);
                sb.append("*)");
            }
        } else {
            sb.append("(cn=*)");
        }

        sb.append("))");
        String query = sb.toString();

        SearchDirectoryRequest req = new SearchDirectoryRequest();
        req.setQuery(query);
        req.setTypes("accounts");
        req.setLimit(numResults);
        req.setOffset(startIndex);
        req.setDomain(XMPPServer.getInstance().getServerInfo().getXMPPDomain());

        try {
            ZimbraSoapUtils.soapAdminAuthenticate(mTransport, adminLogin, adminPassword);
            SearchDirectoryResponse resp = ZimbraSoapUtils.invokeJaxb(mTransport, req);
            return processAccountInfoList(resp.getAccounts());
        } catch (ServiceException e) {
            Log.error(e.getMessage(), e);
            if(fallback && fallbackProvider != null) {
                Log.debug("Failed to find users using Zimbra SOAP API. Falling back to " + fallbackProviderClass);
                return fallbackProvider.findUsers(fields, value,startIndex, numResults);
            } else {
                return Collections.emptyList();
            }
        }
    }

    public boolean isReadOnly() {
        // we do not want OpenFire to be managing Zimbra user accounts
        return true;
    }

    public boolean isNameRequired() {
        return true;
    }

    public boolean isEmailRequired() {
        return true;
    }

    private Collection<User> processAccountInfoList(List<AccountInfo> accounts) throws ServiceException {
        List<User> users = new ArrayList<User>();
        for (AccountInfo acc : accounts) {
            Map<String, Object> attrsMap = Attr.collectionToMap(acc.getAttrList());
            Date createdDate = DateUtil.parseGeneralizedTime(attrsMap.get(ZAttrProvisioning.A_zimbraCreateTimestamp).toString());
            // plugging same date for created and modified, because Zimbra does not store account modification date in LDAP
            Object name = attrsMap.get(ZAttrProvisioning.A_displayName);
            users.add(new User(attrsMap.get(ZAttrProvisioning.A_uid).toString(), 
                    name == null ? acc.getName() : name.toString(),
                    attrsMap.get(ZAttrProvisioning.A_mail).toString(), createdDate,
                    createdDate));
        }
        return users;
    }

    private Collection<String> processAccountNames(List<AccountInfo> accounts) throws ServiceException {
        List<String> users = new ArrayList<String>();
        for (AccountInfo acc : accounts) {
            Map<String, Object> attrsMap = Attr.collectionToMap(acc.getAttrList());
            users.add(attrsMap.get(ZAttrProvisioning.A_uid).toString());
        }
        return users;
    }
}
