// main.java for running the server
public class main {
    public static void main(String[] args) {
        System.out.println("Starting Trading Game Server...");
        ServerController server = ServerController.getInstance();
        server.start();
    }
}