package rmi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A thread which listens for ServerSocket connections.
 */
public class ListenThread extends Thread {

    /**
     * ServerSocket which this thread listens for
     */
    ServerSocket serverSocket;

    /**
     * If the ListenThread should be listening for connections
     */
    private boolean shouldRun;

    public ListenThread(ServerSocket serverSocket) {

        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {

        shouldRun = true;

        while(shouldRun){
            Socket socket;
            try {
                //Blocking call to wait for a connection
                socket = serverSocket.accept();
                System.out.println("New connection from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                System.out.println("IOException while connecting to socket");
            }
        }
    }

    /**
     * Stops listening for connection, closes this thread
     */
    public void stopRun() {

        if(serverSocket == null) {
            System.out.println("ServerSocket is not initialized, so not stopped");
            return;
        }
        if(serverSocket.isClosed()){
            System.out.println("ServerSocket is already stopped, so not stopped");
            return;
        }

        try {
            serverSocket.close();
            shouldRun = false;
        } catch (IOException e) {
            System.out.println("IOException when trying to close Server Socket");
            e.printStackTrace();
        }
    }
}
