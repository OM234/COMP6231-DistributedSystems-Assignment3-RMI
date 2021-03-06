package storage;

import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    /**
     * Represents the location on the local machine of the root directory
     */
    private File root;
    /**
     * Storage Skeleton of this server
     */
    private Skeleton<Storage> storageSkeleton;
    /**
     * Command Skeleton of this server
     */
    private Skeleton<Command> commandSkeleton;
    /**
     * Storage Stub of this server
     */
    private Storage storageStub;
    /**
     * Command Stub of this server
     */
    private Command commandStub;

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
        storageSkeleton.setInetSocketAddress(inetAddress);
        commandSkeleton.setInetSocketAddress(inetAddress);
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

    /**
     * Deletes the empty folders in the root directory
     *
     * @param root folder in which to recursively delete the empty folders
     */
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
        //used to read file
        FileInputStream fis;
        //to store the read bytes
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
        buffer = new byte[(int)target.length()];

        fis.read(buffer, (int)offset, length);

        return buffer;
    }

    @Override
    public synchronized void write(Path file, long offset, byte[] data)
        throws FileNotFoundException, IOException
    {
        FileOutputStream fos;
        File target = new File(this.root + file.toString());

        if(!target.exists() || target.isDirectory()) {
            throw new FileNotFoundException();
        }
        if(offset < 0) {
            throw new IndexOutOfBoundsException();
        }

        if(offset == 0) {
            fos = new FileOutputStream(target);
            fos.write(data, 0, data.length);
            fos.close();
        } else {
            offsetWrite(target, offset, data);
        }
    }

    /**
     * Writes to a target some data, given an offset
     *
     * @param target to write to
     * @param offset to start write
     * @param data to write
     * @throws IOException if error during write
     */
    private void offsetWrite(File target, long offset, byte[] data) throws IOException {

        FileInputStream fis = new FileInputStream(target);
        FileOutputStream fos;

        int bytesToRead = Math.min(fis.available(), (int)offset);
        byte[] readData = new byte[(int)offset + data.length];

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
        File toCreate;
        String[] components;

        if(file == null) {
            throw new NullPointerException();
        }
        if(file.isRoot()) {
            return false;
        }

        toCreate = new File(this.root + file.toString());
        if(toCreate.exists()){
            return false;
        }

        components = file.toString().substring(1).split("/");
        toCreate = this.root;

        for(int i = 0; i < components.length; i++){

            toCreate = new File(toCreate, components[i]);
            if(!toCreate.exists() && i != components.length-1) {
                toCreate.mkdir();
            } else if(i == components.length-1) {
                try {
                    toCreate.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return true;
    }

    @Override
    public synchronized boolean delete(Path path)
    {
        if(path.isRoot()) return false;

        File toDelete = new File(this.root, path.toString());

        if(!toDelete.exists()) {
            return false;
        }
        if(toDelete.isFile()) {
            return toDelete.delete();
        }

        recursiveFileDelete(toDelete);
        return true;
    }

    /**
     * Deletes a file or folder
     *
     * @param toDelete file/folder to delete
     */

    public void recursiveFileDelete(File toDelete) {

        for(File file : toDelete.listFiles()) {

            if(file == null) return;

            if(file.isDirectory()) {
                recursiveFileDelete(file);
            }
            file.delete();
        }
        toDelete.delete();
    }
}
