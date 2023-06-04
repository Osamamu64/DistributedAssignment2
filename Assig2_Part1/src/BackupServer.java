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
	//static DataOutputStream outPrimary = null;

    public static void main(String[] args) {
        try {
        	
        	System.out.println("Waiting for Primary to connect ... ");
        	
			/*ServerSocket listenSocket = new ServerSocket(50001);
			Socket PrimarySocket = listenSocket.accept();
			DataInputStream inPrimary = new DataInputStream(PrimarySocket.getInputStream());
			System.out.println("Connected with Primary ... ");*/
        	
            // Connect to the primary server
            //Socket primarySocket = new Socket(args[0], 50000);
			ServerSocket serverSocket = new ServerSocket(50001);
			Socket primarySocket = serverSocket.accept();
            DataInputStream inPrimary = new DataInputStream(primarySocket.getInputStream());
            DataOutputStream outPrimary = new DataOutputStream(primarySocket.getOutputStream());

            // Listen for client connections
            //ServerSocket serverSocket = new ServerSocket(50001);
            System.out.println("Backup Server is CONNECTED and waiting for client connections...");

            while (true) {
                //Socket clientSocket = serverSocket.accept();
            	
            	String msg = inPrimary.readUTF();
				data = msg.split(",");
				clients.put(data[0], data[1]);

                // Create a new thread to handle each client
                //Thread clientThread = new Connection(clientSocket, inPrimary, outPrimary);
                //clientThread.start();
            }
        } catch (IOException e) {
            //e.printStackTrace();
        	System.out.println("-----------------------------------");
        	System.out.println("Server Failed, Backup Takes Over...");
      		int serverPort = 50000; // Backup behaving like a Primary
      		
      		try {
      			ServerSocket listenSocket = new ServerSocket(serverPort);
      			System.out.println("Backup Server is ready to takeover and waiting for requests ... ");
      			while (true) {
      				Socket clientSocket = listenSocket.accept();
      				Thread clientThread = new Connection(clientSocket, data[1]);
      				clientThread.start();
      			}
      		} catch(IOException ee) {System.out.println("Error Listen socket:"+ee.getMessage());}
        }
    }

    private static synchronized void handleTokenRequest(Socket clientSocket, String inPrimary, DataOutputStream out) throws IOException {
        if (tokenAvailable) {
            // Grant token to the requesting client
            tokenAvailable = false;
            out.writeUTF("TOKEN_GRANTED");
            out.flush();
        } else {
            // Queue the request if the token is not available
            requestQueue.offer(clientSocket);
            out.writeUTF("TOKEN_DENIED");
            out.flush();
        }
    }

    private static synchronized void handleTokenRelease() {
        if (!requestQueue.isEmpty()) {
            // Grant the token to the next client in the queue
            Socket nextClient = requestQueue.poll();
            try {
                DataOutputStream out = new DataOutputStream(nextClient.getOutputStream());
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
        String inPrimary;
        DataOutputStream outPrimary;

        public Connection(Socket clientSocket, String inPrimary) {
            this.clientSocket = clientSocket;
            this.inPrimary = inPrimary;
            //this.outPrimary = outPrimary;
        }

        public void run() {
            try {
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                while (true) {
                    // Wait for client request
                    String request = data[1];
                    
                    System.out.println("Received:"+ request + " from:" + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                    if (request.equals("REQUEST_TOKEN")) {
                        // Handle token request
                        handleTokenRequest(clientSocket, inPrimary, out);
                    } else if (request.equals("RELEASE_TOKEN")) {
                        // Handle token release
                        handleTokenRelease();
                        break;
                    }
                    //out.writeUTF(request);
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
