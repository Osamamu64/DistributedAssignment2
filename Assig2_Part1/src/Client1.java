import java.io.*;
import java.net.*;

public class Client1 {
    public static void main(String args[]) throws UnknownHostException, IOException, InterruptedException {
        // args[0] = Server IP
        // args[1] = Client ID

        Socket serverSocket = null;
        int serverPort = 50000;

        try {
            
            serverSocket = new Socket(args[0], serverPort);
            DataInputStream in = new DataInputStream(serverSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());

            boolean tokenGranted = false;

            while (!tokenGranted) {
                // Request token from the server
                out.writeUTF("REQUEST_TOKEN");
                String response = in.readUTF();

                if (response.equals("TOKEN_GRANTED")) {
                    tokenGranted = true;
                    System.out.println("Token granted, performing the critical section...");

                    // Read a hash from the file and start cracking
                    while (true) {
                        String hash = FileReader.getHash();
                        if (hash != null) {
                            Thread.sleep(2000);
                           

                            // Crack the password
                            String crackedPassword = BruteForce.crackPassword(hash);

                            // Write the cracked password along with the client's ID to the file
                            int clientID = Integer.parseInt(args[1]);
                            writePasswordToFile(crackedPassword, clientID);

                            System.out.println("Client ID: " + clientID + "\nCracked password: " + crackedPassword
                                    + "\nWith the hash: " + hash);
                            System.out.println("--------------------------------------------------------------------------");
                        } else {
                            System.out.println("No more hashes to crack");
                            break; // Exit the loop when there are no more hashes
                        }
                    }
                } else if (response.equals("TOKEN_DENIED")) {
                    System.out.println("TOKEN_DENIED: Another process has the token, waiting...");
                     // Wait for some time before requesting the token again
                     Thread.sleep(2000);
                } else {
                    throw new IllegalStateException();
                }
            }

            // Release the token
            out.writeUTF("RELEASE_TOKEN");

            // Close the connection with the primary server
            serverSocket.close();

            // Connect to the backup server
            

            // Perform additional tasks with the backup server
            // ...

        } catch (UnknownHostException e) {
            System.out.println("Error Socket:" + e.getMessage());
        }   catch (EOFException e) {
            System.out.println("Error EOF: Connection closed by the server");
        
            // Connect to the backup server and perform additional tasks
            try {
                boolean connectedToBackupServer = false;
                while (!connectedToBackupServer) {
                    try {
                        // Connect to the backup server
                        Socket backupSocket = new Socket(args[0], 50000);
                        System.out.println("Connected to the backup server");
                        int clientID = Integer.parseInt(args[1]);
                        // Perform additional tasks with the backup server
                        performBackupTasks(backupSocket, clientID);
        
                        connectedToBackupServer = true;
                    } catch (IOException ex) {
                        System.out.println("Error connecting to the backup server, retrying...");
                        Thread.sleep(5000); // Wait for some time before retrying
                    }
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
            catch (IOException e) {
            System.out.println("Error readline:" + e.getMessage());
        }finally {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void writePasswordToFile(String password, int clientID) {
        try (FileWriter writer = new FileWriter(
                "/Users/simbaa/Desktop/DistributedAssignment2/Assig2_Part1/src/crackedPasswords.txt", true)) {
            writer.write("Client ID: " + clientID + ", Password: " + password + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void performBackupTasks(Socket backupSocket, int clientID) {
        try {
            DataInputStream in = new DataInputStream(backupSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(backupSocket.getOutputStream());
    
            boolean tokenGranted = false;
    
            while (!tokenGranted) {
                // Request token from the server
                out.writeUTF("REQUEST_TOKEN");
                String response = in.readUTF();
    
                if (response.equals("TOKEN_GRANTED")) {
                    tokenGranted = true;
                    System.out.println("Token granted, performing the critical section...");
    
                    // Read a hash from the file and start cracking
                    while (true) {
                        String hash = FileReader.getHash();
                        if (hash != null) {
                            Thread.sleep(2000);
    
                            // Crack the password
                            String crackedPassword = BruteForce.crackPassword(hash);
    
                            // Write the cracked password along with the client's ID to the file
                            
                            writePasswordToFile(crackedPassword, clientID);
    
                            System.out.println("Client ID: " + clientID + "\nCracked password: " + crackedPassword
                                    + "\nWith the hash: " + hash);
                            System.out.println("--------------------------------------------------------------------------");
                        } else {
                            System.out.println("No more hashes to crack");
                            break; // Exit the loop when there are no more hashes
                        }
                    }
                } else if (response.equals("TOKEN_DENIED")) {
                    System.out.println("TOKEN_DENIED: Another process has the token, waiting...");
                    // Wait for some time before requesting the token again
                    Thread.sleep(2000);
                } else {
                    throw new IllegalStateException();
                }
            }
    
            // Release the token
            out.writeUTF("RELEASE_TOKEN");
    
            // Close the connection with the backup server
            backupSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
