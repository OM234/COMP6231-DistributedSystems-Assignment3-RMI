package naming;

import common.Path;
import rmi.RMIException;
import rmi.Skeleton;
import storage.Command;
import storage.Storage;

import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Naming server.
 *
 * <p>
 * Each instance of the filesystem is centered on a single naming server. The
 * naming server maintains the filesystem directory tree. It does not store any
 * file data - this is done by separate storage servers. The primary purpose of
 * the naming server is to map each file name (path) to the storage server
 * which hosts the file's contents.
 *
 * <p>
 * The naming server provides two interfaces, <code>Service</code> and
 * <code>Registration</code>, which are accessible through RMI. Storage servers
 * use the <code>Registration</code> interface to inform the naming server of
 * their existence. Clients use the <code>Service</code> interface to perform
 * most filesystem operations. The documentation accompanying these interfaces
 * provides details on the methods supported.
 *
 * <p>
 * Stubs for accessing the naming server must typically be created by directly
 * specifying the remote network address. To make this possible, the client and
 * registration interfaces are available at well-known ports defined in
 * <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration {

    /**
     * A map of the directory structure of this NamingServer.
     */
    static DirectoryMap directoryMap;
    /**
     * Contains all of the Storage stubs which have registered with this NamingServer
     */
    static Set<Storage> storageStubs;
    /**
     * Contains all of the Command stubs which have registered with this NamingServer
     */
    static Set<Command> commandStubs;
    Skeleton<Service> serviceSkeleton;
    Skeleton<Registration> registrationSkeleton;

    /**
     * Creates the naming server object.
     *
     * <p>
     * The naming server is not started.
     */
    public NamingServer() {
        storageStubs = new HashSet<>();
        commandStubs = new HashSet<>();
        directoryMap = new DirectoryMap();
    }

    /**
     * Starts the naming server.
     *
     * <p>
     * After this method is called, it is possible to access the client and
     * registration interfaces of the naming server remotely.
     *
     * @throws RMIException If either of the two skeletons, for the client or
     *                      registration server interfaces, could not be
     *                      started. The user should not attempt to start the
     *                      server again if an exception occurs.
     */
    public synchronized void start() throws RMIException {

        //This serviceSkeleton is assigned a value, given localHost address
        serviceSkeleton = new Skeleton<>(
                Service.class, new NamingServer(), new InetSocketAddress("127.0.0.1", NamingStubs.SERVICE_PORT));
        //This registrationSkeleton is assigned a value, given localHost address
        registrationSkeleton = new Skeleton<>(
                Registration.class, new NamingServer(), new InetSocketAddress("127.0.0.1", NamingStubs.REGISTRATION_PORT));

        //Start this skeletons
        serviceSkeleton.start();
        registrationSkeleton.start();
    }

    /**
     * Stops the naming server.
     *
     * <p>
     * This method waits for both the client and registration interface
     * skeletons to stop. It attempts to interrupt as many of the threads that
     * are executing naming server code as possible. After this method is
     * called, the naming server is no longer accessible remotely. The naming
     * server should not be restarted.
     */
    public void stop() {

        //Stop this skeletons
        serviceSkeleton.stop();
        registrationSkeleton.stop();
        stopped(null);
    }

    /**
     * Indicates that the server has completely shut down.
     *
     * <p>
     * This method should be overridden for error reporting and application
     * exit purposes. The default implementation does nothing.
     *
     * @param cause The cause for the shutdown, or <code>null</code> if the
     *              shutdown was by explicit user request.
     */
    protected void stopped(Throwable cause) {
    }

    // The following methods are documented in Service.java.
    @Override
    public boolean isDirectory(Path path) throws FileNotFoundException {
        if (path == null) {
            throw new NullPointerException("Path for isDirectory cannot be null");
        }
        if (!directoryMap.pathExists(path)) {
            throw new FileNotFoundException("directory not found");
        }

        return directoryMap.isFolder(path);
    }

    @Override
    public String[] list(Path directory) throws FileNotFoundException {

        if (directory == null) {
            throw new NullPointerException("Path for list method cannot be null");
        }
        if (!directoryMap.pathExists(directory)) {
            throw new FileNotFoundException("directory not found");
        }
        if (!directoryMap.isFolder(directory)) {
            throw new FileNotFoundException("not a directory");
        }

        return directoryMap.list(directory);
    }

    @Override
    public boolean createFile(Path file)
            throws RMIException, FileNotFoundException {

        if (file == null) {
            throw new NullPointerException("file cannot be null");
        }
        if (!directoryMap.parentExists(file)) {
            throw new FileNotFoundException("Parent Directory does not exist");
        }
        if (directoryMap.pathExists(file)) {
            return false;
        }

        //Command one the Storage servers to create the file
        commandStubs.iterator().next().create(file);

        //Add the path to the directoryMap, given the associated storage stub
        return directoryMap.addPath(file, storageStubs.iterator().next(), commandStubs.iterator().next());
    }

    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException {

        if (directory == null) {
            throw new NullPointerException("directory cannot be null");
        }
        if (directoryMap.pathExists(directory)) {
            return false;
        }
        if (!directoryMap.parentExists(directory)) {
            throw new FileNotFoundException("Parent Directory does not exist");
        }

        //Add the path to the directoryMap, given the associated storage stub
        return directoryMap.addPathDirectory(directory, storageStubs.iterator().next(), commandStubs.iterator().next());
    }

    @Override
    public boolean delete(Path path) throws FileNotFoundException {

        if(path == null) {
            throw new NullPointerException();
        }
        if(!directoryMap.pathExists(path)) {
            throw new FileNotFoundException();
        }

        try {
            directoryMap.getCommandStub(path).delete(path);
            directoryMap.deletePath(path);
            return true;
        } catch (RMIException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Storage getStorage(Path file) throws FileNotFoundException {

        if (file == null) {
            throw new NullPointerException();
        }
        if (!directoryMap.pathExists(file)) {
            throw new FileNotFoundException();
        }
        if (directoryMap.isFolder(file)) {
            throw new FileNotFoundException("No storage stub with a directory");
        }

        return directoryMap.getStorageStub(file);
    }

    // The method register is documented in Registration.java.
    @Override
    public Path[] register(Storage client_stub, Command command_stub,
                           Path[] files) {

        if (client_stub == null || command_stub == null || files == null) {
            throw new NullPointerException("Cannot pass null parameters");
        }
        if (storageStubs.contains(client_stub) || commandStubs.contains(command_stub)) {
            throw new IllegalStateException("Storage server already registered");
        }

        //Add storage and command stubs to this Set
        storageStubs.add(client_stub);
        commandStubs.add(command_stub);

        //Get the Paths to Delete
        Path[] toDelete = getToDeleteArr(files);

        //For each of the paths to be registered, remove the Paths to be deleted, then add them to the directoryMap
        Arrays.stream(files)
                .filter(file -> Arrays.stream(toDelete).noneMatch(toDelFile -> toDelFile == file))
                .forEach(file -> directoryMap.addPath(file, client_stub, command_stub));

        return toDelete;
    }

    /**
     * Returns the Paths to be deleted during registration
     *
     * @param files to be registered
     * @return the Paths to be deleted, which are the Paths which already exist
     */
    private Path[] getToDeleteArr(Path[] files) {

        //The files to be deleted are those with an already existing path, excluding the root
        return Arrays.stream(files)
                .filter(file -> !file.toString().equals("/"))
                .filter(file -> directoryMap.pathExists(file)).toArray(Path[]::new);
    }

    /**
     * Represents a mapping of the current Directory structure.
     */
    private class DirectoryMap {

        /**
         * A map with key ==> String of the file/directory name and value ==> a DirectoryMap.
         */
        Map<String, DirectoryMap> current;
        /**
         * If the current level represents a folder or not
         */
        private boolean isFolder;
        /**
         * A Map with key ==> String of the file/directory and value ==> the associated Storage stub
         */
        private Map<String, Storage> storageStubMap;
        /**
         * A Map with key ==> String of the file/directory and value ==> the associated Command stub
         */
        private Map<String, Command> commandStubMap;

        public DirectoryMap() {
            current = new HashMap<>();
            isFolder = true;
            storageStubMap = new HashMap<>();
            commandStubMap = new HashMap<>();
        }

        /**
         * Add a Path to this DirectoryMap
         *
         * @param path        to be added
         * @param storageStub associated with Path
         * @return true if Path was added, false otherwise
         */
        public boolean addPath(Path path, Storage storageStub, Command commandStub) {

            //The components of the path parameter
            String[] paths;
            //A helper Map for DirectoryMap traversal
            Map<String, DirectoryMap> traverse;

            //root path already exists, so not added
            if (path.toString().equals("/")) return false;

            //get the components of the path
            paths = Utilities.getPathComponents(path);
            traverse = current;

            //Traverse the directory until the parent directory is reached
            for (int i = 0; i < paths.length - 1; i++) {
                if (!traverse.containsKey(paths[i])) {
                    traverse.put(paths[i], new DirectoryMap());
                }
                traverse = traverse.get(paths[i]).current;
            }

            traverse.put(paths[paths.length - 1], new DirectoryMap());
            //As this is a file, set isFolder to false
            traverse.get(paths[paths.length - 1]).isFolder = false;
            //Assign the associated StorageStub
            traverse.get(paths[paths.length - 1]).storageStubMap.put(paths[paths.length - 1], storageStub);
            traverse.get(paths[paths.length - 1]).commandStubMap.put(paths[paths.length - 1], commandStub);

            return true;
        }

        /**
         * Add a Path to this DirectoryMap associate with a directory. Similar to <code>addPath</code>
         *
         * @param path        to be added
         * @param storageStub associated with Path
         * @return
         */
        public boolean addPathDirectory(Path path, Storage storageStub, Command commandStub) {

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length - 1; i++) {
                if (!traverse.containsKey(paths[i])) {
                    traverse.put(paths[i], new DirectoryMap());
                }
                traverse = traverse.get(paths[i]).current;
            }

            traverse.put(paths[paths.length - 1], new DirectoryMap());
            traverse.get(paths[paths.length - 1]).isFolder = true;
            traverse.get(paths[paths.length - 1]).storageStubMap.put(paths[paths.length - 1], storageStub);
            traverse.get(paths[paths.length - 1]).commandStubMap.put(paths[paths.length - 1], commandStub);

            return true;
        }

        /**
         * Determines whether the path exits
         *
         * @param path to determine is the parent exists
         * @return true if the path exists, false otherwise
         */
        public boolean pathExists(Path path) {

            if (path.toString().equals("/")) return true;

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length; i++) {
                if (!traverse.containsKey(paths[i])) return false;
                traverse = traverse.get(paths[i]).current;
            }
            return true;
        }

        /**
         * Determines whether the parent the path exits
         *
         * @param path to determine is the parent exists
         * @return true is parent exists, false otherwise
         */
        private boolean parentExists(Path path) {

            String[] paths = Utilities.getPathComponents(path);
            Path pathTemp = new Path("/");

            for (int i = 0; i < paths.length - 1; i++) {
                pathTemp = new Path(pathTemp, paths[i]);
            }

            return pathExists(pathTemp) && isFolder(pathTemp);
        }

        /**
         * Determines if the path represents a folder
         *
         * @param path to be checked
         * @return true is the path is a folder, false otherwise
         */
        public boolean isFolder(Path path) {

            if (path.toString().equals("/")) return true;

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length - 1; i++) {
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.get(paths[paths.length - 1]).isFolder;
        }

        /**
         * Lists the contents in the folder representated by the Path
         *
         * @param path to list
         * @return a String[] representation of the paths in the folder
         */
        public String[] list(Path path) {

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length; i++) {
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.keySet().stream().toArray(String[]::new);
        }

        /**
         * Returns the Storage stub associated with Path
         *
         * @param path associated with request
         * @return the Storage stub associated with Path
         */
        public Storage getStorageStub(Path path) {

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length - 1; i++) {
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.get(paths[paths.length - 1]).storageStubMap.get(paths[paths.length - 1]);
        }

        /**
         * Returns the Command stub associated with Path
         *
         * @param path associated with request
         * @return the Command stub associated with Path
         */
        public Command getCommandStub(Path path) {

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length - 1; i++) {
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.get(paths[paths.length - 1]).commandStubMap.get(paths[paths.length - 1]);
        }

        /**
         * Deletes a path from the directory
         *
         * @param path to delete
         * @return true if the path was deleted, false otherwise
         */
        public boolean deletePath(Path path) {

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for (int i = 0; i < paths.length - 1; i++) {
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.remove(paths[paths.length - 1]) != null;
        }
    }
}
