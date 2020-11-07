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

    static DirectoryMap directoryMap;
    Skeleton<Service> serviceSkeleton;
    Skeleton<Registration> registrationSkeleton;
    static Set<Storage> clientStubs;
    static Set<Command> commandStubs;

    /**
     * Creates the naming server object.
     *
     * <p>
     * The naming server is not started.
     */
    public NamingServer() {
        clientStubs = new HashSet<>();
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
        serviceSkeleton = new Skeleton<Service>(
                Service.class, new NamingServer(), new InetSocketAddress("127.0.0.1", NamingStubs.SERVICE_PORT));
        registrationSkeleton = new Skeleton<Registration>(
                Registration.class, new NamingServer(), new InetSocketAddress("127.0.0.1", NamingStubs.REGISTRATION_PORT));

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
        if(!directoryMap.parentExists(file)) {
            throw new FileNotFoundException("Parent Directory does not exist");
        }
        if(directoryMap.pathExists(file)) {
            return false;
        }

        commandStubs.iterator().next().create(file);

        return directoryMap.addPath(file, commandStubs.iterator().next(), clientStubs.iterator().next());
    }

    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException {

        if (directory == null) {
            throw new NullPointerException("directory cannot be null");
        }

        if(directoryMap.pathExists(directory)) {
            return false;
        }

        if(!directoryMap.parentExists(directory)) {
            throw new FileNotFoundException("Parent Directory does not exist");
        }

        return directoryMap.addPathDirectory(directory, commandStubs.iterator().next(), clientStubs.iterator().next());
    }

    @Override
    public boolean delete(Path path) throws FileNotFoundException {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Storage getStorage(Path file) throws FileNotFoundException {

        if(file == null) {
            throw new NullPointerException();
        }
        if(!directoryMap.pathExists(file)){
            throw new FileNotFoundException();
        }
        if(directoryMap.isFolder(file)) {
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

        if (clientStubs.contains(client_stub) || commandStubs.contains(command_stub)) {
            throw new IllegalStateException("Storage server already registered");
        }

        clientStubs.add(client_stub);
        commandStubs.add(command_stub);

        Path[] toDelete = getToDeleteArr(files);

        Arrays.stream(files)
                .filter(file -> Arrays.stream(toDelete).noneMatch(toDelFile -> toDelFile == file))
                .forEach(file -> directoryMap.addPath(file, command_stub, client_stub));

        return toDelete;
    }

    private Path[] getToDeleteArr(Path[] files) {

        return Arrays.stream(files)
                .filter(file -> !file.toString().equals("/"))
                .filter(file -> directoryMap.pathExists(file)).toArray(Path[]::new);
    }

    private class DirectoryMap {

        Map<String, DirectoryMap> current;
        private boolean isFolder;
        private Command commandStub;
        private Storage storageStub;
        private Map<String, Command> commandStubMap;
        private Map<String, Storage> storageStubMap;

        public DirectoryMap() {
            current = new HashMap<>();
            isFolder = true;
            commandStub = null;
            commandStubMap = new HashMap<>();
            storageStubMap = new HashMap<>();
        }

        public boolean addPath(Path path, Command commandStub, Storage storageStub) {

            if(path.toString().equals("/")) return false;

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for(int i = 0 ; i < paths.length -1; i++){
                if(!traverse.containsKey(paths[i])) {
                    traverse.put(paths[i], new DirectoryMap());
                }
                traverse = traverse.get(paths[i]).current;
            }
            traverse.put(paths[paths.length-1], new DirectoryMap());
            traverse.get(paths[paths.length-1]).isFolder = false;
            traverse.get(paths[paths.length-1]).storageStubMap.put(paths[paths.length-1], storageStub);
            traverse.get(paths[paths.length-1]).commandStubMap.put(paths[paths.length-1], commandStub);

            return true;
        }

        public boolean addPathDirectory(Path path, Command commandStub, Storage storageStub){

            //if(!parentExists(path)) return false;

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for(int i = 0 ; i < paths.length -1; i++){
                if(!traverse.containsKey(paths[i])) {
                    traverse.put(paths[i], new DirectoryMap());
                }
                traverse = traverse.get(paths[i]).current;
            }

            traverse.put(paths[paths.length-1], new DirectoryMap());
            traverse.get(paths[paths.length-1]).isFolder = true;
            traverse.get(paths[paths.length-1]).storageStubMap.put(paths[paths.length-1], storageStub);
            traverse.get(paths[paths.length-1]).commandStubMap.put(paths[paths.length-1], commandStub);

            return true;
        }

//        private boolean addPathFileOrDirectory(Path path) {
//
//            String[] paths = Utilities.getPathComponents(path);
//            Map<String, DirectoryMap> traverse = current;
//
//            for(int i = 0 ; i < paths.length -1; i++){
//                if(!traverse.containsKey(paths[i])) {
//                    traverse.put(paths[i], new DirectoryMap());
//                }
//                traverse = traverse.get(paths[i]).current;
//            }
//            traverse.put(paths[paths.length-1], new DirectoryMap());
//            traverse.get(paths[paths.length-1]).isFolder = false;
//
//            return true;
//        }

        private boolean parentExists(Path path){

            String[] paths = Utilities.getPathComponents(path);
            Path pathTemp = new Path("/");

            for(int i = 0 ; i < paths.length - 1; i++) {
                pathTemp = new Path(pathTemp, paths[i]);
            }

            return pathExists(pathTemp) && isFolder(pathTemp);
         }

        public boolean pathExists(Path path) {

            if(path.toString().equals("/")) return true;

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for(int i = 0; i < paths.length; i++){
                if(!traverse.containsKey(paths[i])) return false;
                traverse = traverse.get(paths[i]).current;
            }
            return true;
        }

        public boolean isFolder(Path path){

            if(path.toString().equals("/")) return true;

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for(int i = 0; i < paths.length-1; i++){
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.get(paths[paths.length-1]).isFolder;
        }

        public String[] list(Path path){

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for(int i = 0; i < paths.length; i++){
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.keySet().stream().toArray(String[]::new);
        }

//        public Command getCommandStub(Path path){
//
//            String[] paths = Utilities.getPathComponents(path);
//            Map<String, DirectoryMap> traverse = current;
//
//            for(int i = 0; i < paths.length-1; i++){
//                traverse = traverse.get(paths[i]).current;
//            }
//
//            return traverse.get(paths[paths.length-1]).commandStub;
//        }

        public Storage getStorageStub(Path path){

            String[] paths = Utilities.getPathComponents(path);
            Map<String, DirectoryMap> traverse = current;

            for(int i = 0; i < paths.length-1; i++){
                traverse = traverse.get(paths[i]).current;
            }

            return traverse.get(paths[paths.length-1]).storageStubMap.get(paths[paths.length-1]);
        }
    }
}
