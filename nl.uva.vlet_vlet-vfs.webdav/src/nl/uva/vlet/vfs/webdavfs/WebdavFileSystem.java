package nl.uva.vlet.vfs.webdavfs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.Global;
import nl.uva.vlet.GlobalConfig;
import nl.uva.vlet.data.StringHolder;
import nl.uva.vlet.data.VAttribute;
import nl.uva.vlet.data.VAttributeConstants;
import nl.uva.vlet.exception.ResourceAlreadyExistsException;
import nl.uva.vlet.exception.ResourceCreationFailedException;
import nl.uva.vlet.exception.ResourceNotFoundException;
import nl.uva.vlet.exception.VRLSyntaxException;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.exception.VlIOException;
import nl.uva.vlet.presentation.Presentation;
import nl.uva.vlet.vfs.FileSystemNode;
import nl.uva.vlet.vfs.VDir;
import nl.uva.vlet.vfs.VFSClient;
import nl.uva.vlet.vfs.VFSNode;
import nl.uva.vlet.vfs.VFSTransfer;
import nl.uva.vlet.vfs.VFile;
import nl.uva.vlet.vrl.VRL;
import nl.uva.vlet.vrs.ServerInfo;
import nl.uva.vlet.vrs.VRSContext;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.CopyMethod;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.apache.jackrabbit.webdav.client.methods.MoveMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.xerces.dom.DeferredElementNSImpl;

import sun.misc.BASE64Encoder;

/**
 * WebdavFileSystem
 *
 * @author S. Koulouzis, Piter T. de Boer
 */
public class WebdavFileSystem extends FileSystemNode {

    private static final int MAX_HOST_CONNECTIONS = 30;
    private static ClassLogger logger;

    static {
        logger = ClassLogger.getLogger(WebdavFileSystem.class);
        logger.setLevelToError();
    }
    // ==== instance === 
    private boolean connected;
    private HostConfiguration hostConfig;
    private MultiThreadedHttpConnectionManager connectionManager;
    private HttpConnectionManagerParams httpConnectionParams;
    private HttpClient client;
    private boolean useSSL;
    private ServerInfo info;
    private Enumeration allowedMethods = null;

    /**
     * Creates a WebdavFileSystem. Most of the interaction with a server happens
     * through this class
     *
     * @param context
     * @param info
     * @param location
     */
    public WebdavFileSystem(VRSContext context, ServerInfo info, VRL location) {

        super(context, info);
        if (location.getScheme().endsWith("ssl")) {
            useSSL = true;
        }
        int port = getPort();
        if (port == -1) {
            port = 443;
        }

        ProtocolSocketFactory socketFactory =
                new EasySSLProtocolSocketFactory();
        Protocol https = new Protocol("https", socketFactory, port);
        Protocol.registerProtocol("https", https);
        setRoot(location, info);
    }

    @Override
    public VFSNode openLocation(VRL vrl) throws VlException {
        connect();

        VRL newVRL = vrl;
        String path = vrl.getPath();
        if (path.endsWith("~")) {
//            newVRL = vrl.copyWithNewPath("/");
            newVRL = info.getRootPath();
        } else if (path.equals("/") && !info.getRootPath().equals(vrl)) {
            newVRL = info.getRootPath();
        }
        ArrayList<VFSNode> nodes = null;
        try {
            nodes = propFind(newVRL, DavConstants.PROPFIND_ALL_PROP_INCLUDE, DavConstants.DEPTH_0, true);
        } catch (Exception ex) {
            if (ex.getMessage().contains("Bad Request")) {
                nodes = propFind(newVRL, DavConstants.PROPFIND_ALL_PROP_INCLUDE, DavConstants.DEPTH_0, false);
            } else {
                throw new ResourceNotFoundException("Query returned not result for location:" + vrl, ex);
            }
        }

        if ((nodes == null) || (nodes.size() <= 0)) {
            throw new ResourceNotFoundException("Query returned not result for location:" + vrl);
        }

        VFSNode[] nodesArray = new VFSNode[nodes.size()];
        nodesArray = nodes.toArray(nodesArray);

        return nodesArray[0];
    }

    /**
     *
     * The PROPFIND method retrieves properties defined on the resource
     * identified by the Request-VRL, if the resource does not have any internal
     * members, or on the resource identified by the Request-URI and potentially
     * its member resources, if the resource is a collection that has internal
     * member URIs.
     *
     * @param vrl
     * @param requestPropType DavConstants.PROPFIND_ALL_PROP,
     * DavConstants.PROPFIND_BY_PROPERTY ,DavConstants.PROPFIND_ALL_PROP_INCLUDE
     * @param depth DavConstants.DEPTH_0, DavConstants.DEPTH_1,
     * DavConstants.DEPTH_INFINITY
     * @return
     * @throws VlException
     */
    protected ArrayList<VFSNode> propFind(VRL vrl, int requestPropType, int depth, boolean addTrailingSlash) throws VlException {
        PropFindMethod method = null;
        ArrayList<VFSNode> nodes = null;
        try {
            method = new PropFindMethod(vrlToUrl(vrl, addTrailingSlash).toString(), requestPropType, depth);
            nodes = executePropFind(method);
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw new VlIOException((IOException) e);
            }
            if (e.getMessage().contains("Bad Request")) {
                return propFind(vrl, requestPropType, depth, !addTrailingSlash);
            } else {
                throw new VlException(e);
            }
        }

        return nodes;
//        String url = vrlToUrl(vrl).toString();
//        DavPropertyNameSet propertyNameSet = new DavPropertyNameSet();
//
////        DavPropertyName availableStorageSitesName = DavPropertyName.create("avail-storage-sites", Namespace.getNamespace("DAV:"));
//        propertyNameSet.add(DavPropertyName.CREATIONDATE);
//        propertyNameSet.add(DavPropertyName.DISPLAYNAME);
//        propertyNameSet.add(DavPropertyName.GETCONTENTLANGUAGE);
//        propertyNameSet.add(DavPropertyName.GETCONTENTTYPE);
//        propertyNameSet.add(DavPropertyName.GETETAG);
//        propertyNameSet.add(DavPropertyName.GETLASTMODIFIED);
//        propertyNameSet.add(DavPropertyName.ISCOLLECTION);
//        propertyNameSet.add(DavPropertyName.RESOURCETYPE);
//        propertyNameSet.add(DavPropertyName.SOURCE);
//        propertyNameSet.add(DavPropertyName.SUPPORTEDLOCK);
//        PropFindMethod propFind;
//        try {
//            propFind = new PropFindMethod(url, propertyNameSet, DavConstants.DEPTH_INFINITY);
//            int status = getClient().executeMethod(propFind);
//
//
//            MultiStatus multiStatus = propFind.getResponseBodyAsMultiStatus();
//            MultiStatusResponse[] responses = multiStatus.getResponses();
//
//
//            for (MultiStatusResponse r : responses) {
//                DavPropertySet allProp = getProperties(r);
//                DavPropertyIterator iter = allProp.iterator();
//                while (iter.hasNext()) {
//                    DavProperty<?> p = iter.nextProperty();
//                    System.err.println(p.getName() + " : " + p.getValue());
//                }
//            }
//
//        } catch (IOException ex) {
//            Logger.getLogger(WebdavFileSystem.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (DavException ex) {
//            Logger.getLogger(WebdavFileSystem.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return null;
    }

    /**
     *
     * The PROPFIND method retrieves properties defined on the resource
     * identified by the Request-VRL, if the resource does not have any internal
     * members, or on the resource identified by the Request-URI and potentially
     * its member resources, if the resource is a collection that has internal
     * member URIs.
     *
     * @param vrl
     * @param requestPropType DavConstants.PROPFIND_ALL_PROP,
     * DavConstants.PROPFIND_BY_PROPERTY ,DavConstants.PROPFIND_ALL_PROP_INCLUDE
     * @param depth DavConstants.DEPTH_0, DavConstants.DEPTH_1,
     * DavConstants.DEPTH_INFINITY
     * @return
     * @throws VlException
     */
    protected ArrayList<VFSNode> propFind(VRL vrl, DavPropertyNameSet propNameSet, int depth, boolean addTrailingSlash) throws VlException {
        PropFindMethod method = null;

        try {
            method = new PropFindMethod(vrlToUrl(vrl, addTrailingSlash).toString(), propNameSet, depth);
        } catch (IOException e) {
            throw new VlIOException(e);
        }

        return executePropFind(method);
    }

    /**
     * Executes the
     * <code>PropFindMethod</code>
     *
     * @param method the PropFindMethod
     * @return <code>ArrayList<VFSNode></code>
     * @throws VlException
     */
    private ArrayList<VFSNode> executePropFind(PropFindMethod method) throws VlException {
        ArrayList<VFSNode> node = new ArrayList<>();
        int code;
        try {
            code = executeMethod(method);

            if (code == HttpStatus.SC_NOT_FOUND) {
                return null;
            }

            MultiStatus multiStatus = method.getResponseBodyAsMultiStatus();

            MultiStatusResponse[] responses = multiStatus.getResponses();

            VFSNode currNode = null;

            for (int i = 0; i < responses.length; i++) {
                currNode = createVFSNode(responses[i]);
                node.add(currNode);
            }

        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } catch (DavException e) {
            String msg = "" + e.getMessage();
            msg = msg.toLowerCase();

            boolean forbidden = msg.contains("forbidden");
            boolean unauth = msg.contains("unauthorized");

            // Permision denied/wrong authentication: 
            if (forbidden || unauth) {
                ServerInfo info = this.getServerInfo();
                info.setHasValidAuthentication(false);
                info.store();
                throw new nl.uva.vlet.exception.ResourceAccessDeniedException("Access denied or wrong authentication (user=" + getUsername() + ").\nMessage=" + msg, e);
            } else {
                throw new VlException(e);
            }
        } finally {
            method.releaseConnection();
        }
        return node;

    }

    protected int executeMethod(HttpMethod method) throws HttpException, IOException, VlException {
        // todo: redirects!
        // PTdB: here ? 
//        method.setFollowRedirects(true);

        boolean useBasicAuth = getUseBasicAuth();
        String user = null;
        String passwd = null;
        if (useBasicAuth) {
            user = getServerInfo().getUsername();
            passwd = getServerInfo().getPassword();

            method.addRequestHeader("Authorization",
                    "Basic " + (new BASE64Encoder()).encode((user + ":" + passwd).getBytes()));
        }

        int val = getClient().executeMethod(method);
        if (val >= 300 && val <= 399) {
            String redirectLocation;
            Header locationHeader = method.getResponseHeader("location");
            if (locationHeader != null) {
                redirectLocation = locationHeader.getValue();
                method.setURI(new URI(redirectLocation));
                val = getClient().executeMethod(method);
            }
        }

        user = null;
        passwd = null;

        return val;
    }

    /**
     * Used to get the properties of a resource
     *
     * @param vrl
     * @return the properties of that resource
     * @throws VlException
     */
    protected DavPropertySet getProperties(VRL vrl, boolean addTrailingSlash) throws VlException {

        URL uri = vrlToUrl(vrl, addTrailingSlash);

        PropFindMethod method = null;
        MultiStatusResponse[] responses;
        try {
            method = new PropFindMethod(uri.toString(), DavConstants.PROPFIND_ALL_PROP_INCLUDE, DavConstants.DEPTH_0);

            int code = executeMethod(method);

            if (code == HttpStatus.SC_NOT_FOUND) {
                return null;
            }

            MultiStatus multiStatus = method.getResponseBodyAsMultiStatus();

            responses = multiStatus.getResponses();
        } catch (IOException e) {
            throw new VlIOException(e);
        } catch (DavException e) {
            throw new VlException(e);
        } finally {
            method.releaseConnection();
        }
        return getProperties(responses[0]);
    }

    /**
     * Extracts properties from a server responce
     *
     * @param statusResponse
     * @return the properties
     */
    private DavPropertySet getProperties(MultiStatusResponse statusResponse) {
        Status[] status = statusResponse.getStatus();

        DavPropertySet allProp = new DavPropertySet();
        for (int i = 0; i < status.length; i++) {
            DavPropertySet pset = statusResponse.getProperties(status[i].getStatusCode());
            allProp.addAll(pset);
        }

        return allProp;
    }

    /**
     * Instantiates a
     * <code>VFSNode</> based on the properties received from the server
     *
     * @param statusResponse
     * @return the node (File of Dir)
     * @throws VRLSyntaxException
     * @throws MalformedURLException
     */
    private VFSNode createVFSNode(MultiStatusResponse statusResponse) throws VRLSyntaxException,
            MalformedURLException {
        DavPropertySet allProp = getProperties(statusResponse);
//        DavPropertyIterator iter = allProp.iterator();
        boolean isDir = false;

//        while (iter.hasNext()) {
//            // DavProperty<?> porp = iter.nextProperty();
//            DavProperty<?> prop = iter.next();
//            logger.debugPrintf("%s : %s\n", prop.getName().getName(),
//                    prop.getValue());
//        }

        // logger.debugPrintf("------------------------\n");

        Object value = null;
        String valueStr = null;


//        DavPropertyName[] names = allProp.getPropertyNames();
//        for (DavPropertyName n : names) {
//            logger.debugPrintf("%s : %s\n", n,
//                    allProp.get(n));
//        }

        DavProperty<?> prop = allProp.get(WebdavConst.XPROP_ISCOLLECTION);
        if (prop == null) {
            prop = allProp.get(DavConstants.PROPERTY_RESOURCETYPE);
        }

        value = prop.getValue();
//        logger.debugPrintf("%s : %s\n", DavConstants.PROPERTY_RESOURCETYPE,
//                value);
        if (value != null) {
            valueStr = "" + value;
            if (valueStr.toLowerCase().equals("true")) {
                isDir = true;
            }
            if (valueStr.toLowerCase().contains("collection")) {
                isDir = true;
            }
        }


        DavProperty<?> resourceType = allProp.get(DavProperty.PROPERTY_RESOURCETYPE);

        if (resourceType != null) {
            value = resourceType.getValue();
        }

        String name = null;
        if (value != null && value instanceof org.apache.xerces.dom.DeferredElementNSImpl) {
            DeferredElementNSImpl element = (DeferredElementNSImpl) value;
            name = element.getLocalName();
        }

        if (name != null && name.equals(DavProperty.XML_COLLECTION)) {
            isDir = true;
        }

        VFSNode node;
        if (isDir) {
            node = new WebdavDir(this, urlToVrl(statusResponse.getHref()), allProp);
        } else {
            node = new WebdavFile(this, urlToVrl(statusResponse.getHref()), allProp);
        }

        return node;

    }

    private VRL urlToVrl(String href) throws VRLSyntaxException {
        VRL vrl = new VRL(href);
        if (vrl.isRelative()) {
            vrl = getVRL().copyWithNewPath(href);
//            vrl = new VRL(getVRL().getHostname()).appendPath(href);
        }
        if (useSSL) {
            return new VRL(WebdavFSFactory.schemes[1], vrl.getHostname(), vrl.getPort(), vrl.getPath());
        }
        return new VRL(WebdavFSFactory.schemes[0], vrl.getHostname(), vrl.getPort(), vrl.getPath());
    }

    private URL vrlToUrl(VRL vrl, boolean addTrailingSlash) throws VRLSyntaxException {
        URL url;
        if (useSSL) {
            url = new VRL("https", vrl.getHostname(), vrl.getPort(), vrl.getPath()).toURL();
        } else {
            url = new VRL("http", vrl.getHostname(), vrl.getPort(), vrl.getPath()).toURL();
        }
        if (addTrailingSlash && !url.toString().endsWith("/")) {
            try {
                url = new URL(url.toString() + "/");
            } catch (MalformedURLException ex) {
                throw new VRLSyntaxException(ex);
            }
        }
        return url;
    }

    @Override
    public VDir newDir(VRL dirVrl) throws VlException {
        DavPropertySet props = new DavPropertySet();
        return new WebdavDir(this, dirVrl, props);
    }

    @Override
    public VFile newFile(VRL fileVrl) throws VlException {
        DavPropertySet props = new DavPropertySet();
        return new WebdavFile(this, fileVrl, props);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public String getUsername() {
        VAttribute attr = this.getServerInfo().getAttribute(VAttributeConstants.ATTR_USERNAME);
        if (attr != null) {
            return attr.getStringValue();
        }

        return Global.getUsername();
    }

    @Override
    public void connect() throws VlException {
        if (!isConnected()) {
            if (hostConfig == null) {
                hostConfig = new HostConfiguration();
            }
            if (connectionManager == null) {
                connectionManager = new MultiThreadedHttpConnectionManager();
            }
            if (httpConnectionParams == null) {
                httpConnectionParams = new HttpConnectionManagerParams();
            }
            hostConfig.setHost(getHostname(), getPort());

            String timeOut = (String) vrsContext.getProperty(GlobalConfig.TCP_CONNECTION_TIMEOUT);
            if (timeOut == null) {
                timeOut = "30000";
            }
            httpConnectionParams.setConnectionTimeout(Integer.valueOf(timeOut));
            httpConnectionParams.setParameter(HttpClientParams.ALLOW_CIRCULAR_REDIRECTS, true);
            httpConnectionParams.setParameter(HttpClientParams.REJECT_RELATIVE_REDIRECT, false);
            httpConnectionParams.setParameter(HttpClientParams.MAX_REDIRECTS, 50);



            // httpConnectionParams.setMaxConnectionsPerHost(hostConfig,
            // MAX_HOST_CONNECTIONS);
            connectionManager.setParams(httpConnectionParams);

            client = new HttpClient(connectionManager);

            getClient().getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());

            // StringHolder secret = new StringHolder();
            // getContext().getUI().askAuthentication("Password for " +
            // getUsername() + " user:", secret);

            // Credentials creds = new
            // UsernamePasswordCredentials(getUsername(), secret.value);

            ServerInfo info = this.getServerInfo();
            String user = info.getUsername();
            String passwd = info.getPassword();

            if ((info.hasValidAuthentication() == false) || (passwd == null)) {
                passwd = this.uiPromptPassfield("Webdav: Password for user:" + user + "@" + getHostname() + ":" + getPort());
                info.setPassword(passwd);
            }

            Credentials creds = new UsernamePasswordCredentials(user, passwd);

            client.getState().setCredentials(AuthScope.ANY, creds);
            client.setHostConfiguration(hostConfig);

            creds = null;

            connected = true;
        }
    }

    public String uiPromptPassfield(String message) {
        if (this.getVRSContext().getConfigManager().getAllowUserInteraction() == false) {
            return null;
        }

        StringHolder secretHolder = new StringHolder(null);

        boolean result = getVRSContext().getUI().askAuthentication(message, secretHolder);

        if (result == true) {
            return secretHolder.value;
        } else {
            return null;
        }
    }

    @Override
    public void disconnect() throws VlException {
        this.client = null;
        hostConfig = null;
        connectionManager = null;
        httpConnectionParams = null;
        connected = false;
    }

    public static void clearClass() {
        // TODO Auto-generated method stub
    }

    /**
     * Creates Date from a string. The format is Day, NumOfDay Month Year
     * hh:mm:ss timeZone example: 'Thu, 18 Feb 2010 07:54:48 GMT'
     *
     * @param value
     * @return
     * @throws VlException
     */
    protected Date createDateFromString(String value) throws VlException {
        if (value == null || value.equals("null")) {
            return null;
        }

        if (value.length() < 29) {
            throw new VlException("Date parsing error. The date string should have a lenght of 29 instead got:  "
                    + value.length());
        }

        String[] strs = value.split("[ ,:]");

        int day = new Integer(strs[2]);
        int month = new Integer(Presentation.getMonthNumber(strs[3]));
        int year = new Integer(strs[4]);
        int hours = new Integer(strs[5]);
        int minutes = new Integer(strs[6]);
        int seconds = new Integer(strs[7]);

        TimeZone storedTimeZone = TimeZone.getTimeZone(strs[8]);

        GregorianCalendar now = new GregorianCalendar();
        TimeZone localTMZ = now.getTimeZone();

        now.setTimeZone(storedTimeZone);
        now.set(year, month, day, hours, minutes, seconds);
        // now.set(GregorianCalendar.MILLISECOND, millis); // be precize!
        // convert timezone back to 'local'
        now.setTimeZone(localTMZ);

        return now.getTime();
    }

    protected InputStream getInputStream(VRL vrl) throws VlException {
        logger.infoPrintf(" - >>> getInputStream:%s\n", vrl);

        //if (false)
        {
            GetMethod get = new GetMethod(vrlToUrl(vrl, false).toString());
            //get.setFollowRedirects(true);

            try {
                int result = executeMethod(get);

                if (result == HttpStatus.SC_NOT_FOUND) {
                    throw new nl.uva.vlet.exception.ResourceNotFoundException("Not found:" + vrl);
                }

                Header[] headers = get.getResponseHeaders();
                for (int i = 0; i < headers.length; i++) {
                    logger.debugPrintf(" - %s=%s\n", headers[i].getName(), headers[i].getValue());
                }

                return new WebdavInputStream(get, get.getResponseBodyAsStream());
            } catch (HttpException e) {
                throw new VlException(e);
            } catch (IOException e) {
                throw new VlIOException(e);
            } finally {
                // do not release connection, intputstream must be kept 
                // get.releaseConnection();
            }
        }
        //        URLConnection conn;
//        try
//        {
//            URL url = vrlToUrl(vrl);
//            conn = url.openConnection();
//
//            return conn.getInputStream();
//        }
//        catch (IOException e)
//        {
//            throw new VlIOException(e);
//        }
    }

    /**
     * We are going to cheat for now and not really open a stream to the remote
     * file. Instead download the file open the stream change its contents and
     * on close stream uploaded it again
     *
     * @param vrl
     * @return
     * @throws VlException
     */
    protected OutputStream getOutputStream(VRL vrl) throws VlException {
        ArrayList<VFSNode> list = propFind(vrl, DavConstants.PROPFIND_ALL_PROP_INCLUDE, DavConstants.DEPTH_0, false);
        WebdavFile targetFile;
        if (list == null || list.isEmpty()) {
            targetFile = (WebdavFile) newFile(vrl);
        } else {
            targetFile = (WebdavFile) list.get(0);
        }

        WebdavOutputStream webdavOS = new WebdavOutputStream(this, targetFile);
        return webdavOS;
    }

    @Override
    public VDir createDir(VRL vrl, boolean ignoreExisting) throws VlException {

        WebdavDir dir;
        String url = vrlToUrl(vrl, true).toString();
        MkColMethod mkCol = new MkColMethod(url);
        try {
            int code = executeMethod(mkCol);

            if (code != HttpStatus.SC_CREATED && !ignoreExisting) {
                throw new ResourceAlreadyExistsException("Could not create " + vrl + " " + mkCol.getStatusText());
            }

            ArrayList<VFSNode> nodes = propFind(vrl, DavConstants.PROPFIND_ALL_PROP_INCLUDE, DavConstants.DEPTH_0, true);

            VFSNode node = nodes.get(0);
            if (node instanceof WebdavDir) {
                dir = (WebdavDir) nodes.get(0);
            } else {
                throw new ResourceCreationFailedException("File " + vrl + " not created");
            }


        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            mkCol.releaseConnection();
        }

        return dir;
    }

    @Override
    public boolean existsFile(VRL fileVrl) throws VlException {
        return existsPath(fileVrl);
    }

    @Override
    public WebdavFile createFile(VRL vrl, boolean ignoreExisting) throws VlException {

        // Since the call is not creating any exception we have to check if file
        // exists
        if (!ignoreExisting) {
            if (existsPath(vrl)) {
                throw new ResourceAlreadyExistsException("File " + vrl + " already exists.");
            }
        }

        WebdavFile file;
        org.apache.jackrabbit.webdav.client.methods.PutMethod put = new org.apache.jackrabbit.webdav.client.methods.PutMethod(
                vrlToUrl(vrl, true).toString());
        try {
            int code = executeMethod(put);
            if (code != HttpStatus.SC_OK && code != HttpStatus.SC_CREATED) {
                throw new ResourceCreationFailedException("File " + vrl + " not created");
            }
            put.releaseConnection();

            DavPropertyNameSet nameSet = new DavPropertyNameSet();
            nameSet.add(DavPropertyName.DISPLAYNAME);
            PropFindMethod propFind = new PropFindMethod(vrlToUrl(vrl, false).toString(), nameSet, DavConstants.DEPTH_INFINITY);
//            System.err.println("propFind: " + propFind + " vrlToUrl(vrl, false).toString(): " + vrlToUrl(vrl, false).toString() + " nameSet: " + nameSet + "  DavConstants.DEPTH_INFINITY: " + DavConstants.DEPTH_INFINITY);
            int status = getClient().executeMethod(propFind);
//            System.err.println("status: " + status);


            ArrayList<VFSNode> nodes = propFind(vrl, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_1, false);
            VFSNode node = nodes.get(0);
            if (node instanceof WebdavFile) {
                file = (WebdavFile) nodes.get(0);
            } else {
                throw new ResourceCreationFailedException("File " + vrl + " not created");
            }


        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            if (put != null) {
                put.releaseConnection();
            }
        }

        return file;
    }

    protected boolean delete(VRL vrl, boolean recurse, boolean addTrailingSlash) throws VlException {
        DeleteMethod del = new DeleteMethod(vrlToUrl(vrl, addTrailingSlash).toString());
        try {
            executeMethod(del);
        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            del.releaseConnection();
        }

        return true;
    }

    public VRL nameToVRL(String name) {
        return new VRL(WebdavFSFactory.schemes[0], getHostname(), getPort(), getVRL().getPath() + "/" + name);
    }

    public VRL move(VRL source, VRL destination, boolean overwrite) throws VlException {
        URL sourceUri = vrlToUrl(source, true);
        URL destinationUri = vrlToUrl(destination, true);
        MoveMethod move = new MoveMethod(sourceUri.toString(), destinationUri.toString(), overwrite);
        try {
            int code = executeMethod(move);

            if (code != HttpStatus.SC_OK || code != HttpStatus.SC_CREATED) {
                String message = "Moving " + source + " to: " + destination + " failed. " + move.getStatusText();
                if (!overwrite && existsPath(destination)) {
                    return destination;
                    // throw new ResourceAlreadyExistsException(message);
                } else {
                    throw new VlException(message);
                }
            }

            return destination;
        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            move.releaseConnection();
        }

    }

    @Override
    public boolean existsPath(VRL path) throws VlException {
        ArrayList<VFSNode> result = propFind(path, DavConstants.PROPFIND_PROPERTY_NAMES, DavConstants.DEPTH_0, true);
        return (result != null && !result.isEmpty());
    }

    @Override
    public boolean existsDir(VRL path) throws VlException {
        return this.existsPath(path);
    }

    protected boolean copy(VRL source, VRL destination, boolean overwrite, boolean recursive)
            throws ResourceAlreadyExistsException, VlException {

        URL sourceUri = vrlToUrl(source, true);
        URL destinationUri = vrlToUrl(destination, true);

        CopyMethod copy = new CopyMethod(sourceUri.toString(), destinationUri.toString(), overwrite, recursive);

        try {
            int code = executeMethod(copy);
            if (code != HttpStatus.SC_OK || code != HttpStatus.SC_CREATED) {
                String message = "Copying " + source + " to: " + destination + " failed. " + copy.getStatusText();
                if (!overwrite && existsPath(destination)) {
                    throw new ResourceAlreadyExistsException(message);
                } else {
                    throw new VlException(message);
                }
            }
            return true;
        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            copy.releaseConnection();
        }
    }

    protected void upload(VRL source, VRL destination) throws VlException {
        try {
//            URL uri = vrlToUrl(destination);
//            post = new PostMethod(uri.toString());
//            Part[] parts = new Part[1];
//            if (source.getScheme().equals("file")) {
//
//                parts[0] = new FilePart(source.getBasename(), new File(source.getPath()));
//
//            } else {
//                VFSClient Vclient = new VFSClient(getContext());
//                VFile file = Vclient.getFile(source);
//                localFile = file.copyTo(GlobalConfig.getDefaultTempDir());
//                parts[0] = new FilePart(source.getBasename(), new File(localFile.getVRL().toURIString()));
//            }
//
//
//            MultipartRequestEntity requestEntity = new MultipartRequestEntity(parts, post.getParams());
//            post.setRequestEntity(requestEntity);


//            put = new PutMethod(uri.toString());
            VFSClient Vclient = new VFSClient(getContext());
            VFile localSource = Vclient.getFile(source);
//            RequestEntity requestEntity = new InputStreamRequestEntity(file.getInputStream());
//            put.setRequestEntity(requestEntity);
//            executeMethod(post);


            putFile(localSource, destination);

        } catch (HttpException e) {

            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
        }
    }

    void uploadFile(VFSTransfer transferInfo, VFile localSource, VRL dest) throws VlException {
        if (transferInfo != null) {
            transferInfo.startSubTask("UploadToWebDAV", localSource.getLength());
        }

        URL uri = vrlToUrl(dest, false);
//        OptionsMethod options = new OptionsMethod(uri.toString());
//        PostMethod post = null;
        VFile localFile = null;
        try {
//            boolean canPost = false;
//            if (allowedMethods == null) {
//                int code = executeMethod(options);
//                if (code == HttpStatus.SC_OK) {
//                    allowedMethods = options.getAllowedMethods();
//                }
//                options.releaseConnection();
//                if (allowedMethods != null) {
//                    while (allowedMethods.hasMoreElements()) {
//                        String method = (String) allowedMethods.nextElement();
//                        if (method.contains("POST")) {
//                            canPost = true;
//                            break;
//                        }
//                    }
//                }
//            }

//            if (canPost) {
//                postFile(localSource, dest);
//            } else {
            putFile(localSource, dest);
//            }


        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            if (localFile != null) {
                localFile.delete();
            }
        }

        if (transferInfo != null) {
            transferInfo.endTask("UploadToWebDAV");
        }
    }

//    private void postFile(VFile localSource, VRL dest) throws VRLSyntaxException, FileNotFoundException, HttpException, VlException, IOException {
//        URL uri = vrlToUrl(dest);
//        PostMethod post = new PostMethod(uri.toString());
//        try {
//            Part[] parts = new Part[1];
//            VFile localFile = null;
//            if (localSource.getVRL().getScheme().equals("file")) {
////                String fileURI = localSource.getVRL().toURIString();
//                String path = localSource.getVRL().getPath();
//                parts[0] = new FilePart(localSource.getVRL().getBasename(), new File(path));
//            } else {
//                localFile = localSource.copyTo(GlobalConfig.getDefaultTempDir());
//                parts[0] = new FilePart(dest.getBasename(), new File(localFile.getVRL().toURIString()));
//            }
//            MultipartRequestEntity requestEntity = new MultipartRequestEntity(parts, post.getParams());
//            post.setRequestEntity(requestEntity);
//            int code = executeMethod(post);
//
//
//            if (code != HttpStatus.SC_OK || code != HttpStatus.SC_CREATED) {
//                throw new nl.uva.vlet.exception.ResourceCreationFailedException(post.getStatusText());
//            }
//            if (localFile != null) {
//                localFile.delete();
//            }
//        } finally {
//            post.releaseConnection();
//        }
//    }
    private void putFile(VFile localSource, VRL dest) throws VRLSyntaxException, FileNotFoundException, VlException, HttpException, IOException {
        URL uri = vrlToUrl(dest, false);
//        PutMethod put = new PutMethod(uri.toString());
        org.apache.jackrabbit.webdav.client.methods.PutMethod put = new org.apache.jackrabbit.webdav.client.methods.PutMethod(uri.toString());
        VFile localFile = null;
        try {
//            Part[] parts = new Part[1];
            File jFile = null;
            if (localSource.getVRL().getScheme().equals("file")) {
//                String fileURI = localSource.getVRL().toURIString();
                String path = localSource.getVRL().getPath();
//                parts[0] = new FilePart(localSource.getVRL().getBasename(), );
                jFile = new File(path);
            } else {
                localFile = localSource.copyTo(GlobalConfig.getDefaultTempDir());
//                parts[0] = new FilePart(dest.getBasename(), new File(localFile.getVRL().toURIString()));
                jFile = new File(localFile.getVRL().toURIString());
            }
            //            RequestEntity requestEntity = new MultipartRequestEntity(parts, put.getParams());
            String type = Files.probeContentType(FileSystems.getDefault().getPath(jFile.getParent(), jFile.getName()));
            RequestEntity requestEntity = new FileRequestEntity(jFile, type);

            put.setRequestEntity(requestEntity);
            int code = executeMethod(put);

            if (String.valueOf(code).startsWith("4") || String.valueOf(code).startsWith("5")) {
                throw new VlException(put.getStatusText());
            }

        } finally {
            put.releaseConnection();
            if (localFile != null) {
                localFile.delete();
            }
        }
    }

    protected void getACL(VRL vrl) throws VlException {
        // URL uri = vrlToUrl(vrl);
        //
        // Principal principal = Principal.getAllPrincipal();
        // Privilege[] privileges = new Privilege[1];
        // privileges[0] = Privilege.PRIVILEGE_ALL;
        // boolean invert = false;
        // boolean isProtected = false;
        // AclResource inheritedFrom = null;// new DavResourceImpl();
        // Ace ace = AclProperty.createGrantAce(principal, privileges, invert,
        // isProtected, inheritedFrom);
        // Ace[] accessControlElements = new Ace[1];
        // accessControlElements[0] = ace;
        // AclProperty aclProp = new AclProperty(accessControlElements);
        //
        // AclMethod acl = null;
        // try
        // {
        // acl = new AclMethod(uri.toString(), aclProp);
        //
        // int code = executeMethod(acl);
        //
        // logger.debugPrintf("Code : %s sttus: %s\n", code,
        // acl.getStatusText());
        //
        // MultiStatus status = acl.getResponseBodyAsMultiStatus();
        //
        // }
        // catch (IOException e)
        // {
        // throw new VlIOException(e);
        // }
        // catch (DavException e)
        // {
        // throw new VlException(e);
        // }
        // finally
        // {
        // acl.releaseConnection();
        // }
    }

    boolean getUseBasicAuth() {
        return this.getServerInfo().getBoolProperty(WebdavConst.ATTR_ENABLE_HTTPAUTH_BASIC, false);
    }

    /**
     * Modification time is stored as string
     */
    public String getModificationTimeString(DavPropertySet davProperties) {
        if (davProperties == null) {
            return null;
        }

        String modstr = null;
        DavProperty<?> prop = davProperties.get(DavConstants.PROPERTY_GETLASTMODIFIED);

        if (prop != null) {
            modstr = "" + prop.getValue();
        }

        return modstr;
    }

    public long getLength(DavPropertySet davProperties) {

        if (davProperties == null) {
            return 0;
        }


//        DavPropertyIterator iter = davProperties.iterator();
//        while (iter.hasNext()) {
//            // DavProperty<?> porp = iter.nextProperty();
//            DavProperty<?> prop = iter.next();
//            logger.debugPrintf("%s : %s\n", prop.getName().getName(),
//                    prop.getValue());
//        }
//        DavProperty<?> p = davProperties.get(DavPropertyName.GETCONTENTLENGTH);
//        logger.debugPrintf("%s : %s\n", p.getName().getName(),
//                p.getValue());

        String StyLen = null;
        DavProperty<?> prop = davProperties.get(DavConstants.PROPERTY_GETCONTENTLENGTH);
        if ((prop == null) || (prop.getValue() == null)) {
            StyLen = "0";
        } else {
            StyLen = "" + prop.getValue();
        }

        return new Long(StyLen);
    }

    public boolean isReadable(DavPropertySet davProperties, boolean defaultValue) {
        // tobedone
        return true;
    }

    public boolean isWritable(DavPropertySet davProperties, boolean defaultValue) {
        // tobedone
        return true;
    }

//    protected HttpClient getHttpClient() {
//        return this.getClient();
//    }
    /**
     * @return the client
     */
    private HttpClient getClient() throws VlException {
        if (client == null || !isConnected()) {
            connect();
        }
        return client;
    }

    private void setRoot(VRL location, ServerInfo info) {
        String[] elements = location.getPathElements();
        VRL root = info.getRootPath();
        boolean exists = false;
        try {
            ArrayList<VFSNode> result = propFind(root, DavConstants.PROPFIND_PROPERTY_NAMES,
                    DavConstants.DEPTH_0, true);
            exists = (result != null && !result.isEmpty());
        } catch (VlException ex) {
            exists = false;
        }

        if (!exists) {
            for (String v : elements) {
                root = root.append(v);
                try {
                    ArrayList<VFSNode> result = propFind(root, DavConstants.PROPFIND_PROPERTY_NAMES,
                            DavConstants.DEPTH_0, true);
                    exists = (result != null && !result.isEmpty());
                } catch (VlException ex) {
                    exists = false;
                }
                if (exists) {
                    break;
                }
            }
        }
        info.setRootPath(root.getPath());
        info.store();
        this.info = info;
    }

    void setContents(String contents, String encoding, VRL vrl) throws VRLSyntaxException, UnsupportedEncodingException, HttpException, IOException, VlException {
        org.apache.jackrabbit.webdav.client.methods.PutMethod put = null;
        try {
            put = new org.apache.jackrabbit.webdav.client.methods.PutMethod(
                    vrlToUrl(vrl, false).toString());
            put.setRequestEntity(new StringRequestEntity(contents, "text/plain", encoding));
            int code = executeMethod(put);
            if (code != HttpStatus.SC_OK && code != HttpStatus.SC_CREATED) {
                throw new ResourceCreationFailedException("Failed to set conetents in " + vrl);
            }
        } finally {
            if (put != null) {
                put.releaseConnection();
            }
        }
    }

    String getContentsAsString(VRL vrl) throws VRLSyntaxException, HttpException, IOException, VlException {
        GetMethod get = new GetMethod(vrlToUrl(vrl, false).toString());
        try {
            int code = executeMethod(get);
            if (code != HttpStatus.SC_OK && code != HttpStatus.SC_CREATED) {
                throw new nl.uva.vlet.exception.VlException(get.getStatusText());
            }
        } finally {
//            get.releaseConnection();
        }
        return get.getResponseBodyAsString();

    }
//    ArrayList<VFSNode> propfind2(VRL vrl) throws VRLSyntaxException, IOException {
//        URL url = vrlToUrl(vrl);
//        DavPropertyNameSet propertyNameSet = new DavPropertyNameSet();
//
//        propertyNameSet.add(DavPropertyName.CREATIONDATE);
//        propertyNameSet.add(DavPropertyName.DISPLAYNAME);
//        propertyNameSet.add(DavPropertyName.GETCONTENTLANGUAGE);
//        propertyNameSet.add(DavPropertyName.GETCONTENTLENGTH);
//        propertyNameSet.add(DavPropertyName.GETCONTENTTYPE);
//        propertyNameSet.add(DavPropertyName.GETLASTMODIFIED);
//        propertyNameSet.add(DavPropertyName.ISCOLLECTION);
//        propertyNameSet.add(DavPropertyName.LOCKDISCOVERY);
//        propertyNameSet.add(DavPropertyName.RESOURCETYPE);
//        propertyNameSet.add(DavPropertyName.SOURCE);
//        propertyNameSet.add(DavPropertyName.SUPPORTEDLOCK);
//
//        PropFindMethod propFind = new PropFindMethod(url.toString(), propertyNameSet, DavConstants.DEPTH_INFINITY);
//        int status = client.executeMethod(propFind);
//
//        System.err.println("status: "+status);
//        return null;
//    }
}
