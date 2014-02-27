package nl.uva.vlet.vfs.slide.webdavfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.exception.VlIOException;
import nl.uva.vlet.vfs.VDir;
import nl.uva.vlet.vfs.VFSNode;
import nl.uva.vlet.vrl.VRL;

import org.apache.commons.httpclient.HttpException;
import org.apache.webdav.lib.WebdavResource;
import org.apache.webdav.lib.WebdavResources;

/**
 * WebdavDir
 * 
 * @author S. Koulouzis
 */
public class WebdavDir extends VDir
{

    private static ClassLogger logger;

    static
    {
        logger = ClassLogger.getLogger(WebdavDir.class);
        logger.setLevelToError();
    }

    private WebdavResource webdavResource;

    private WebdavFileSystem webdavFileSystem;

    public WebdavDir(WebdavFileSystem webdavFileSystem, VRL vrl, WebdavResource webdavResource)
    {
        super(webdavFileSystem, vrl);
        this.webdavResource = webdavResource;
        this.webdavFileSystem = webdavFileSystem;
    }

    @Override
    public VFSNode[] list() throws VlException
    {
        WebdavResources child;
        try
        {
            child = webdavResource.getChildResources();
            Enumeration<org.apache.webdav.lib.WebdavResource> resourceEnum = child.getResources();

            ArrayList<VFSNode> nodes = new ArrayList<VFSNode>();
            while (resourceEnum.hasMoreElements())
            {
                org.apache.webdav.lib.WebdavResource childResoure = (WebdavResource) resourceEnum.nextElement();

                VRL vrl = webdavFileSystem.HttpURL2Vrl(childResoure.getHttpURL());
                VFSNode node = webdavFileSystem.createNode(childResoure, vrl);
                nodes.add(node);
            }

            VFSNode[] nodesArray = new VFSNode[nodes.size()];
            nodesArray = nodes.toArray(nodesArray);

            return nodesArray;

        }
        catch (HttpException e)
        {
            throw new VlException(e);
        }
        catch (IOException e)
        {
            throw new VlIOException(e);
        }
    }

    @Override
    public boolean create(boolean ignoreExisting) throws VlException
    {
        VDir dir = webdavFileSystem.createDir(getVRL(), ignoreExisting);
        return (dir != null);
    }

    @Override
    public boolean exists() throws VlException
    {
        return webdavResource.exists();
    }

    @Override
    public long getModificationTime() throws VlException
    {
        return webdavResource.getGetLastModified();
    }

    @Override
    public boolean isReadable() throws VlException
    {
        return false;
    }

    @Override
    public boolean isWritable() throws VlException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public VRL rename(String newNameOrPath, boolean nameIsPath) throws VlException
    {
        VRL destination = null;
        if (nameIsPath || (newNameOrPath.startsWith("/")))
        {
            destination = getVRL().copyWithNewPath(newNameOrPath);
        }
        else
        {
            destination = getVRL().getParent().append(newNameOrPath);
        }

        return webdavFileSystem.move(getVRL(), destination, false);
    }

    public long getNrOfNodes() throws VlException
    {
        return webdavResource.list().length;
    }

    public boolean delete(boolean recurse) throws VlException
    {
        return webdavFileSystem.delete(getVRL(), recurse);
    }

}
