// ServerController.java
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ServerController implements Runnable {
    public final Map<String, Integer> clientData = new HashMap<>();
    public int clientID = 0;
    private static final int PORT = 5000;
    private static final String HOST = "127.0.0.1";
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(HOST));
            log("Server listening on " + HOST + ":" + PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    log("CLIENT CONNECTED: " + clientIP + ":" + clientSocket.getPort());
                    clientData.put(clientIP, clientID++);

                    Thread clientThread = new Thread(new ClientHandler(clientSocket, clientData.get(clientIP)));
                    clientThread.setDaemon(true);
                    clientThread.start();

                } catch (SocketException e) {
                    if (running) log("Socket error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                log("Server stopped");
            }
        } catch (IOException e) {
            log("Error closing server: " + e.getMessage());
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String clientIP;
        private final int clientID;

        public ClientHandler(Socket socket, int clientID) {
            this.clientSocket = socket;
            this.clientIP     = socket.getInetAddress().getHostAddress();
            this.clientID     = clientID;
        }

        @Override
        public void run() {
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
            ) {
                String message;
                while ((message = reader.readLine()) != null) {
                    log("FROM " + clientIP + ": " + message);
                    writer.write("Server received: " + message + "\n");
                    writer.flush();
                }
                log("CLIENT DISCONNECTED: " + clientIP);
            } catch (IOException e) {
                log("Client disconnected: " + e.getMessage() + " (IP: " + clientIP + ")");
            } finally {
                try { clientSocket.close(); } catch (IOException e) {
                    log("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
}