
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;




class server {
    
    public static void main(String argv[]) throws IOException {
        // Assert for arguments.
        
        int error = 0;
        if (argv.length != 1) {
            if (argv.length == 0) {
                System.out.println("ERROR: Missing req_code. [argv.length=" + argv.length + "]");
            } else {
                System.out.println("ERROR: argv.length=" + argv.length);                
            }
            error = 1;
        }
        try {
            if (argv.length >= 1) Integer.parseInt(argv[0]);  // Authenticates that the req_code is an integer 
        } catch (NumberFormatException e){
            System.out.println("ERROR: req_code[" + argv[0] + "] is not an integer.");
            error = 1;
        }
        if (error == 1) System.exit(1);
        
        // At this point, we have authenticated all paramters. Proceeding with listening on a UDP socket.
        
        String req_code = argv[0];
        
        int n_port = 1024; // random starting point port. Will be incremented if unavailable.
        DatagramSocket socket = null;
        while (true) {
            try {
                socket = new DatagramSocket(n_port);
                break;
            } catch (BindException e) {
                n_port++;
            }
            
        }
        
        if (socket == null) throw new NullPointerException("Null socket."); // we should never get to this exception.
        System.out.println("SERVER_PORT=" + socket.getLocalPort());
        
        while(true) {
            byte[] receiveReqCode = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveReqCode, receiveReqCode.length);
            socket.receive(receivePacket);
            InetAddress host = receivePacket.getAddress();
            int port = receivePacket.getPort();
            String req_code_received = new String(receivePacket.getData());
            if (!req_code_received.substring(0, req_code.length()).equals(req_code)) {
                continue; // Illegal code. Ignoring request...
            }
            
            ServerSocket tcpSocket = new ServerSocket(0); // this should randomly generate a TCP port.
            String r_port = Integer.toString(tcpSocket.getLocalPort());
            System.out.println("SERVER_TCP_PORT=" + r_port);
            socket.send(new DatagramPacket(r_port.getBytes(),r_port.getBytes().length, host, port));
            byte[] confirmationCode = new byte[1024];
            socket.receive(new DatagramPacket(confirmationCode, confirmationCode.length));
            if ((new String(confirmationCode).substring(0, r_port.length()).equals(r_port))) {
                socket.send(new DatagramPacket(("OK").getBytes(),("OK").getBytes().length, host, port));
            } else {
                socket.send(new DatagramPacket(("NO").getBytes(),("NO").getBytes().length, host, port));
                // the client is assumed to do nothing once it gets this response other than close the connection.
                // therefore, just continue.
                continue; 
            }
            
            // accepting the client connection, and receiving it's string.
            Socket connSocket = tcpSocket.accept();
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
            DataOutputStream outToClient = new DataOutputStream(connSocket.getOutputStream());
            
            String clientSentence = inFromClient.readLine();
            
            System.out.println("SERVER_RCV_MSG=" + clientSentence );
            
            // these three statements should do the reverse trick.
            StringBuilder reverseProc = new StringBuilder();
            reverseProc.append(clientSentence);
            reverseProc.reverse();
            
            // Send the reverse string back.
            outToClient.writeBytes((new String(reverseProc)) + '\n');
            
            // Our business with this client is over.
        }
    }
    
}