package nl.uva.vlet.vfs.slide.webdavfs;

import java.io.IOException;
import java.util.Enumeration;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.exception.VRLSyntaxException;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.exception.VlIOException;
import nl.uva.vlet.vfs.FileSystemNode;
import nl.uva.vlet.vfs.VDir;
import nl.uva.vlet.vfs.VFSNode;
import nl.uva.vlet.vfs.VFile;
import nl.uva.vlet.vrl.VRL;
import nl.uva.vlet.vrs.ServerInfo;
import nl.uva.vlet.vrs.VRSContext;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpURL;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.webdav.lib.WebdavResource;
import org.apache.webdav.lib.methods.DepthSupport;

/**
 * WebdavFileSystem
 *
 * @author S. Koulouzis
 */
public class WebdavFileSystem extends FileSystemNode {

    private static final Object UNIX_DIR = "httpd/unix-directory";
    private static ClassLogger logger;

    static {
        logger = ClassLogger.getLogger(WebdavFileSystem.class);
        logger.setLevelToError();
    }
    private boolean useSSL;

    public WebdavFileSystem(VRSContext context, ServerInfo info, VRL vrl) {
        super(context, info);
        if (vrl.getScheme().equals("webdavsSlide")) {
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
        setRoot(vrl, info);
    }

    @Override
    public VFSNode openLocation(VRL vrl) throws VlException {
        HttpURL httpURL = vrlToHttpURL(vrl);

        WebdavResource webdavResource = null;

        try {
            webdavResource = new WebdavResource(httpURL);
            //webdavResource = new WebdavResource(httpURL,credentials);

            webdavResource.propfindMethod(DepthSupport.DEPTH_1);

            Enumeration enume = webdavResource.getActiveLockOwners();

            if (enume != null) {
                while (enume.hasMoreElements()) {
                    Object elem = enume.nextElement();
                    logger.debugPrintf("lockOwners: %s\n", elem.getClass().getName());
                }
            }

            enume = webdavResource.getAllowedMethods();
            if (enume != null) {
                while (enume.hasMoreElements()) {
                    Object elem = enume.nextElement();
                    logger.debugPrintf("lockOwners: %s\n", elem.getClass().getName());
                }
            }

            enume = webdavResource.getDavCapabilities();
            if (enume != null) {
                while (enume.hasMoreElements()) {
                    Object elem = enume.nextElement();
                    logger.debugPrintf("lockOwners: %s\n", elem.getClass().getName());
                }
            }

            boolean existance = webdavResource.getExistence();
            logger.debugPrintf("existance: %s\n", existance);

            boolean overwrite = webdavResource.getOverwrite();
            logger.debugPrintf("overwrite: %s\n", overwrite);

            logger.debugPrintf("-------------------------\n");

            return createNode(webdavResource, vrl);
        } catch (HttpException e) {
            throw new VlException(e);
        } catch (IOException e) {
            throw new VlIOException(e);
        } finally {
            try {
                if (webdavResource != null) {
                    webdavResource.closeSession();
                }
            } catch (IOException e) {
                throw new VlIOException(e);
            }
        }
    }

    private HttpURL vrlToHttpURL(VRL vrl) throws VRLSyntaxException {
        HttpURL httpURL;
        try {
            if (useSSL) {
                httpURL = new HttpURL(new VRL("https", vrl.getHostname(), vrl.getPort(), vrl.getPath()).toString());
            } else {
                httpURL = new HttpURL(new VRL("http", vrl.getHostname(), vrl.getPort(), vrl.getPath()).toString());
            }
        } catch (URIException e) {
            throw new VRLSyntaxException(e);
        }
        return httpURL;
    }

    @Override
    public VDir newDir(VRL dirVrl) throws VlException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public VFile newFile(VRL fileVrl) throws VlException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isConnected() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void connect() throws VlException {
        // TODO Auto-generated method stub
    }

    @Override
    public void disconnect() throws VlException {
        // TODO Auto-generated method stub
    }

//    public static WebdavFileSystem getClientFor(VRSContext context, VRL location) throws VlException
//    {
//        String serverID = ServerNode.createServerID(location);
//
//        WebdavFileSystem webdavClient = (WebdavFileSystem) context.getServerInstance(serverID, WebdavFileSystem.class);
//
//        if (webdavClient == null)
//        {
//            // store new client
//            ServerInfo srmInfo = context.getServerInfoFor(location, true);
//            webdavClient = new WebdavFileSystem(context, srmInfo,location);
//            webdavClient.setID(serverID);
//            context.putServerInstance(webdavClient);
//        }
//
//        return webdavClient;
//    }
    protected VFSNode createNode(WebdavResource resource, VRL vrl) {
        if (resource.isCollection() || resource.getGetContentType().equals(UNIX_DIR)) {

            return new nl.uva.vlet.vfs.slide.webdavfs.WebdavDir(this, vrl, resource);
        } else {
            return new nl.uva.vlet.vfs.slide.webdavfs.WebdavFile(this, vrl, resource);
        }
    }

    protected VRL HttpURL2Vrl(HttpURL httpURL) throws VRLSyntaxException {
        try {
            if (useSSL) {
                return new VRL(WebdavFSFactory.schemes[1], httpURL.getHost(), httpURL.getPort(), httpURL.getPath());
            }
            return new VRL(WebdavFSFactory.schemes[0], httpURL.getHost(), httpURL.getPort(), httpURL.getPath());
        } catch (URIException e) {
            throw new VRLSyntaxException(e);
        }
    }

    protected VRL move(VRL vrl, VRL destination, boolean b) {
        // TODO Auto-generated method stub
        return null;
    }

    protected boolean delete(VRL vrl, boolean recurse) {
        // TODO Auto-generated method stub
        return false;
    }

    private void setRoot(VRL vrl, ServerInfo info) {
        String[] elements = vrl.getPathElements();
        VRL root = info.getRootPath();
        boolean exists = false;
        try {
            exists = existsDir(vrl);
        } catch (VlException ex) {
            exists = false;
        }

        if (!exists) {
            for (String v : elements) {
                root = root.append(v);
                try {
                    exists = existsDir(vrl);
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
    }
}
