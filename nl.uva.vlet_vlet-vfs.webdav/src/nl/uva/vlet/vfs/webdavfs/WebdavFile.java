package nl.uva.vlet.vfs.webdavfs;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.data.StringList;
import nl.uva.vlet.data.VAttribute;
import nl.uva.vlet.exception.VRLSyntaxException;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.vfs.VFSNode;
import nl.uva.vlet.vfs.VFSTransfer;
import nl.uva.vlet.vfs.VFile;
import nl.uva.vlet.vrl.VRL;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;

/**
 * WebdavFile
 *
 * @author S. Koulouzis, Piter T. de Boer
 */
public class WebdavFile extends VFile {

    private static ClassLogger logger;

    static {
        logger = ClassLogger.getLogger(WebdavFile.class);
        logger.setLevelToError();
    }
    // == instance === 
    private DavPropertySet _davProps;
    private WebdavFileSystem webdavfs;

    /**
     * Creates a WebdavFile
     *
     * @param webdavFSystem the file system
     * @param vrl the vrl pointing of the resource
     * @param davPropSet the property set
     */
    public WebdavFile(WebdavFileSystem webdavFSystem, VRL vrl, DavPropertySet props) {
        super(webdavFSystem, vrl);
        this._davProps = props;
        this.webdavfs = webdavFSystem;
    }

    public String[] getAttributeNames() {
        String attrs[] = super.getAttributeNames();
        if (_davProps == null) {
            return attrs;
        }

        StringList atrList = new StringList(attrs);

        DavPropertyName[] propNames = null;
        propNames = this._davProps.getPropertyNames();

        for (int i = 0; i < propNames.length; i++) {
            atrList.add(propNames[i].getName());
        }

        return atrList.toArray();
    }

    public VAttribute getAttribute(String name) throws VlException {
        DavProperty<?> prop = getDavProperty(name);

        if (prop != null) {
            return new VAttribute(name, "" + prop.getValue());
        } else {
            return super.getAttribute(name);
        }
    }

    public DavProperty<?> getDavProperty(String name) throws VlException {
        this._davProps = this.getDavProperties();

        if (_davProps == null) {
            return null;
        }

        DavProperty<?> prop = _davProps.get(name);
        return prop;
    }

    @Override
    public boolean exists() throws VlException {

        ArrayList<VFSNode> result = webdavfs.propFind(getVRL(), DavConstants.PROPFIND_PROPERTY_NAMES,
                DavConstants.DEPTH_0);
        boolean exists = (result != null && !result.isEmpty());

        if (exists) {
            VFSNode node = result.get(0);
            if (node instanceof WebdavFile) {
                WebdavFile file = (WebdavFile) result.get(0);
                this._davProps = file._davProps;
            } else {
                return false;
            }
        }

        return exists;
    }

    protected DavPropertySet getDavProperties() throws VlException {
        if (_davProps != null && _davProps.getPropertyNames().length > 0) {
            return _davProps;
        }
        _davProps = webdavfs.getProperties(getVRL());
        return _davProps;
    }

    @Override
    public long getLength() throws VlException {
        return this.webdavfs.getLength(this.getDavProperties());
    }

    @Override
    public boolean create(boolean ignoreExisting) throws VlException {
        VFile file = webdavfs.createFile(getVRL(), ignoreExisting);
        return (file != null);
    }

    @Override
    public long getModificationTime() throws VlException {
        String modstr = this.webdavfs.getModificationTimeString(this.getDavProperties());
        if (modstr == null) {
            return -1;
        }
        // convert to millis since epoch. 
        return webdavfs.createDateFromString(modstr).getTime();
    }

    @Override
    public boolean isReadable() throws VlException {
        return webdavfs.isReadable(this.getDavProperties(), true);
    }

    @Override
    public boolean isWritable() throws VlException {
        return webdavfs.isWritable(this.getDavProperties(), true);
    }

    @Override
    public VRL rename(String newNameOrPath, boolean nameIsPath) throws VlException {
        VRL destination = null;
        if (nameIsPath || (newNameOrPath.startsWith("/"))) {
            destination = getVRL().copyWithNewPath(newNameOrPath);
        } else {
            destination = getVRL().getParent().append(newNameOrPath);
        }

        return webdavfs.move(getVRL(), destination, false);
    }

    @Override
    public InputStream getInputStream() throws VlException {
        return webdavfs.getInputStream(getVRL());
    }

    @Override
    public OutputStream getOutputStream() throws VlException {
        return webdavfs.getOutputStream(getVRL());
    }

    @Override
    public boolean delete() throws VlException {
        return webdavfs.delete(getVRL(), true);
    }

    @Override
    protected void uploadFrom(VFSTransfer transferInfo, VFile localSource) throws VlException, VRLSyntaxException {
        this.webdavfs.uploadFile(transferInfo, localSource, getVRL());
    }

    public void uploadFrom(VFile localSource) throws VlException, VRLSyntaxException {
        this.webdavfs.uploadFile(null, localSource, getVRL());
    }
}
