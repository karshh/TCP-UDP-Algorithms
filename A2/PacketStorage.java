
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class PacketStorage {
    private List<Packet> packetList = new ArrayList<Packet>();
    
    public PacketStorage(String filename) throws IOException {
        
        this.populatePacketList(filename);
        
    }
    
    private void populatePacketList(String filename) throws IOException {
        
        packetList.clear();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException ex) {
            System.out.println("ERROR: File "+ filename + "' was not found.");
            System.exit(1);
        }
        byte[] filebytes = Files.readAllBytes(Paths.get(filename));
        reader.close();
        int offset = 0;
        int seq = 0;
        while (true) {
            byte[] packetBytes = Arrays.copyOfRange(filebytes, offset, Math.min(filebytes.length, offset+500));
            offset += 500;
            packetList.add(Packet.DATA(seq, packetBytes));
            seq++;
            if (seq >= 256) seq = 0;
            if (filebytes.length <= offset) break;
        }
        
    }
    
    
    public boolean isPacketListEmpty() {
        return this.packetList.isEmpty();
    }
    
    public Packet getPacket() {
        if (this.isPacketListEmpty()) {
            System.out.println("ERROR: Out of packets to get.");
            System.exit(1);
        }
        Packet p = packetList.get(0);
        packetList.remove(p);
        return p;
    }
    
   
    /*
        for test purposes
    */
//    public static void main(String[] args) throws IOException {
//        PacketStorage ps = new PacketStorage("testinput");
//        
//        for (Packet p : ps.packetList) {
//            System.out.println("Packet " + p.getSeqNumber() + "[" + p.getPayload().length + "] :" + (new String(p.getPayload())));
//            
//        }
//    }
    
    
    
    
}