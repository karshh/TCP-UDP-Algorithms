
/**
 *
 * @author karsh
 */
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sender {

    protected class SendThread extends Thread {
        private DatagramSocket socket;
        private DatagramPacket packet;

        boolean exit;

        public SendThread( DatagramSocket socket, DatagramPacket packet) {
            this.socket = socket;
            this.packet = packet;
        }
        
        @Override
        public void run() {
            try {
                execSR();
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(Sender.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        private void execSR() throws IOException, ClassNotFoundException {
            Packet p = Packet.DECODE(packet.getData());
            while(true) {
                try {
                    socket.send(packet);
                    System.out.println("PKT SND DAT " + p.getPacketLength() + " " + p.getSeqNumber());
                    sleep(timeout);
                    if (isInterrupted()) {
                        return;
                    }
                } catch (IOException ex) {
                    System.out.println("SEND ERROR: Could not send packet " + p.getSeqNumber());
                    System.exit(1);
                } catch (InterruptedException ex) {
//                    System.out.println("LOG: SendThread is exitting now.");
                    return;
                }
            }
        }
        

        public int getSeqNumber() throws IOException, ClassNotFoundException {
            return Packet.DECODE(packet.getData()).getSeqNumber();
        }
        
    }
    

        

    private final static int WINDOW_SIZE = 10;
    private final boolean GBN;
    private final int timeout;
    private final PacketStorage packetStorage;
    private final DatagramSocket socket;
    private int port;
    private InetAddress host;
    
    
    private final ArrayList<Packet> window = new ArrayList<Packet>();
    
    
    public Sender(boolean GBN, int timeout, String filename) throws SocketException, IOException, ClassNotFoundException, InterruptedException {
        this.GBN = GBN;
        this.timeout = timeout;
        this.packetStorage = new PacketStorage(filename);
        this.socket = new DatagramSocket();
        this.getAddress(); // initialises host and port.
        
        // start the receiver thread.
        
        if (GBN) runGBN();
        else runSR();
        
        terminalo();
    }
   
    
    private void getAddress() {
        
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
            System.out.println("ERROR: Incorrect number of arguments in 'channelInfo'. [size:" + cred.size() +"]");
            System.exit(1);
        }
        
        this.host = null;
        try {
            host = InetAddress.getByName(cred.get(0));
        } catch (UnknownHostException ex) {
            System.out.println("Could not find host " + cred.get(0));
            System.exit(1);
        }
        this.port = Integer.parseInt(cred.get(1));
        
    }
    
    
        private void terminalo() throws IOException, ClassNotFoundException {
            byte[] eot = Packet.ENCODE(Packet.EOT(0));
                byte[] eotReturn = new byte[512];
                DatagramPacket eotPacket = new DatagramPacket(eot, eot.length, host, port);
                DatagramPacket eotReturnPacket = new DatagramPacket(eotReturn, eotReturn.length);
                socket.send(eotPacket);
                System.out.println("PKT SND EOT " + 12 + " " + 0);
                
                while (true) {
                    socket.receive(eotReturnPacket);
                    Packet p = Packet.DECODE(eotReturnPacket.getData());
                    if (p.getPacketType() == 2) {
                        System.out.println("PKT RCV EOT " + 12 + " " + p.getSeqNumber());
                        socket.close();
    //                    System.out.println("LOG: Receiver is now exitting");
                        return;
                    } else {
                        continue;
                    }
                    
                }
        }
        
        private void runGBN() throws IOException, ClassNotFoundException {
            List<Packet> sendPacketList = populateSendPacketList();
            int startIndex = 0;
            int endIndex = startIndex + Math.min(sendPacketList.size() - 1, WINDOW_SIZE - 1);
            List<Packet> runningThreadPackets = new ArrayList<Packet>();
            
            for (int i = startIndex; i <= endIndex; i++) {
                byte[] pb = Packet.ENCODE(sendPacketList.get(i));
                runningThreadPackets.add(sendPacketList.get(i));
            }
            byte[] packetBytes = new byte[512];
            DatagramPacket dp = new DatagramPacket(packetBytes, packetBytes.length);
            Packet p = null;
            int expectedSeq = 0;
            socket.setSoTimeout(timeout);
            for (Packet pkt : runningThreadPackets) {
                byte[] pbytes = Packet.ENCODE(pkt);
                socket.send(new DatagramPacket(pbytes, pbytes.length, host, port));
                System.out.println("PKT SND DAT " + pkt.getPacketLength() + " " + pkt.getSeqNumber());
            }
            while(true) {
                try {
                    socket.receive(dp);
                    p = Packet.DECODE(dp.getData());
                    System.out.println("PKT RCV ACK " + p.getPacketLength() + " " + p.getSeqNumber());
                    
                    if (p.getSeqNumber() < expectedSeq || (expectedSeq >= 246 && (p.getSeqNumber() < 246 && p.getSeqNumber() >= 10))) continue;
    //                System.out.println("LOG: Got matching seqNumber value.");
                    
                    int diff = 0;
                    if (p.getSeqNumber() >= expectedSeq) diff = p.getSeqNumber() - expectedSeq + 1;
                    else diff = (255 - expectedSeq) + (p.getSeqNumber() - 0) + 2; 
                    expectedSeq = p.getSeqNumber() + 1; 
                    
                    for (; diff > 0; diff--) {
                        if (runningThreadPackets.isEmpty()) break;
                        runningThreadPackets.remove(runningThreadPackets.get(0));
                        if (endIndex + 1 < sendPacketList.size()) {
                            endIndex++;
                            byte[] pbytes = Packet.ENCODE(sendPacketList.get(endIndex));
                            socket.send(new DatagramPacket(pbytes, pbytes.length, host, port));
                            System.out.println("PKT SND DAT " + sendPacketList.get(endIndex).getPacketLength() + " " + sendPacketList.get(endIndex).getSeqNumber());
                            runningThreadPackets.add(sendPacketList.get(endIndex));
                        }
                        
//                        System.out.println("Window size: [" + runningThreadPackets.get(0).getSeqNumber() + "," + runningThreadPackets.get(runningThreadPackets.size() - 1).getSeqNumber() + "]");
                    }

                    if (runningThreadPackets.isEmpty()) {
                        return;
                    } // we have gotten an ack for all threads. Just break out now. 
    //                System.out.println("LOG: Initiating new timer thread.");
                    
                } catch (SocketTimeoutException skt) {
                    for (Packet pkt : runningThreadPackets) {
                        byte[] pbytes = Packet.ENCODE(pkt);
                        socket.send(new DatagramPacket(pbytes, pbytes.length, host, port));
                        System.out.println("PKT SND DAT " + pkt.getPacketLength() + " " + pkt.getSeqNumber());
                    }
                }
            }
        }
        
        private void runSR() throws IOException, ClassNotFoundException {
            
            List<SendThread> sendThreadList = populateSendThreadList();
            int startIndex = 0;
            int endIndex = startIndex + Math.min(sendThreadList.size() - 1, WINDOW_SIZE - 1);
            for (int i = startIndex; i <= endIndex; i++) {
                System.out.println("SYSCALL start " + sendThreadList.get(i).getName());
                sendThreadList.get(i).start();
            }
            
            byte[] packetBytes = new byte[512];
            DatagramPacket dp = new DatagramPacket(packetBytes, packetBytes.length);
            Packet p = null;
            int expectedSeq = 0;
            while(true) {
                socket.receive(dp);
                p = Packet.DECODE(dp.getData());
                System.out.println("PKT RCV ACK " + p.getPacketLength() + " " + p.getSeqNumber());
                // CASE: If sequence number is less than the first sequence in the window then discard.
                if (p.getSeqNumber() < expectedSeq) continue;
                
                for (int index = startIndex; index <= endIndex; index++) {
                    if (sendThreadList.get(index).getSeqNumber() != p.getSeqNumber()) continue;
//                    System.out.println("LOG: Interrupted " + index + ". Window [" + startIndex + "," + endIndex +"]");
                        System.out.println("SYSCALL exit " + sendThreadList.get(index).getName());
                    sendThreadList.get(index).interrupt(); // this should exit the thread.
                    break;
                }
                
                if (p.getSeqNumber() == expectedSeq) {
                    startIndex++;
                    expectedSeq++;
                    if (expectedSeq >= 256) expectedSeq = 0;
                    if (endIndex + 1 < sendThreadList.size()) {
                        endIndex++;
                        System.out.println("SYSCALL start " + sendThreadList.get(endIndex).getName());
                        sendThreadList.get(endIndex).start();
                    }
                    while(startIndex < sendThreadList.size() && (sendThreadList.get(startIndex).getState().equals(Thread.State.TERMINATED))) {
                        startIndex++;
                        expectedSeq++;
                        if (expectedSeq >= 256) expectedSeq = 0;
                        if (endIndex + 1 < sendThreadList.size()) {
                            endIndex++;
                            sendThreadList.get(endIndex).start();
                        }
                    }
                }
                
                if (startIndex > endIndex) {
                    
                    break;
                } // we have completed sending packets. Break out.
                
                
            }
        }
        
        private List<SendThread> populateSendThreadList() throws IOException, ClassNotFoundException {
            List<SendThread> sendThreadList = new ArrayList<>();
            while (true) {
                if (packetStorage.isPacketListEmpty()) break;
                byte[] packetBytes = Packet.ENCODE(packetStorage.getPacket());
                SendThread t = new SendThread(socket, new DatagramPacket(packetBytes, packetBytes.length, host, port));
                sendThreadList.add(t);
            }
            return sendThreadList;
        }
        
        private List<Packet> populateSendPacketList() throws IOException, ClassNotFoundException {
            List<Packet> sendPacketList = new ArrayList<Packet>();
            while (true) {
                if (packetStorage.isPacketListEmpty()) break;
                Packet p = packetStorage.getPacket();
                sendPacketList.add(p);
            }
            return sendPacketList;
            
        }
    
    public static void main(String[] args) throws SocketException, IOException, ClassNotFoundException {
        
        
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
        
        try {
            //  System.out.println("LOG: Beginning Sender code.");
            // Authentication done. Instantiate Sender.
            Sender s = new Sender(args[0].equals("0"), Math.max(0, Integer.parseInt(args[1])), args[2]);
        } catch (InterruptedException ex) {
            return;
        }
    }
}