
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class Sender {
        
    public static void main(String[] args) throws FileNotFoundException {
        
        
        
        boolean GBN = args[0].equals("0");
        int timeout = -1;
        try {
            timeout = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR: timeout is not an integer. [" + args[1] + "]");
            System.exit(1);
        }
        String filename = args[2];
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        List<String> cred = new ArrayList<>();
        
        String line = null;
        try {
            line = reader.readLine();
            
        } catch (IOException ex) {
            System.out.println("ERROR: Could not open file '" + filename + "'.");
            System.exit(1);
        }
        
        cred.addAll(Arrays.asList(line.trim().split(" ")));
        if (cred.size() != 2) {
            System.out.println("ERROR: Incorrect number of arguments in '" + filename + "'. [size:" + cred.size() +"]");
            System.exit(1);
        }
        
        String hostname = cred.get(0);
        int port = -1;
        try {
            port = Integer.parseInt(cred.get(1));
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Port is an invalid format.");
            System.exit(1);
        }
        
        
        // sending data now.
        
    }
}