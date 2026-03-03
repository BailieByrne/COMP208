import java.io.IOException;

//Server - runs ServerController to listen for connections
public class main {
    public static void main(String[] args) {
        ServerController server = new ServerController();
        Thread serverThread = new Thread(server);
        serverThread.setDaemon(false);
        serverThread.start();
        
        // Keep the server running
        try {
            serverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            server.stop();
        }
    }
}