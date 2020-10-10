package rmi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ListenThread extends Thread {

    ServerSocket serverSocket;

    public ListenThread(ServerSocket serverSocket) {

        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        while(!serverSocket.isClosed()){
            Socket socket;
            try {
                socket = serverSocket.accept();
                System.out.println("New connection from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                System.out.println("IOException while connecting to socket");
                e.printStackTrace();
            }
        }
    }
}
