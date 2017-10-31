
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;


public class Receiver {
    
    private boolean GBN;
    private String filename;
    private DatagramSocket socket;
    
    private Receiver(boolean GBN, String filename) throws IOException, ClassNotFoundException {
        this.GBN = GBN;
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
        
        List<Packet> packetList = new ArrayList<Packet>();
        while(true) {
            socket.receive(receivePacket);
            InetAddress rhost = receivePacket.getAddress();
            int rport = receivePacket.getPort();
            Packet p = Packet.DECODE(receivePacket.getData());
            if (p.getPacketType() == 2) {
                
                System.out.println("PKT RCV EOT " + p.getPacketLength() + " " + p.getSeqNumber());
                // got an EOT. Send an EOT back.
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
            
            if ((seqNumber == 255 && p.getSeqNumber() == 0) || (p.getSeqNumber() == seqNumber+1)) {
                packetList.add(p);
                seqNumber++;
            }
            if (seqNumber >= 256) seqNumber = 0;
            byte[] ack = Packet.ENCODE(Packet.ACK(seqNumber));
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, rhost, rport);
            socket.send(ackPacket);
            System.out.println("PKT SND ACK " + 12 + " " + seqNumber);
        }
        
        socket.close();
        System.out.println("Packetlist size:" + packetList.size());
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
                System.out.println("PKT RCV EOT " + p.getPacketLength() + " " + p.getSeqNumber());
                // got an EOT. Send an EOT back.
                byte[] eot = Packet.ENCODE(Packet.EOT(p.getSeqNumber()));
                DatagramPacket eotPacket = new DatagramPacket(eot, eot.length, rhost, rport);
                System.out.println("PKT SND EOT " + 12 + " " + p.getSeqNumber());
                socket.send(eotPacket);
                break;
            }
            
            int seqNumber = p.getSeqNumber();
            System.out.println("PKT RCV DAT " + p.getPacketLength() + " " + seqNumber);
            
            // compare for duplicates
            try {
                Packet pkt = packetCache.stream().filter(e -> packetsEqual(e, p)).findFirst().get();
            } catch (NoSuchElementException ex) {
                if (packetCache.size() > 25) packetCache.remove(0);
                packetCache.add(p);
                packetList.add(p);
                System.out.println("Added packet to packetList [size:" + packetList.size() + "]");
                
            }
            
            byte[] ack = Packet.ENCODE(Packet.ACK(seqNumber));
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, rhost, rport);
            socket.send(ackPacket);
            System.out.println("PKT SND ACK " + 12 + " " + seqNumber);
            
        }
        
        socket.close();
        
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
        
        return sortedPacketList;
    }
    
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
        System.out.println("Packetlist size:" + packetList.size());
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
        boolean GBN = args[0].equals("0");
        String filename = args[1];
        
        Receiver receiver = new Receiver(GBN, filename);
        // for now, test and print out whats in the data
       
        
    }
    
    
}