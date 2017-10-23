
import java.io.*;
import java.util.*;
import java.net.*;


public class Receiver {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        if (args.length != 2) {
            System.out.println("ERROR: Invalid number of arguments. [size:" + args.length + "]");
            System.exit(1);
        }
        boolean GBN = args[0].equals("0");
        String filename = args[1];
        
        
        // get the hostname and the port.
        String hostname = InetAddress.getLocalHost().getHostName();
        int port = 2000;
        DatagramSocket socket = null;
        while (true) {
            try {
                socket = new DatagramSocket(port);
                // succes!
                break;
            } catch (BindException e) {
                port++;
            } catch (SocketException ex) {
                System.out.println("ERROR: Couldn't set up the socket with port " + port + ". [SocketException] ");
                System.exit(1);
            }
        }
        
        // we have the hostname and port, write into a file.
        FileWriter fw = new FileWriter("recvInfo");
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(hostname + " " + port + '\n');
        bw.close();
        fw.close();
        
        
        
        
        
        
        
    }
}