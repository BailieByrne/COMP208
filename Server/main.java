// main.java for running the server
public class main {
    // boot server loop
    public static void main(String[] args) {
        ServerController server = new ServerController();
        Thread serverThread = new Thread(server);
        serverThread.setDaemon(false);
        serverThread.start();

        try {
            serverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            server.stop();
        }
    }
}