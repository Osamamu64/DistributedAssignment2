import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class PrimaryServer {
    private static Queue<Socket> requestQueue = new LinkedList<>();
    private static boolean tokenAvailable = true;

    public static void main(String[] args) {
        try {
            // Connect to the backup server
            Socket backupSocket = new Socket(args[0], 50001);
            DataOutputStream outBackup = new DataOutputStream(backupSocket.getOutputStream());

            // Listen for client connections
            ServerSocket serverSocket = new ServerSocket(50000);
            System.out.println("Primary Server is ready and waiting for client connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                // Create a new thread to handle each client
                Thread clientThread = new Connection(clientSocket, outBackup);
                clientThread.start();
                System.out.println("THIS IS REQUEST QUEUE----->" + requestQueue);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void handleTokenRequest(Socket clientSocket, DataOutputStream out) throws IOException {
        if (tokenAvailable) {
            // Grant token to the requesting client
            tokenAvailable = false;
            out.writeUTF("TOKEN_GRANTED");
        } else {
            // Queue the request if the token is not available
            requestQueue.offer(clientSocket);
            out.writeUTF("TOKEN_DENIED");
        }
        out.flush();
    }

    private static synchronized void handleTokenRelease() {
        if (!requestQueue.isEmpty()) {
            // Grant the token to the next client in the queue
            Socket nextClient = requestQueue.poll();
            try {
                DataOutputStream out = new DataOutputStream(nextClient.getOutputStream());
                // Move the writeUTF outside the synchronized block
                out.writeUTF("TOKEN_GRANTED");
                out.flush();
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
        DataOutputStream outBackup;

        public Connection(Socket clientSocket, DataOutputStream outBackup) {
            this.clientSocket = clientSocket;
            this.outBackup = outBackup;
        }

        public void run() {
            try {
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                while (true) {
                    try {
                        // Wait for client request
                        String request = in.readUTF();

                        System.out.println("Received: " + request + " from: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                        if (request.equals("REQUEST_TOKEN")) {
                            // Handle token request
                            handleTokenRequest(clientSocket, out);
                        } else if (request.equals("RELEASE_TOKEN")) {
                            // Handle token release
                            handleTokenRelease();
                            break;
                        }

                        // Forward the message to the backup server
                        String msg = clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "," + request;
                        if (outBackup != null) {
                            outBackup.writeUTF(msg);
                            outBackup.flush();
                        }
                    } catch (EOFException e) {
                        // Handle EOFException (connection closed)
                        System.out.println("Connection closed by the client: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
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
