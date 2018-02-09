
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Client {
    
    static boolean DEBUG = false;
    // F
    public Client(String host, int port) throws IOException {
        byte[] keyByte = new byte[9];
        for (int i = 0; i < keyByte.length; i++) keyByte[i] = 0;
        keyByte = ("F").getBytes();
        
        DataOutputStream outToServer = new DataOutputStream((new Socket(host, port)).getOutputStream());
        if (DEBUG) System.out.println("SND KEY " + (new String(keyByte)) + "[" + keyByte.length + "]");
        outToServer.write(keyByte); // send the F command.
    }
    
    // P
    public Client(String host, int port, String key, String filename, int bufferSize, int timeOut) throws IOException, InterruptedException {
        byte[] keyByte = new byte[9];
        for (int i = 0; i < keyByte.length; i++) keyByte[i] = 0;
        keyByte = key.getBytes();
        
        try {
            Socket connSocket = new Socket(host, port);
            OutputStream os = connSocket.getOutputStream();
            FileInputStream fis = new FileInputStream(filename);
            if (DEBUG) System.out.println("SND KEY " + (new String(keyByte)) + "[" + keyByte.length + "]");
            os.write(keyByte);
            byte[] inputArray = new byte[bufferSize];
            int count;
            while ((count = fis.read(inputArray)) >= 0) {
                os.write(inputArray, 0, count);
                   if (DEBUG) System.out.println("SND DATA [" + count + "]");
                if (count < bufferSize) break; // the block was the final block.
                Thread.sleep(timeOut);
            }
            
            fis.close();
            os.close();
            connSocket.close();
            
        } catch (FileNotFoundException ex1) {
            System.out.println("ERROR: File " + filename + " does not exist.");
            System.exit(1);
        } catch (UnknownHostException ex2) {
            System.out.println("ERROR: Host " + host + "[" + port +"]" + " is not found.");
            System.exit(1);
        }
        
    }
    
    // G
    public Client(String host, int port, String key, String filename, int bufferSize) throws IOException {
        byte[] keyByte = new byte[9];
        for (int i = 0; i < keyByte.length; i++) keyByte[i] = 0;
        keyByte = key.getBytes();
        
        try {
            Socket connSocket = new Socket(host, port);
            OutputStream os = connSocket.getOutputStream();
            InputStream is = connSocket.getInputStream();
            FileOutputStream fos = new FileOutputStream(filename);
            if (DEBUG) System.out.println("SND KEY " +  (new String(keyByte)) + "[" + keyByte.length + "]");
            os.write(keyByte);
            byte[] inputArray = new byte[bufferSize];
            int count;
            while (connSocket.isConnected()) {
                count = is.read(inputArray);
                if (DEBUG) System.out.println("RCV DATA " + "[" + count + "]");
                if (count < 0) break;
                fos.write(inputArray, 0, count);
                //if (count < bufferSize) break; // the block was the final block.
            }
            
            fos.close();
            is.close();
            connSocket.close();
            
        } catch (UnknownHostException ex2) {
            System.out.println("ERROR: Host " + host + "[" + port +"]" + " is not found.");
            System.exit(1);
        } 
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        
        if (args.length < 3) {
            System.out.println("ERROR: Invalid number of parameters.");
            System.exit(1);
        }
        
        String host = args[0];
        String key = args[2]; 
        
        try {
            int port = Integer.parseInt(args[1]);
            if (args[2].charAt(0) == 'F') {
                new Client(host, port);
                return; // we sent the exit code. Just return.
            } 

            if (args.length < 5) {
                System.out.println("ERROR: Invalid number of parameters.");
                System.exit(1);
            }
            // At this point, we should only be processing P and G cases.
            String filename = args[3];
            int size = Integer.parseInt(args[4]);

            if (key.charAt(0) == 'P') {
                
                if (args.length < 6) {
                    System.out.println("ERROR: Invalid number of parameters.");
                    System.exit(1);
                }
                int waitTime = Integer.parseInt(args[5]);
                new Client(host, port, key, filename, size, waitTime);
                return; // we sent the exit code. Just return.
            }

            else if (key.charAt(0) == 'G') {
                new Client(host, port, key, filename, size);
                return;
            }
            
        } catch(SocketException ex) {
            // server closed the connection, perhaps because the key was not found. Close the connection.
            return;
        } catch(NumberFormatException ex2) {
            System.out.println("ERROR: Invalid parameter format.");
            return;
        }catch(UnknownHostException ex3) {
            System.out.println("ERROR: Unknown host and port.");
            return;
        }
        
    }
}