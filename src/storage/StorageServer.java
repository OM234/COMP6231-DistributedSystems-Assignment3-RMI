package storage;

import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.util.Arrays;

import common.*;
import rmi.*;
import naming.*;

/** Storage server.

    <p>
    Storage servers respond to client file access requests. The files accessible
    through a storage server are those accessible under a given directory of the
    local filesystem.
 */
public class StorageServer implements Storage, Command
{
    private File root;
    private Skeleton<Storage> storageSkeleton;
    private Skeleton<Command> commandSkeleton;
    private Storage storageStub;
    private Command commandStub;
    private static int portNum = 501;


    /** Creates a storage server, given a directory on the local filesystem.

        @param root Directory on the local filesystem. The contents of this
                    directory will be accessible through the storage server.
        @throws NullPointerException If <code>root</code> is <code>null</code>.
    */
    public StorageServer(File root)
    {
        if(root == null) {
            throw new NullPointerException("root is null");
        }

       this.root = root;
       storageSkeleton = new Skeleton<>(Storage.class, this);
       commandSkeleton = new Skeleton<>(Command.class, this);
    }

    /** Starts the storage server and registers it with the given naming
        server.

        @param hostname The externally-routable hostname of the local host on
                        which the storage server is running. This is used to
                        ensure that the stub which is provided to the naming
                        server by the <code>start</code> method carries the
                        externally visible hostname or address of this storage
                        server.
        @param naming_server Remote interface for the naming server with which
                             the storage server is to register.
        @throws UnknownHostException If a stub cannot be created for the storage
                                     server because a valid address has not been
                                     assigned.
        @throws FileNotFoundException If the directory with which the server was
                                      created does not exist or is in fact a
                                      file.
        @throws RMIException If the storage server cannot be started, or if it
                             cannot be registered.
     */
    public synchronized void start(String hostname, Registration naming_server)
        throws RMIException, UnknownHostException, FileNotFoundException
    {
        StartSkeletons(hostname);
        createStubs();
        deleteRepeatFileAndEmptyFolders(naming_server);
    }

    /** Creates a new InetAddress with the hostname. Sets the InetSocketAddress
     *  of the storage and command skeletons. Starts these skeletons with different
     *  ports
     *
     *  @param hostname
     *  @throws UnknownHostException
     *  @throws RMIException
     */
    private void StartSkeletons(String hostname) throws UnknownHostException, RMIException {

        InetAddress inetAddress = InetAddress.getByName(hostname);
        storageSkeleton.setInetSocketAddress(new InetSocketAddress(inetAddress, portNum++));
        commandSkeleton.setInetSocketAddress(new InetSocketAddress(inetAddress, portNum++));
        storageSkeleton.start();
        commandSkeleton.start();
    }

    /** Creates the storage and command stubs.
     *
     *  @throws UnknownHostException
     */
    private void createStubs() throws UnknownHostException {
        storageStub = Stub.create(Storage.class, storageSkeleton);
        commandStub = Stub.create(Command.class, commandSkeleton);
    }

    /** Deletes repeat files, as commanded from the naming server. Deletes empty folders
     *
     * @param naming_server
     * @throws RMIException
     * @throws FileNotFoundException
     */

    private void deleteRepeatFileAndEmptyFolders(Registration naming_server)
            throws RMIException, FileNotFoundException {

        Path[] toDelete = naming_server.register(storageStub, commandStub, Path.list(root));
        //delete the files indicated by the naming server
        Arrays.stream(toDelete).forEach(this::delete);
        deleteEmptyFolders(root);
    }

    private void deleteEmptyFolders(File root) {

        if(root.isDirectory()) {
            Arrays.stream(root.listFiles()).forEach(this::deleteEmptyFolders);
            if(root.list().length == 0){
                root.delete();
            }
        }
    }


    /** Stops the storage server.

        <p>
        The server should not be restarted.
     */
    public void stop()
    {
        storageSkeleton.stop();
        stopped(null);
    }

    /** Called when the storage server has shut down.

        @param cause The cause for the shutdown, if any, or <code>null</code> if
                     the server was shut down by the user's request.
     */
    protected void stopped(Throwable cause)
    {
    }

    // The following methods are documented in Storage.java.
    @Override
    public synchronized long size(Path file) throws FileNotFoundException
    {
        //System.out.println(this.root + file.toString());
        File target = new File(this.root + file.toString());

        if(!target.exists() || target.isDirectory()) {
            throw new FileNotFoundException("file not found");
        }

        return target.length();
    }

    @Override
    public synchronized byte[] read(Path file, long offset, int length)
        throws FileNotFoundException, IOException
    {
        FileInputStream fis;
        ObjectInputStream ois;
        byte[] buffer;
        File target = new File(this.root + file.toString());

        if(!target.exists() || target.isDirectory()) {
            throw new FileNotFoundException();
        }
        if(length < 0 || offset < 0 || offset + length > target.length()) {
            throw new IndexOutOfBoundsException();
        }

        if(target.length() == 0){
            return new byte[]{};
        }

        fis = new FileInputStream(target);
        //ois = new ObjectInputStream(fis);
        buffer = new byte[(int)target.length()];

        fis.read(buffer, (int)offset, length);

        return buffer;
    }

    @Override
    public synchronized void write(Path file, long offset, byte[] data)
        throws FileNotFoundException, IOException
    {
        ObjectOutputStream oos;
        FileOutputStream fos;
        File target = new File(this.root + file.toString());

        if(!target.exists() || target.isDirectory()) {
            throw new FileNotFoundException();
        }
        if(offset < 0) {
            throw new IndexOutOfBoundsException();
        }

        //oos = new ObjectOutputStream(fos);
        if(offset == 0) {
            fos = new FileOutputStream(target);
            fos.write(data, 0, data.length);
            fos.close();
        } else {
            offsetWrite(file, target, offset, data);
        }
        //oos.close();
    }

    private void offsetWrite(Path file, File target, long offset, byte[] data) throws IOException {

        FileInputStream fis = new FileInputStream(target);
        FileOutputStream fos;
        //ObjectInputStream ois = new ObjectInputStream(fis);

        System.out.println(fis.available());
        int bytesToRead = Math.min(fis.available(), (int)offset);
        byte[] readData = new byte[(int)offset + data.length];
        //byte[] newDataRead = new byte[(int)offset + data.length];

        fis.read(readData, 0, bytesToRead);

        for(int i = (int)offset; i < readData.length; i++){
            readData[i] = data[i-(int)offset];
        }

        fos = new FileOutputStream(target);
        fos.write(readData, 0, readData.length);
        fos.close();
    }

    // The following methods are documented in Command.java.
    @Override
    public synchronized boolean create(Path file)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public synchronized boolean delete(Path path)
    {
        if(path.isRoot()) return false;

        File toDelete = new File(this.root, path.toString());

        return toDelete.delete();
    }
}
