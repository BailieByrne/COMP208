import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

//Server controller - listens for client connections
public class ServerController implements Runnable {
    private static final int PORT = 5000;
    private static final String HOST = "127.0.0.1";
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    //Simple logging with time stamp use new Date() change to System time
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    @Override
    /**
     * RUN:
     * 1. Create a server socket bound to the specified port and host.
     * 2. Log that the server is listening and waiting for client connections.
     * 3. Enter a loop that continues while the server is running (this is because the socket is blocking)
     */
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(HOST));
            log("Server is listening on " + HOST + ":" + PORT);
            log("Waiting for client connections...");
            log("");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    log("CLIENT CONNECTED: " + clientIP + ":" + clientSocket.getPort());
                    
                    // Handle client in a separate thread
                    Thread clientThread = new Thread(new ClientHandler(clientSocket));
                    clientThread.setDaemon(true);
                    clientThread.start();
                    //ctach and log socket errors
                } catch (SocketException e) {
                    if (running) {
                        log("Socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //handle server stop
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

    // Inner class to handle individual client connections
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private String clientIP;

        //Client handler construc 
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            this.clientIP = socket.getInetAddress().getHostAddress();
        }

        @Override
        //Main handler to allow reading froma nd writing to client socket
        public void run() {
            try (
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();
            ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output));

                String message;
                while ((message = reader.readLine()) != null) {
                    log("FROM " + clientIP + ": " + message);
                    
                    writer.write("Server received: " + message + "\n");
                    writer.flush();
                }
                log("CLIENT DISCONNECTED: " + clientIP);
            } catch (IOException e) {
                log("Client DISCONNECTED: " + e.getMessage() + " (IP: " + clientIP + ")");
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }
}
