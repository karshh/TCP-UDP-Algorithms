
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;



public class Server {
    
    static boolean DEBUG = false;
    
    protected static class TransferThread extends Thread {
        
        Socket inSocket;
        Socket outSocket;
        
        public TransferThread(Socket inSocket, Socket outSocket) {
            this.inSocket = inSocket;
            this.outSocket = outSocket;
        }
        @Override
        public void run() {
            if (DEBUG) System.out.println(this.getName() + " INIT");
            int count;
            
            try {
                InputStream connInput = inSocket.getInputStream();
                OutputStream connOutput = outSocket.getOutputStream();
                count = 0;
                // Naive approach. Just write everything from incoming socket to outgoing socket in blocks of 1kb.
                while (true) {
                    byte[] inputArray = new byte[1024];
                    if ((count = connInput.read(inputArray)) < 0) break;
                    if (DEBUG) System.out.println(this.getName() + ":TFR DATA [" + count + "]");
                    connOutput.write(inputArray, 0, count);
                }
                
                connInput.close();
                connOutput.close();
                inSocket.close();
                outSocket.close();
                if (DEBUG) System.out.println(this.getName() + " EXIT");
                
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            } 
        }
    }
    
    CharsetEncoder ASCII_ENCODER = StandardCharsets.US_ASCII.newEncoder();
    
    private String getAsciiString(String v) {
        char[] strChar = v.toCharArray();
        char[] strChar2 = v.toCharArray();
        for (int i = strChar.length - 1; i >= 0; i--) {
            if (ASCII_ENCODER.canEncode(strChar[i])) break;
            strChar2[i] = 0;
        }
        return (new String(strChar2));
    } 
    
    private Server(ServerSocket tcpSocket) throws IOException {
        writePort(Integer.toString(tcpSocket.getLocalPort())); 
           
        HashMap<String, Socket> waitList = new HashMap<>(); // key of downloader to the downloader socket
        
        
        Socket s = null;
        int count = 0;
        
        while(true) {
            byte[] inputBytes = new byte[9];
            s = tcpSocket.accept();
            count = s.getInputStream().read(inputBytes); 
            
            String controlInfo = getAsciiString(new String(inputBytes));
            
            if (DEBUG) System.out.println("RCV KEY " + controlInfo + "[" + count + "]");
            
            if (controlInfo.substring(0, 1).equals("F")) {
                if (DEBUG) System.out.println("EXIT");
                for (Socket so : waitList.values()) so.close();
                break;
            }
            
            if (controlInfo.substring(0, 1).equals("G")) {
                if (DEBUG) System.out.println("Add " + controlInfo.substring(1) + " to waitlist.");
                waitList.put(controlInfo.substring(1), s);
                continue;
            }
            
            if (controlInfo.substring(0, 1).equals("P")) {
                if (DEBUG) System.out.println("Get " + controlInfo.substring(1) + " from waitlist.");
                String key = controlInfo.substring(1);
                Socket s2 = waitList.remove(key);
                if (s2 == null && DEBUG) {
                    System.out.println("No key named " + controlInfo.substring(1) + " in waitlist.");
                    s.close();
                    continue;
                }
                if (DEBUG) System.out.println("Key " + controlInfo.substring(1) + " found. Init thread");
                (new TransferThread(s, s2)).start();
                continue;
            }
            
            // check if new downloader. 
            
            
            
        }
        
    }
    
    private static void writePort(String port) throws IOException {
        System.out.println(port);
        FileWriter fw = new FileWriter("port");
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(port + '\n');
        bw.close();
        fw.close();
    } 
    
    public static void main(String[] args) throws IOException { 
        Server server = new Server(new ServerSocket(0)); // this should randomly generate a TCP port.
    }
}