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
    private static Queue<ClientRequest> requestQueue = new ArrayDeque<>();
    private static Semaphore tokenSemaphore = new Semaphore(1);

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
                //System.out.println("THIS IS REQUEST QUEUE----->" + requestQueue.toString());
   
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

   /*  private static synchronized String getRequestQueueAsString() {
        StringBuilder sb = new StringBuilder("[");

        for (ClientRequest request : requestQueue) {
            sb.append(request.toString());
            sb.append(", ");
        }

        if (!requestQueue.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove the trailing ", "
        }

        sb.append("]");

        return sb.toString();
    }*/

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
                if (e.getMessage().equals("Connection reset"))
                {
                    System.out.println("Connection closed by the client: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    
                }
                else
                {
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
