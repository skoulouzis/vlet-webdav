package nl.uva.vlet.vfs.slide.webdavfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.exception.VlIOException;
import nl.uva.vlet.vfs.VFile;
import nl.uva.vlet.vrl.VRL;

import org.apache.commons.httpclient.HttpException;
import org.apache.webdav.lib.Ace;
import org.apache.webdav.lib.WebdavResource;
import org.apache.webdav.lib.properties.AclProperty;

/**
 * WebdavFile
 * 
 * @author S. Koulouzis, Piter T. de Boer
 */
public class WebdavFile extends VFile
{
    private static ClassLogger logger;

    static
    {
        logger = ClassLogger.getLogger(WebdavFile.class);
        logger.setLevelToError();
    }

    // === Instance === 
    
    private WebdavResource webdavResource;

    private WebdavFileSystem webdavFileSystem;
    
    public WebdavFile(WebdavFileSystem webdavFileSystem, VRL vrl, WebdavResource webdavResource)
    {
        super(webdavFileSystem, vrl);
        this.webdavResource = webdavResource;
        this.webdavFileSystem = webdavFileSystem;
    }

    @Override
    public boolean exists() throws VlException
    {
        return webdavResource.exists();
    }

    @Override
    public long getLength() throws VlException
    {
        return webdavResource.getGetContentLength();
    }

    @Override
    public boolean create(boolean ignoreExisting) throws VlException
    {
        VFile file = webdavFileSystem.createFile(getVRL(), ignoreExisting);
        return (file != null);
    }

    @Override
    public long getModificationTime() throws VlException
    {
        return webdavResource.getGetLastModified();
    }

    @Override
    public boolean isReadable() throws VlException
    {
        try
        {
            AclProperty res = webdavResource.aclfindMethod(getVRL().getPath());
            if (res != null)
            {
                Ace[] ace = res.getAces();

                if (ace != null || ace.length > 1)
                {
                    for (int i = 0; i < ace.length; i++)
                    {
                        logger.debugPrintf("ACL: %s\n", ace[i].getPrincipal());
                    }
                }
            }

        }
        catch (HttpException e)
        {
            throw new VlException(e);
        }
        catch (IOException e)
        {
            throw new VlIOException(e);
        }
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

    public InputStream getInputStream() throws VlException
    {
        try
        {
            return webdavResource.getMethodData();
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

    public OutputStream getOutputStream() throws VlException
    {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean delete() throws VlException
    {
        return webdavFileSystem.delete(getVRL(), true);
    }

}
