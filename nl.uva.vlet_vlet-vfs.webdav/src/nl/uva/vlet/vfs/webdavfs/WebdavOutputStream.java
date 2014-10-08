package nl.uva.vlet.vfs.webdavfs;

import java.io.IOException;
import java.io.OutputStream;

import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.GlobalConfig;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.vfs.VDir;
import nl.uva.vlet.vfs.VFSClient;
import nl.uva.vlet.vfs.VFile;
import nl.uva.vlet.vrl.VRL;

/**
 * OutputStream for changing files on webdav servers.
 *
 * @author S. koulouzis
 *
 */
public class WebdavOutputStream extends OutputStream {

    private static ClassLogger logger;

    static {
        logger = ClassLogger.getLogger(WebdavOutputStream.class);
        logger.setLevelToError();
    }
    // === Instance === 
    private WebdavFileSystem webdavfs;
    private VRL tmpDir;
    private OutputStream localOS;
    private VFile localFile;
    private WebdavFile remoteFile;

    /**
     * Creates an instance of an OutputStream. On creation a temporary local
     * file is crated, and its OutputStream is used. On closing this stream the
     * file uploaded to the server and the temporary file deleted.
     *
     * @param webdavFileSystem
     * @param remoteFile
     * @throws VlException
     */
    public WebdavOutputStream(WebdavFileSystem webdavFileSystem, WebdavFile remoteFile) throws VlException {
        this.webdavfs = webdavFileSystem;

        tmpDir = GlobalConfig.getDefaultTempDir();
        // SP: Maybe lock the file and on close unlock it
        this.remoteFile = remoteFile;

        // if (remoteFile.exists())
        // {
        // localFile = remoteFile.copyToDir(tmpDir);
        // }
        // else
        // {
        VFSClient vclient = new VFSClient(webdavFileSystem.getContext());

        VDir dir = vclient.getDir(tmpDir);

        localFile = dir.createFile(remoteFile.getName());
        // }

        localOS = localFile.getOutputStream();
    }

    @Override
    public void write(int b) throws IOException {
        localOS.write(b);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        localOS.write(b, off, len);
    }

    @Override
    public void write(byte b[]) throws IOException {
        localOS.write(b);
    }

    @Override
    public void flush() throws IOException {
        localOS.flush();
    }

    @Override
    public void close() throws IOException {
        localOS.close();
        try {
            webdavfs.upload(localFile.getVRL(), remoteFile.getVRL());
            localFile.delete();
        } catch (VlException e) {
            throw new IOException(e);
        }

    }
}
