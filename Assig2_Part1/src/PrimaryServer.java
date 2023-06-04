import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class PrimaryServer {
    public static final String[] args = null;

    private static Queue<ClientRequest> requestQueue = new ArrayDeque<>();
    private static Semaphore tokenSemaphore = new Semaphore(1);
    private static volatile boolean isRunning = true;
    private static DataOutputStream outBackup; // Make outBackup accessible globally

    public static void main(String[] args) {
        try {
            // Connect to the backup server
            Socket backupSocket = new Socket(args[0], 50002);
            outBackup = new DataOutputStream(backupSocket.getOutputStream());

            // Start the backup listener thread
            BackupListener backupListener = new BackupListener(backupSocket);
            backupListener.start();

            // Listen for client connections
            ServerSocket serverSocket = new ServerSocket(50000);
            System.out.println("Primary Server is ready and waiting for client connections...");

            // Add shutdown hook to handle termination gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                isRunning = false;
                try {
                    backupSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();

                // Create a new thread to handle each client
                Thread clientThread = new Connection(clientSocket);
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void handleTokenRequest(Socket clientSocket, DataOutputStream out) throws IOException {
        if (tokenSemaphore.tryAcquire()) {
            // Grant token to the requesting client
            out.writeUTF("TOKEN_GRANTED");
        } else {
            // Queue the request if the token is not available
            ClientRequest request = new ClientRequest(clientSocket, out);
            requestQueue.offer(request);
            out.writeUTF("TOKEN_DENIED");

            // Forward the message to the backup server
            String msg = clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "," + "TOKEN_DENIED";
            if (outBackup != null) {
                outBackup.writeUTF(msg);
                outBackup.flush(); // Flush the output stream to ensure the data is sent
            }
        }
        out.flush(); // Flush the output stream to ensure the data is sent
    }

    private static synchronized void handleTokenRelease() {
        tokenSemaphore.release();
        if (!requestQueue.isEmpty()) {
            // Grant the token to the next client in the queue
            ClientRequest nextRequest = requestQueue.poll();
            try {
                DataOutputStream out = nextRequest.getOutputStream();
                out.writeUTF("TOKEN_GRANTED");
                out.flush(); // Flush the output stream to ensure the data is sent
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class Connection extends Thread {
        Socket clientSocket;

        public Connection(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                while (true) {
                    try {
                        // Wait for client request
                        String request = in.readUTF();

                        if (request.equals("REQUEST_TOKEN")) {
                            // Handle token request
                            handleTokenRequest(clientSocket, out);
                        } else if (request.equals("RELEASE_TOKEN")) {
                            // Handle token release
                            handleTokenRelease();
                            break;
                        } else {
                            // Handle other requests from the client
                            // ... (code for handling the specific requests)
                        }

                        // Forward the message to the backup server
                        String msg = clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "," + request;
                        if (outBackup != null) {
                            outBackup.writeUTF(msg);
                            outBackup.flush(); // Flush the output stream to ensure the data is sent
                        }
                    } catch (EOFException e) {
                        // Handle EOFException (connection closed)
                        System.out.println("Connection closed by the client: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                        handleTokenRelease();
                        break;
                    }
                }
            } catch (IOException e) {
                if (e.getMessage().equals("Connection reset")) {
                    System.out.println("Connection closed by the client: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                } else {
                    e.printStackTrace();
                }
            } finally {
                try {
                    clientSocket.close(); // Close the clientSocket
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class BackupListener extends Thread {
        Socket backupSocket;

        public BackupListener(Socket backupSocket) {
            this.backupSocket = backupSocket;
        }

        public void run() {
            try {
                DataInputStream in = new DataInputStream(backupSocket.getInputStream());

                while (isRunning) {
                    try {
                        String message = in.readUTF();
                        // Update the backup server based on the message received
                        // ... (code to process the message and update the backup server)
                        System.out.println("Received message from backup server: " + message);
                    } catch (EOFException e) {
                        // Handle EOFException (connection closed by the backup server)
                        System.out.println("Connection closed by the backup server.");
                        // Reconnect to the backup server
                        try {
                            backupSocket = new Socket(args[0], 50002);
                            in = new DataInputStream(backupSocket.getInputStream());
                            System.out.println("Reconnected to the backup server.");
                        } catch (IOException ex) {
                            System.out.println("Error connecting to backup server: " + ex.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                if (e.getMessage().equals("Connection reset")) {
                    System.out.println("Connection closed by the backup server.");
                } else {
                    e.printStackTrace();
                }
            } finally {
                try {
                    backupSocket.close(); // Close the backupSocket
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class ClientRequest {
        private Socket socket;
        private DataOutputStream outputStream;

        public ClientRequest(Socket socket, DataOutputStream outputStream) {
            this.socket = socket;
            this.outputStream = outputStream;
        }

        public Socket getSocket() {
            return socket;
        }

        public DataOutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public String toString() {
            return "ClientRequest [socket=" + socket + ", outputStream=" + outputStream + "]";
        }
    }
}
