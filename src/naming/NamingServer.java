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

    static Map<Path, Storage> pathStorageMap = new HashMap<>();
    Skeleton<Service> serviceSkeleton;
    Skeleton<Registration> registrationSkeleton;
    Set<Storage> clientStubs;
    Set<Command> commandStubs;

    /**
     * Creates the naming server object.
     *
     * <p>
     * The naming server is not started.
     */
    public NamingServer() {
        clientStubs = new HashSet<>();
        commandStubs = new HashSet<>();
        pathStorageMap = new HashMap<>();

        pathStorageMap.put(new Path("/"), null);
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
        if (!pathStorageMap.containsKey(path)) {
            throw new FileNotFoundException("directory not found");
        }

        //If this File is a directory, there should be at least 2 entries in the directory tree (pathStorageMap)
        //which contain this File.toString()
        return pathStorageMap.keySet()
                .stream()
                .filter(pathInMap -> pathInMap.toString().contains(path.toString()))
                .count() > 2;
    }

    @Override
    public String[] list(Path directory) throws FileNotFoundException {
        if (directory == null) {
            throw new NullPointerException("Path for list method cannot be null");
        }
        if (!pathStorageMap.containsKey(directory)) {
            throw new FileNotFoundException("directory not found");
        }
        if (!isDirectory(directory)) {
            throw new FileNotFoundException("not a directory");
        }

        String paths[] = pathStorageMap.keySet()
                .stream()
                .filter(path -> path.toString().startsWith(directory.toString()) && !path.toString().equals(directory.toString()))
                .map(path -> {
                    if (directory.toString().equals("/"))
                        return path.toString().substring(directory.toString().length());
                    else
                        return path.toString().substring(directory.toString().length() + 1);
                })
                .filter(path -> !path.contains("/"))
                .toArray(String[]::new);

        return paths;
    }

    @Override
    public boolean createFile(Path file)
            throws RMIException, FileNotFoundException {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean delete(Path path) throws FileNotFoundException {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Storage getStorage(Path file) throws FileNotFoundException {
        throw new UnsupportedOperationException("not implemented");
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
                .filter(path -> !pathStorageMap.containsKey(path))
                .forEach(e -> addPathComponentsToMap(e, client_stub));

        return toDelete;
    }

    private Path[] getToDeleteArr(Path[] files) {

        List<Path> toDeleteList = new ArrayList<>();

        Arrays.stream(files).forEach(path -> {

            if (pathStorageMap.containsKey(path)) {
                toDeleteList.add(path);
            }
        });

        toDeleteList.remove(new Path("/"));

        return toDeleteList.toArray(new Path[toDeleteList.size()]);
    }

    private void addPathComponentsToMap(Path file, Storage client_stub) {

        String[] components = file.toString().substring(1).split("/");

        if (components.length >= 2) {
            Path path = new Path("/" + components[0]);
            pathStorageMap.put(path, client_stub);
            for (int i = 1; i < components.length - 1; i++) {
                path = new Path(path, components[i]);
                pathStorageMap.put(path, client_stub);
            }
        }
        pathStorageMap.put(file, client_stub);
    }
}
