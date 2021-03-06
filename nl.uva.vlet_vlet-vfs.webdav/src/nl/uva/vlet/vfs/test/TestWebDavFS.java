package nl.uva.vlet.vfs.test;

import java.util.logging.Level;
import static junit.framework.Assert.fail;
import nl.uva.vlet.ClassLogger;
import nl.uva.vlet.Global;
import nl.uva.vlet.exception.ResourceAlreadyExistsException;
import nl.uva.vlet.exception.ResourceCreationFailedException;
import nl.uva.vlet.exception.ResourceException;
import nl.uva.vlet.exception.VRLSyntaxException;
import nl.uva.vlet.exception.VlException;
import nl.uva.vlet.vfs.VDir;
import nl.uva.vlet.vfs.VFSClient;
import nl.uva.vlet.vfs.VFSNode;
import nl.uva.vlet.vfs.VFile;
import nl.uva.vlet.vfs.VFileSystem;
import static nl.uva.vlet.vfs.test.VTestCase.verbose;
import nl.uva.vlet.vfs.webdavfs.WebdavDir;
import nl.uva.vlet.vrl.VRL;
import nl.uva.vlet.vrs.ServerInfo;
import nl.uva.vlet.vrs.VNode;
import nl.uva.vlet.vrs.VRS;
import nl.uva.vlet.vrs.VRSContext;

public class TestWebDavFS {

    private static ClassLogger logger;
    private static String userName;
    private static String password;
    private static VRL vrl;
    private static VFSClient client;

    static {
        logger = ClassLogger.getLogger(TestWebDavFS.class);
        logger.setLevelToError();
    }
    private static final String TEST_CONTENTS = ">>> This is a testfile used for the VFS unit tests  <<<\n"
            + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\n" + "0123456789@#$%*()_+\n"
            + "Strange characters:...<TODO>\n" + "UTF8:<TODO>\n" + "\n --- You Can Delete this File ---\n";

    public static void main(String[] args) {
        try {
            vrl = new VRL(args[0]);

            client = new VFSClient();
            VRSContext context = client.getVRSContext();

            ServerInfo info = ServerInfo.createFor(context, vrl);

            info.setUsername(args[1]);
            info.setPassword(args[2]);

            info.store();

            Global.init();
            VRS.getRegistry().addVRSDriverClass(nl.uva.vlet.vfs.webdavfs.WebdavFSFactory.class);

//            testExitsts();
//
//            testList();

//            testCreateFile();
//
//            testDelete();
//
//            testRename();
//
//            testGetLen();

            testZExceptionsExistingDir();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            VRS.exit();
        }
    }

    private static void testGetLen() throws VRLSyntaxException, VlException {
        VDir testDir = client.createDir(vrl.append("Test"), true);

        VFileSystem fs = testDir.getFileSystem();
        VFile newFile = fs.newFile(testDir.resolvePathVRL("testFile7"));

        newFile.setContents(TEST_CONTENTS);

        long newLen = newFile.getLength();

        logger.debugPrintf("After setting contents, size may NOT be zero %s", newLen);

    }

    private static void testRename() throws VlException {
        VDir testDir = client.createDir(vrl.append("Test"), true);

        VDir newDir = testDir.createDir("NewDir", true);

        boolean result = newDir.renameTo("newDirName", false);

        logger.debugPrintf("Rename must return true????%s\n", result);

        result = testDir.existsDir("newDirName");

        logger.debugPrintf("New VDir doesn't exist:????%s\n", result);

        VDir renamedDir = testDir.getDir("newDirName");
        logger.debugPrintf("After rename, new VDir is NULL:????%s\n", renamedDir);

        // cleanup:
        renamedDir.delete();

    }

    private static void testList() throws VRLSyntaxException, VlException {

        VNode node = client.getNode(vrl);

        if (node instanceof VDir) {
            WebdavDir dir = (WebdavDir) node;

            VFSNode[] files = dir.list();

            for (int i = 0; i < files.length; i++) {
//                logger.debugPrintf("Name: %s\n", files[i].getName());
                logger.log(Level.INFO, files[i].getName());
                System.err.println(files[i].getName());
            }
        }
    }

    private static void testCreateFile() throws VlException {

        VDir testDir = client.createDir(vrl.append("Test"), true);
        logger.debugPrintf("newFile exists????%s\n", testDir.exists());
        VFile newFile = testDir.createFile(("testFile"));

        logger.debugPrintf("newFile is NULL????%s\n", newFile.getName());

        logger.debugPrintf("newFile exists????%s\n", newFile.exists());


        logger.debugPrintf("Len  is 0????%s\n", newFile.getLength());

        newFile.delete();
    }

    private static void testDelete() throws VlException {

        VDir testDir = client.createDir(vrl.append("Test"), true);

        VFile newFile = testDir.createFile(("testFile"));

        logger.debugPrintf("newFile is NULL????%s\n", newFile.getName());

        logger.debugPrintf("Len  is 0????%s\n", newFile.getLength());

        newFile.delete();
        logger.debugPrintf("newFile exists????%s\n", newFile.exists());

        newFile = testDir.newFile("testFile1b");
        newFile.create(); // use default creat();

        // sftp created 1-length new files !
        logger.debugPrintf("newFile is NUL????%s\n", newFile.getName());
        logger.debugPrintf("Len  is 0????%s\n", newFile.getLength());

        newFile.delete();
        logger.debugPrintf("newFile exists????%s\n", newFile.exists());
    }

    private static void testExitsts() throws VlException {
        boolean exitsts = client.existsFile(vrl);
        System.err.println(vrl + " exists: " + exitsts);
    }

    private static void testZExceptionsExistingDir() throws VlException {
        verbose(1, "testZExceptionsExistingDir");
        VDir testDir = client.createDir(vrl.append("Test"), true);

        VDir newDir = testDir.createDir("testExistingDir2");

        try {
            // create and do NOT ignore:
            newDir = testDir.createDir("testExistingDir2", false);
            newDir.delete();
            fail("createDir(): Should raise Exception:"
                    + ResourceAlreadyExistsException.class);
        } catch (ResourceAlreadyExistsException e) {
            Global.debugPrintStacktrace(e);
        }

        newDir.delete();
        VFile newFile = testDir.createFile("testExistingFile2");

        try {
            // create Dir and do NOT ignore existing File or Dir:
            newDir = testDir.createDir("testExistingFile2", false);
            newDir.delete();
            fail("createDir(): Should raise Exception:"
                    + ResourceAlreadyExistsException.class + " or "
                    + ResourceCreationFailedException.class);
        } // also allowed as the intended resource doesn't exists as exactly
        // the same type: existing Directory is not the intended File
        catch (ResourceCreationFailedException | ResourceAlreadyExistsException e) {
            Global.debugPrintStacktrace(e);
        } catch (ResourceException e) {
            fail("createDir(): Although a resource execption is better then any other,"
                    + "this unit test expects either:"
                    + ResourceAlreadyExistsException.class
                    + " or "
                    + ResourceCreationFailedException.class);
            // Global.debugPrintStacktrace(e);
        }

        newFile.delete();
    }
}
