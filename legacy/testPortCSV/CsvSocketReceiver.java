import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class CsvSocketReceiver {

    // Change if you want a different port
    private static final int PORT = 5555;


    public static int[] parsePair(String s) {
    String[] parts = s.trim().split("\\s+");
    return new int[] {
        Integer.parseInt(parts[0]),
        Integer.parseInt(parts[1])
    };
}
    public static void main(String[] args) {
        System.out.println("CSV Receiver starting on port " + PORT + " ...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Waiting for a client to connect...");

            try (Socket clientSocket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
                )) {

                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                System.out.println("Receiving CSV lines (newline-delimited). Press Ctrl+C to stop.");

                String line;
                while ((line = reader.readLine()) != null) {
                    // Each line is one CSV row
                    int[] values = parsePair(line);
                    System.err.println("time: " + values[0]);
                    System.err.println("value: " + values[1]);
                }

                System.out.println("Client disconnected.");

            } catch (IOException e) {
                System.err.println("Error while handling client: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("Could not start server on port " + PORT + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
