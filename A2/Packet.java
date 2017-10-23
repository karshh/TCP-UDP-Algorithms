
import java.io.*;

public class Packet implements Serializable {
    
    private int packetType;
    private int packetLength;
    private int seqNumber;
    private byte[] payload;
    
    private Packet(int packetType, int seqNumber, byte[] msg) {
        if (msg.length > 500) {
            System.out.println("ERROR: msg length is longer than 500. [length:" + msg.length + "]");
            System.exit(1);
        }
        
        this.packetType = packetType;
        this.seqNumber = seqNumber;
        this.payload = msg;
        this.packetLength = this.payload.length + 3;
        
    }
    
    public static Packet ACK(int seq) {
        return new Packet(1, seq, new byte[0]);
    }
    
    public static Packet EOF(int seq){
        return new Packet(2, seq, new byte[0]);
    }
    
    public static Packet DATA(int seq, byte[] data) {
        return new Packet(0, seq, data);
    }
    
    public static Packet DECODE(byte[] pktbyte) throws IOException, ClassNotFoundException{
        ObjectInput input = new ObjectInputStream(new ByteArrayInputStream(pktbyte));
        Packet pkt = (Packet) input.readObject();
        input.close();
        return pkt;
    }
    
    public static byte[] ENCODE(Packet pkt) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ObjectOutput output = new ObjectOutputStream(stream);
        output.writeObject(pkt);
        output.flush();
        byte[] pktbyte = stream.toByteArray();
        stream.close();
        return pktbyte;
    }
    
    public int getPacketType() {
        return packetType; 
    }
    
    public int getSeqNumber() {
        return seqNumber;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public int getPacketLength() {
        return packetLength;
    }
}

