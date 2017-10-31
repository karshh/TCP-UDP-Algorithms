
/**
 *
 * @author karsh
 */
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;


public class Receiver {
    
    private String filename;
    private DatagramSocket socket;
    
    private Receiver(boolean GBN, String filename) throws IOException, ClassNotFoundException {
        this.filename = filename;
        
        int port = 2000;
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
        writeAddressFile(InetAddress.getLocalHost().getHostName(), port);
        writeDataFile(GBN ? runGBN() : runSR());
    }
    
    private List<Packet> runGBN() throws IOException, ClassNotFoundException {
         byte[] packetbytes = new byte[512]; // MSS of packet, 500 bytes for payload + 12 size of everything else.
        DatagramPacket receivePacket = new DatagramPacket(packetbytes, packetbytes.length);
        
        int seqNumber = 0;
        boolean init0 = true;
        
        // This list will contain our packets arrainged in order.
        List<Packet> packetList = new ArrayList<Packet>();
        while(true) {
            socket.receive(receivePacket);
            InetAddress rhost = receivePacket.getAddress();
            int rport = receivePacket.getPort();
            Packet p = Packet.DECODE(receivePacket.getData());
            
            if (p.getPacketType() == 2) {
                
                // We received an EOT. Send back an EOT and break.
                System.out.println("PKT RCV EOT " + p.getPacketLength() + " " + p.getSeqNumber());
                byte[] eot = Packet.ENCODE(Packet.EOT(p.getSeqNumber()));
                DatagramPacket eotPacket = new DatagramPacket(eot, eot.length, rhost, rport);
                System.out.println("PKT SND EOT " + 12 + " " + p.getSeqNumber());
                socket.send(eotPacket);
                break;
                
            }
            
            System.out.println("PKT RCV DAT " + p.getPacketLength() + " " + p.getSeqNumber());
            if (init0 && seqNumber == 0 && p.getSeqNumber() != 0) continue;
            if (init0 == true) {
                // first packet added.
                packetList.add(p);
                init0 = false;
            }
            
            // Only accept next sequence packet and add it to the list.
            if ((seqNumber == 255 && p.getSeqNumber() == 0) || (p.getSeqNumber() == seqNumber+1)) {
                packetList.add(p);
                seqNumber++;
            }
            if (seqNumber >= 256) seqNumber = 0;
            
            // Send back a cumulative ack for the most recent sequence acknowledged.
            byte[] ack = Packet.ENCODE(Packet.ACK(seqNumber));
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, rhost, rport);
            socket.send(ackPacket);
            System.out.println("PKT SND ACK " + 12 + " " + seqNumber);
        }
        
        socket.close();
        return packetList;
    }
    
    
    private List<Packet> runSR() throws IOException, ClassNotFoundException {
         byte[] packetbytes = new byte[512]; // MSS of packet, 500 bytes for payload + 12 size of everything else.
        DatagramPacket receivePacket = new DatagramPacket(packetbytes, packetbytes.length);
        
        List<Packet> packetList = new ArrayList<Packet>();
        List<Packet> packetCache = new ArrayList<Packet>(); // used to compare for duplicate packets.
        while(true) {
            socket.receive(receivePacket);
            InetAddress rhost = receivePacket.getAddress();
            int rport = receivePacket.getPort();
            Packet p = Packet.DECODE(receivePacket.getData());
            if (p.getPacketType() == 2) {
                
                // We received an EOT. Send back an EOT and break.
                System.out.println("PKT RCV EOT " + p.getPacketLength() + " " + p.getSeqNumber());
                byte[] eot = Packet.ENCODE(Packet.EOT(p.getSeqNumber()));
                DatagramPacket eotPacket = new DatagramPacket(eot, eot.length, rhost, rport);
                System.out.println("PKT SND EOT " + 12 + " " + p.getSeqNumber());
                socket.send(eotPacket);
                break;
            }
            
            int seqNumber = p.getSeqNumber();
            System.out.println("PKT RCV DAT " + p.getPacketLength() + " " + seqNumber);
            
            // compare for Duplicates using our cache. This is done by comparing it with the 25 recent unique packets
            // acknowledged. If it's not in it, it must be unique due to the window size being 10 packets on the sender
            // side. 
            try {
                Packet pkt = packetCache.stream().filter(e -> packetsEqual(e, p)).findFirst().get();
            } catch (NoSuchElementException ex) {
                // Unique packet. Add it to our cache and the list. Note that this is much faster than just
                // comparing with the entire list.
                
                if (packetCache.size() > 25) packetCache.remove(0);
                packetCache.add(p);
                packetList.add(p);
                System.out.println("Added packet to packetList [size:" + packetList.size() + "]");
                
            }
            
            // Send an ack for the received packet.
            byte[] ack = Packet.ENCODE(Packet.ACK(seqNumber));
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, rhost, rport);
            socket.send(ackPacket);
            System.out.println("PKT SND ACK " + 12 + " " + seqNumber);
            
        }
        
        socket.close();
        
        // Our packet list is unfortunately all over the place, but it is spacial. Hence we create a new
        // sorted list, grab items in order of sequence and plac them in it.
        List<Packet> sortedPacketList = new ArrayList<Packet>();
        int seq = 0;
        while(!packetList.isEmpty()) {
            int constseq = seq;
            try {
                Packet pkt = packetList.stream().filter(e -> e.getSeqNumber() == constseq).findFirst().get();
                packetList.remove(pkt);
                sortedPacketList.add(pkt);
                seq++;
                if (seq > 255) seq = 0; 
            } catch (NoSuchElementException ex) {
                System.out.println("Could not find packet with sequence number " + constseq + ".");
                seq++;
                if (seq > 255) seq = 0; 
                
            }
        }
        
        // our sorted packet list. Return.
        return sortedPacketList;
    }
    
    // Used to compare two packets for sequence number and payload. Note that its possible for packets
    // to have the exact same payload and seq number, but within a window of 10 of mod 256, not possible.
    private boolean packetsEqual(Packet p1, Packet p2) {
        return p1.getSeqNumber() == p2.getSeqNumber() && 
                (new String(p1.getPayload()).equals((new String(p2.getPayload()))));
    }
    
    
    private void writeAddressFile(String hostname, int port) throws IOException {
        FileWriter fw = new FileWriter("recvInfo");
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(hostname + " " + port + '\n');
        bw.close();
        fw.close();
        
    }
    
    private void writeDataFile(List<Packet> packetList) throws IOException {
        FileWriter fw = new FileWriter(filename);
        BufferedWriter bw = new BufferedWriter(fw);
        for (Packet p : packetList) {
            bw.write(new String(p.getPayload()));
        }
        bw.close();
        fw.close();
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
        
        if (args.length != 2) {
            System.out.println("ERROR: Invalid number of arguments. [size:" + args.length + "]");
            System.exit(1);
        }
        
        if (!args[0].equals("0") && !args[0].equals("1")) {
            System.out.println("ERROR: Invalid protocol selector code. [" + args[0] + "]");
            System.exit(1);
            
        }
        
        // All authentications done. Start receiver.
        
        boolean GBN = args[0].equals("0");
        String filename = args[1];
        Receiver receiver = new Receiver(GBN, filename);
       
        
    }
    
    
}