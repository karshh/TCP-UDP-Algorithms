
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;



class client {
    
    public static void main(String argv[]) throws IOException, Exception {
        // Assert for arguments.
        int error = 0;
        if (argv.length != 4) {
            if (argv.length == 1) {
                System.out.println("ERROR: Missing all parameters. [argv.length=" + argv.length + "]");
                System.exit(1);
            }
            else if (argv.length == 2) {
                System.out.println("ERROR: Missing n_port and req_code. [argv.length=" + argv.length + "]");
            } else if (argv.length == 3) {
                System.out.println("ERROR: Missing req_code. [argv.length=" + argv.length + "]");
            } else {
                System.out.println("ERROR: argv.length=" + argv.length);
            }
            error = 1;
        }
        
        int n_port = -1;
        try {
            if (argv.length >= 3) n_port = Integer.parseInt(argv[1]);
        } catch (NumberFormatException e){
            System.out.println("ERROR: n_port[" + argv[1] + "] is not an integer.");
            error = 1;
        } 
        
        try {
            if (argv.length >= 4) Integer.parseInt(argv[2]); // check if req_code is an integer.
        } catch (NumberFormatException e){
            System.out.println("ERROR: req_code[" + argv[2] + "] is not an integer.");
            error = 1;
        } 
        
        if (error == 1) System.exit(1);
        
        
        if (n_port == -1) throw new IOException("n_port is -1.");
        
        // All integer and commandline authentications done at this point. Moving on..
        DatagramSocket socket = new DatagramSocket();
        byte[] sendReqCode = new byte[1024];
        byte[] receiveReqCode = new byte[1024];
        InetAddress host = null;
        // Giving user a clean error statement here if the host name cannot be authenticated.
        try {
            host = InetAddress.getByName(argv[0]) ;
        } catch (UnknownHostException e) {
            System.out.println("ERROR: Name of host " + argv[0] + " not known");
            System.exit(1);
        }
        if (host == null) throw new NullPointerException("host is null."); // should not get here.
        String req_code = argv[2];
        sendReqCode = String.valueOf(req_code).getBytes();
        // Sending request code to server.
        DatagramPacket sendPacket = new DatagramPacket(sendReqCode, sendReqCode.length, host, n_port); 
        socket.send(sendPacket);
        socket.setSoTimeout(1000); // Setting timeout to 1 second. Hopefully this suffices.
        
        // At this stage we are parsing the TCP socket port.
        DatagramPacket receivePacket = new DatagramPacket(receiveReqCode, receiveReqCode.length);
        try {
            socket.receive(receivePacket);
            String r_port = new String(receivePacket.getData());
            socket.send(new DatagramPacket(r_port.getBytes(), r_port.getBytes().length, host, n_port));
            byte[] confirmationCode = new byte[32];
            socket.receive(new DatagramPacket(confirmationCode, confirmationCode.length));
            if ((new String(confirmationCode).substring(0, 2).equals("OK"))) {
            } else if ((new String(confirmationCode).substring(0, 2).equals("NO"))) {
                socket.close();
                System.exit(0);
            } else {
                String err = new String(confirmationCode).substring(0, 2);
                System.out.println("Illegal server response: " + err);
                socket.close();
                System.exit(1);
            }
            // Some annoying math to ensure we get all port digits right. 
            int portSize;
            for (portSize = 0; r_port.charAt(portSize) >= '0' && r_port.charAt(portSize) <= '9'; portSize++) {}
            
            // create TCP connection and send the string over.
            Socket connSocket = new Socket(host, Integer.parseInt(r_port.substring(0, portSize)));
            DataOutputStream outToServer = new DataOutputStream(connSocket.getOutputStream());
            BufferedReader inFromServer = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
            
            outToServer.writeBytes(argv[3] + '\n');
            
            // At this point we should receive the reversed string from the server. Printing it out immediately after
            // and closing the socket.
            String reversedSentence = inFromServer.readLine();
            System.out.println("CLIENT_MSG=" + reversedSentence );
            
            connSocket.close();
            socket.close();
            
        } catch (SocketTimeoutException e) {
            System.out.println("Socket has timed out.");
            socket.close();
        }
        
        
    }

}