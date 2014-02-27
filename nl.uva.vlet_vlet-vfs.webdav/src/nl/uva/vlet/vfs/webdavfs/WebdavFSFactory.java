package nl.uva.vlet.vfs.webdavfs;

import nl.uva.vlet.Global;
import nl.uva.vlet.data.VAttribute;
import nl.uva.vlet.data.VAttributeConstants;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.vfs.VFSFactory;
import nl.uva.vlet.vfs.VFileSystem;
import nl.uva.vlet.vrl.VRL;
import nl.uva.vlet.vrs.ServerInfo;
import nl.uva.vlet.vrs.VRSContext;

/**
 * WebdavFSFactory
 * 
 * @author S. Koulouzis
 */
public class WebdavFSFactory extends VFSFactory
{
    public static final String[] schemes = { "webdav","webdavs" };

//    @Override
//    public VFileSystem getFileSystem(VRSContext context, VRL location) throws VlException
//    {
//        WebdavFileSystem webdevClient = WebdavFileSystem.getClientFor(context, location);
//        webdevClient.connect();
//
//        return webdevClient;
//    }

    @Override
    public void clear()
    {
        WebdavFileSystem.clearClass();
    }

    @Override
    public String getName()
    {
        return "WebDav";
    }

    @Override
    public String[] getSchemeNames()
    {
        return schemes;
    }

    protected ServerInfo updateServerInfo(VRSContext context, ServerInfo info, VRL loc) throws VlException
    {
        // create defaults:
        if (info == null)
        {
            info = ServerInfo.createFor(context, loc);
        }

        VAttribute attr = info.getAttribute(VAttributeConstants.ATTR_USERNAME);

        if (attr == null)
        {
            attr = new VAttribute(VAttributeConstants.ATTR_USERNAME, Global.getUsername());
            info.setAttribute(attr);
        }
        
        info.setIfNotSet(WebdavConst.ATTR_ENABLE_HTTPAUTH_BASIC, true); 
        
        info.setUsePasswordAuth(); 
        // Still Needed ? 
        info.store(); 
        
        return info;
    }

	@Override
	public VFileSystem createNewFileSystem(VRSContext context, ServerInfo info,
			VRL location) throws VlException 
	{
		return new WebdavFileSystem(context,info,location); 
	}
}
