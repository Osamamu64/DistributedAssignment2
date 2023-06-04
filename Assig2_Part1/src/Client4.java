import java.io.*;
import java.net.*;

public class Client4 {
    public static void main(String args[]) {
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

                    while (true) {
                        // Read a hash from the file
                        String hash = FileReader.getHash();
                        if (hash != null) {
                            Thread.sleep(2000);

                            // Crack the password
                            String crackedPassword = BruteForce.crackPassword(hash);

                            // Write the cracked password along with the client's ID to the file
                            int clientID = Integer.parseInt(args[1]);
                            writePasswordToFile(crackedPassword, clientID);

                            System.out.println("Client ID: " + clientID + "\nCracked password: " + crackedPassword + "\nWith the hash: " + hash);
                            System.out.println("--------------------------------------------------------------------------");
                        } else {
                            System.out.println("No more hashes to crack");
                            break;  // Exit the loop when there are no more hashes
                        }
                    }

                    // Release the token
                    out.writeUTF("RELEASE_TOKEN");
                } else if (response.equals("TOKEN_DENIED")) {
                    System.out.println("TOKEN_DENIED: Another process has the token, waiting...");
                    Thread.sleep(1000);  // Wait for some time before requesting the token again
                } else {
                    throw new IllegalStateException();
                }
            }
        } catch (UnknownHostException e) {
            System.out.println("Error Socket:" + e.getMessage());
        } catch (EOFException e) {
            System.out.println("Error EOF:" + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error readline:" + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
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
        try (FileWriter writer = new FileWriter("/Users/simbaa/Desktop/DistributedAssignment2/Assig2_Part1/src/crackedPasswords.txt", true)) {
            writer.write("Client ID: " + clientID + ", Password: " + password + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
