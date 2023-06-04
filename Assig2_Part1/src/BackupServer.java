import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Queue;

public class BackupServer {
    private static Queue<Socket> requestQueue = new LinkedList<>();
    private static boolean tokenAvailable = true;
    static Hashtable<String, String> clients = new Hashtable<String, String>();
    static String[] data = null;

    public static void main(String[] args) {
        try {
            System.out.println("Waiting for Primary to connect...");

            ServerSocket serverSocket = new ServerSocket(50001);
            Socket primarySocket = serverSocket.accept();
            DataInputStream inPrimary = new DataInputStream(primarySocket.getInputStream());
            DataOutputStream outPrimary = new DataOutputStream(primarySocket.getOutputStream());

            // Listen for client connections
            System.out.println("Backup Server is CONNECTED and waiting for client connections...");

            while (true) {
                String msg = inPrimary.readUTF();
                data = msg.split(",");
                clients.put(data[0], data[1]);
            }
        } catch (IOException e) {
            System.out.println("-----------------------------------");
            System.out.println("Server Failed, Backup Takes Over...");
            int serverPort = 50002; // Use a different port number, e.g., 50002

            try {
                ServerSocket listenSocket = new ServerSocket(serverPort);
                System.out.println("Backup Server is ready to take over and waiting for requests...");
                while (true) {
                    Socket clientSocket = listenSocket.accept();
                    Thread clientThread = new Connection(clientSocket, data[1]);
                    clientThread.start();
                }
            } catch (IOException ee) {
                System.out.println("Error Listen socket: " + ee.getMessage());
            }
        }
    }

    private static synchronized void handleTokenRequest(Socket clientSocket, String inPrimary, DataOutputStream out) throws IOException {
        if (tokenAvailable) {
            // Grant token to the requesting client
            tokenAvailable = false;
            out.writeUTF("TOKEN_GRANTED");
            out.flush(); // Ensure data is sent immediately
            out.close();
        } else {
            // Queue the request if the token is not available
            requestQueue.offer(clientSocket);
            out.writeUTF("TOKEN_DENIED");
            out.flush(); // Ensure data is sent immediately
            out.close();
        }
    }

    private static synchronized void handleTokenRelease() {
        if (!requestQueue.isEmpty()) {
            // Grant the token to the next client in the queue
            Socket nextClient = requestQueue.poll();
            try {
                DataOutputStream out = new DataOutputStream(nextClient.getOutputStream());
                out.writeUTF("TOKEN_GRANTED");
                out.flush(); // Ensure data is sent immediately
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // No pending requests, token is available
            tokenAvailable = true;
        }
    }

    static class Connection extends Thread {
        Socket clientSocket;
        String inPrimary;
        DataOutputStream outPrimary;

        public Connection(Socket clientSocket, String inPrimary) {
            this.clientSocket = clientSocket;
            this.inPrimary = inPrimary;
        }

        public void run() {
            try {
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                while (true) {
                    // Wait for client request
                    String request = data[1];
                    System.out.println("Received: " + request + " from: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                    if (request.equals("REQUEST_TOKEN")) {
                        // Handle token request
                        handleTokenRequest(clientSocket, inPrimary, out);
                    } else if (request.equals("RELEASE_TOKEN")) {
                        // Handle token release
                        handleTokenRelease();
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
