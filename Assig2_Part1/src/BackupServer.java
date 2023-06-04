import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.Hashtable;

 

public class BackupServer {

 

    private static Queue<ClientRequest> requestQueue = new ArrayDeque<>();
    private static Semaphore tokenSemaphore = new Semaphore(1);
    static Hashtable<String, String> clients = new Hashtable<String, String>();
    static String[] data = null;

 

    public static void main(String[] args) throws InterruptedException {
        try {
            int serverPort = 50002;
            ServerSocket serverSocket = new ServerSocket(serverPort);
            System.out.println("Waiting for Primary to connect...");

 

            // Connect to the primary server
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
            // Use a different port number, e.g., 50002

 

            try {
                Thread.sleep(3000);
                ServerSocket listenSocket = new ServerSocket(50000);

 

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

 

    private static void handleTokenRequest(Socket clientSocket, String inPrimary, DataOutputStream out) throws IOException {
        if (tokenSemaphore.tryAcquire()) {
            // Grant token to the requesting client
            out.writeUTF("TOKEN_GRANTED");
            out.flush(); // Ensure data is sent immediately
        } else {
            // Queue the request if the token is not available
            requestQueue.offer(new ClientRequest(clientSocket, inPrimary, clientSocket.getInputStream(), out));
            out.writeUTF("TOKEN_DENIED");
            out.flush(); // Ensure data is sent immediately

 

            // Wait until the token is released
            try {
                synchronized (tokenSemaphore) {
                    tokenSemaphore.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

 

    private static void handleTokenRelease() {
        tokenSemaphore.release();

 

        if (!requestQueue.isEmpty()) {
            // Grant the token to the next client in the queue
            ClientRequest nextRequest = requestQueue.poll();
            try {
                DataOutputStream out = nextRequest.getOut();
                out.writeUTF("TOKEN_GRANTED");
                out.flush(); // Ensure data is sent immediately
            } catch (IOException e) {
                e.printStackTrace();
            }

 

            // Notify waiting threads that the token has been released
            synchronized (tokenSemaphore) {
                tokenSemaphore.notify();
            }
        }
    }

 

    static class Connection extends Thread {
        Socket clientSocket;
        String inPrimary;
        DataInputStream in;
        DataOutputStream out;

 

        public Connection(Socket clientSocket, String inPrimary) throws IOException {
            this.clientSocket = clientSocket;
            this.inPrimary = inPrimary;
            this.in = new DataInputStream(clientSocket.getInputStream());
            this.out = new DataOutputStream(clientSocket.getOutputStream());
        }

 

        public void run() {
            try {
                while (true) {
                    // Wait for client request
                    String request = null;
                    try {
                        request = in.readUTF();
                    } catch (SocketException e) {
                        if (e.getMessage().equals("Socket closed")) {
                            System.out.println("Connection closed by the client: " + clientSocket.getInetAddress()
                                    + ":" + clientSocket.getPort());
                            handleTokenRelease();
                        } else {
                            e.printStackTrace();
                        }
                        break;
                    }

 

                    System.out.println("Received: " + request + " from: " + clientSocket.getInetAddress() + ":"
                            + clientSocket.getPort());

 

                    if (request.equals("REQUEST_TOKEN")) {
                        // Handle token request
                        handleTokenRequest(clientSocket, inPrimary, out);
                    } else if (request.equals("RELEASE_TOKEN")) {
                        // Handle token release
                        in.close();
                        handleTokenRelease();
                        break;
                    } else {
                        // Handle other requests from the client
                        // ... (code for handling the specific requests)
                        out.writeUTF("Received: " + request); // Send a response to the client
                        out.flush(); // Ensure data is sent immediately
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

 

    static class ClientRequest {
        private Socket clientSocket;
        private String inPrimary;
        private InputStream in;
        private DataOutputStream out;

 

        public ClientRequest(Socket clientSocket, String inPrimary, InputStream inputStream, DataOutputStream out) {
            this.clientSocket = clientSocket;
            this.inPrimary = inPrimary;
            this.in = inputStream;
            this.out = out;
        }

 

        public Socket getClientSocket() {
            return clientSocket;
        }

 

        public String getInPrimary() {
            return inPrimary;
        }

 

        public InputStream getIn() {
            return in;
        }

 

        public DataOutputStream getOut() {
            return out;
        }
    }
}