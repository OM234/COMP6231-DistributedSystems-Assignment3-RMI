package rmi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ListenThread extends Thread {

    ServerSocket serverSocket;
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
                socket = serverSocket.accept();
                System.out.println("New connection from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                System.out.println("IOException while connecting to socket");
                //e.printStackTrace();
            }
        }
    }

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
        } catch (IOException e) {
            System.out.println("IOException when trying to close Server Socket");
            e.printStackTrace();
        }

        shouldRun = false;
    }
}
