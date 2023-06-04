import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class FileReader {
    private static List<String> hashList = new ArrayList<>();
    private static int currentIndex = 0;

    public static String getHash() {
        if (hashList.isEmpty()) {
            getHashesFromFile();
            //currentIndex = 0;
        }

        String hash = null;
        if (!hashList.isEmpty()) {
            hash = hashList.get(currentIndex);
            currentIndex++;
        }
        
        if (currentIndex >= hashList.size())
        	return null;

        return hash;
    }

    private static void getHashesFromFile() {
        try (FileInputStream fileInputStream = new FileInputStream("C:\\Users\\abdul\\OneDrive\\Desktop\\SWE\\Assig2_Part1\\src\\sharedResource.txt");
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            hashList.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                hashList.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

