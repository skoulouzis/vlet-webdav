package nl.uva.vlet.vfs.webdavfs;

import java.util.ArrayList;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.data.StringList;
import nl.uva.vlet.data.VAttribute;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.vfs.VDir;
import nl.uva.vlet.vfs.VFSNode;
import nl.uva.vlet.vfs.VFileSystem;
import nl.uva.vlet.vrl.VRL;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;

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
        logger.setLevelToDebug();
    }

    // === instance === 
    // 
    // ================
    
    private DavPropertySet _davProps;

    private WebdavFileSystem webdavfs;

    /**
     * Creates a WebdavDir
     * 
     * @param webdavFSystem
     *            the file system
     * @param vrl
     *            the vrl pointing of the resource
     * @param davPropSet
     *            the property set
     */
    public WebdavDir(VFileSystem webdavFSystem, VRL vrl, DavPropertySet davPropSet)
    {
        super(webdavFSystem, vrl);
        this._davProps = davPropSet;
        this.webdavfs = (WebdavFileSystem) webdavFSystem;
    }

    protected DavPropertySet getDavProperties() throws VlException
    {
        if (_davProps!=null)
            return _davProps;  
        
        _davProps = webdavfs.getProperties(getVRL());
        return _davProps; 
    }
    
    public DavProperty<?> getDavProperty(String name) throws VlException 
    {
        this._davProps=this.getDavProperties(); 
        
        if (_davProps==null)
            return null; 
          
        DavProperty<?> prop = _davProps.get(name);
        return prop; 
    }

    public String[] getAttributeNames()
    {
        String attrs[]=super.getAttributeNames();
        if (_davProps==null)
            return attrs; 
                    
        StringList atrList=new StringList(attrs);
        
        DavPropertyName[] propNames = null;
        propNames=this._davProps.getPropertyNames();
        
        for (int i=0;i<propNames.length;i++)
            atrList.add(propNames[i].getName());
        
        return atrList.toArray(); 
    }
    
    public VAttribute getAttribute(String name) throws VlException
    {
        DavProperty<?> prop = this.getDavProperty(name);
        if (prop!=null)
            return new VAttribute(name,""+prop.getValue());
        
        return super.getAttribute(name); 
    }
    
    @Override
    public VFSNode[] list() throws VlException
    {        
        ArrayList<VFSNode> nodes = webdavfs.propFind(getVRL(), DavConstants.PROPFIND_ALL_PROP_INCLUDE,
                DavConstants.DEPTH_1);

        // get rid of this node
        if (nodes==null)
            return null;
        
        nodes.remove(0);

        VFSNode[] nodesArray = new VFSNode[nodes.size()];
        nodesArray = nodes.toArray(nodesArray);
        return nodesArray;
    }

    @Override
    public boolean create(boolean ignoreExisting) throws VlException
    {
        VDir dir = webdavfs.createDir(getVRL(), ignoreExisting);

        return (dir != null);
    }

    // @Override
    // public WebdavFile createFile(String fileName,boolean ignoreExisting)
    // throws VlException
    // {
    // WebdavFile file = webdavFSystem.createFile(getVRL().append(fileName),
    // ignoreExisting);
    //        
    // return file;
    // }

    @Override
    public boolean exists() throws VlException
    {
        ArrayList<VFSNode> result = webdavfs.propFind(getVRL(), DavConstants.PROPFIND_PROPERTY_NAMES,
                DavConstants.DEPTH_0);

        return (result != null && !result.isEmpty());
    }

    @Override
    public long getModificationTime() throws VlException
    {
        String modstr=this.webdavfs.getModificationTimeString(this.getDavProperties()); 
        if (modstr==null)
            return -1; 
        // convert to millis since epoch. 
        return webdavfs.createDateFromString(modstr).getTime();
    }
    
    @Override
    public boolean isReadable() throws VlException
    {
        return this.webdavfs.isReadable(this.getDavProperties(),false); 
    }

    @Override
    public boolean isWritable() throws VlException
    {
        return this.webdavfs.isWritable(this.getDavProperties(),false); 
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

        return webdavfs.move(getVRL(), destination, false);
    }

    public long getNrOfNodes() throws VlException
    {
        ArrayList<VFSNode> result = webdavfs.propFind(getVRL(), DavConstants.PROPFIND_ALL_PROP_INCLUDE,
                DavConstants.DEPTH_1);

        // get rid of this node
        result.remove(0);

        return result.size();
    }

    @Override
    public boolean delete(boolean recurse) throws VlException
    {
        return webdavfs.delete(getVRL(), recurse);
    }

}
