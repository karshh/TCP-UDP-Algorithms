
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Sender {
        
    public static void main(String[] args) {
        
        // Authenticate number of parameters.
        if (args.length != 3) {
            System.out.println("ERROR: Invalid number of arguments. [size:" + args.length + "]");
            System.exit(1);
        }
        
        // Authenticate protocol selector parameter.
        if (!args[0].equals("0") && !args[0].equals("1")) {
            System.out.println("ERROR: Invalid protocol selector code. [" + args[0] + "]");
            System.exit(1);
            
        }
        // Authenticate timeout parameter.
        try {
            Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR: timeout is not an integer. [" + args[1] + "]");
            System.exit(1);
        }
        
        // parameter authentication done. TODO: FILENAME AUTHENTICATION
        boolean GBN = args[0].equals("0");
        int timeout = Math.max(0, Integer.parseInt(args[1]));
        String filename = args[2];
        
        // Read in parameters.
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("channelInfo"));
        } catch (FileNotFoundException ex) {
            System.out.println("ERROR: File 'channelInfo' was not found.");
            System.exit(1);
        }
        
        
        List<String> cred = new ArrayList<>();
        try {    
            cred.addAll(Arrays.asList(reader.readLine().trim().split(" ", '\n')));
        } catch (IOException ex) {
            System.out.println("ERROR: Could not read line from 'channelInfo'.");
            System.exit(1);
        }
        
        if (cred.size() != 2) {
            System.out.println("ERROR: Incorrect number of arguments in '" + filename + "'. [size:" + cred.size() +"]");
            System.exit(1);
        }
        
        String hostname = cred.get(0);
        int port = Integer.parseInt(cred.get(1));
        
    }
}