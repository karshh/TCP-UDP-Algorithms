
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

    /*
    
    This thread is primarily used in Selective Return. Rather than having one centralized
    place to store a timer, each thread sleeps for the timeout period and then sends another
    packet, unless it's interrupted.
    
    */

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
                
                // Had some additional debug material here, removed it now. Hope that explains the single line of code
                // in this try block.
                exec();
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(Sender.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        private void exec() throws IOException, ClassNotFoundException {
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
    
    private final int timeout;
    private final PacketStorage packetStorage;
    private final DatagramSocket socket;
    private int port;
    private InetAddress host;
    
    
    private final ArrayList<Packet> window = new ArrayList<Packet>();
    
    
    public Sender(boolean GBN, int timeout, String filename) throws SocketException, IOException, ClassNotFoundException, InterruptedException {
        this.timeout = timeout;
        this.packetStorage = new PacketStorage(filename);
        this.socket = new DatagramSocket();
        this.getAddress(); // initialises host and port.
        
        // All initializations done. Start running the program.
        if (GBN) runGBN();
        else runSR();
        
        // All packets have been transmitted and acknowledged succesfully. Terminalo *escobar style*.
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
        
        // This list will contain all the packets that are currently in the window, which will be updated dynamically
        // with every acknowledged ack.
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
        
        // We send out our first batch of packets to the receiver. After that, we play the waiting game on
        // receive mode, periodically sending the packets in the window again on every timeout exception raised.
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

                // We only accept acks that are equal to or greater than the current minimum sequence in the bottom of our window list. 
                // We also take into account the modulus wraparound, and hence the extra case. 
                // The other acks can be discredited, since they're probably arriving late and an ack with an higher sequence number
                // has already been acknowledged.
                if (p.getSeqNumber() < expectedSeq || (expectedSeq >= 246 && (p.getSeqNumber() < 246 && p.getSeqNumber() >= 10))) continue;
                
                // we calculate how many packets we can disregard as arrived succesfully, as acks in GBN are cumulative so every ack less
                // than or equal to that cumulative number are taken as succesful, taking the wraparound into account as well as an edge case.
                int diff = 0;
                if (p.getSeqNumber() >= expectedSeq) diff = p.getSeqNumber() - expectedSeq + 1;
                else diff = (255 - expectedSeq) + (p.getSeqNumber() - 0) + 2; 
                expectedSeq = p.getSeqNumber() + 1; 

                // Once done, we go on a purge of packets already acked.
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
                    
                    // Debug statement.
                    //System.out.println("Window size: [" + runningThreadPackets.get(0).getSeqNumber() + "," + runningThreadPackets.get(runningThreadPackets.size() - 1).getSeqNumber() + "]");
                }

                if (runningThreadPackets.isEmpty()) {
                    // we have gotten an ack for all threads. Just break out now.
                    return;
                }  
                
            } catch (SocketTimeoutException skt) {
                
                // TIMEOUT! SEND PACKETS IN THE WINDOW AGAIN.
                for (Packet pkt : runningThreadPackets) {
                    byte[] pbytes = Packet.ENCODE(pkt);
                    socket.send(new DatagramPacket(pbytes, pbytes.length, host, port));
                    System.out.println("PKT SND DAT " + pkt.getPacketLength() + " " + pkt.getSeqNumber());
                }
            }
        }
    }
        
    private void runSR() throws IOException, ClassNotFoundException {
        // Note that in runSR, instead of having a window list of packets to send, we
        // use indices to toy around with a list of threads that are ran. 2 reasons why.
        // 1. We just have to run the thread once, and once we get an ack for the packet it sends,
        //    we interrupt and disregard it.
        // 2. It is possible in windows that certain threads may have been terminated due to the nature
        //    or runSR and it turned out slightly more complicated for me to have them in a seperate
        //    window list.
        //
        //    The indices make sure a window of 10 consecutive threads are either running or terminated,
        //    with the next new thread ran when the thread with lowest sequence in window acked. Threads
        //    terminated between this and the next unacked thread are purged.
        
        
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
            
            // If sequence number is less than the first sequence in the window then discard.
            if (p.getSeqNumber() < expectedSeq) continue;

            // We received an ack for a running thread. Interrupt and terminate it.
            for (int index = startIndex; index <= endIndex; index++) {
                if (sendThreadList.get(index).getSeqNumber() != p.getSeqNumber()) continue;
                System.out.println("SYSCALL exit " + sendThreadList.get(index).getName());
                sendThreadList.get(index).interrupt(); // this should exit the thread.
                break;
            }

            // update window if lowest sequence is acked. Also purge any threads between it
            // and next unacked packet if it's terminated. 
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

            if (startIndex > endIndex) break; // we have completed sending packets. Break out.

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
            Sender s = new Sender(args[0].equals("0"), Math.max(0, Integer.parseInt(args[1])), args[2]);
        } catch (InterruptedException ex) {
            return;
        }
    }
}