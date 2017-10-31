
/**
 *
 * @author karsh
 */
import java.io.*;
import java.nio.ByteBuffer;

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
        this.packetLength = this.payload.length + 12;
        
    }
    
    public static Packet ACK(int seq) {
        return new Packet(1, seq, new byte[0]);
    }
    
    public static Packet EOT(int seq){
        return new Packet(2, seq, new byte[0]);
    }
    
    public static Packet DATA(int seq, byte[] data) {
        return new Packet(0, seq, data);
    }
    
    public static Packet DECODE(byte[] pktbyte) throws IOException, ClassNotFoundException{
        ByteBuffer buffer = ByteBuffer.wrap(pktbyte);
        int pktType = buffer.getInt();
        int pktLen = buffer.getInt();
        int seqNum = buffer.getInt();
        byte payload[] = new byte[pktLen-12];
        buffer.get(payload, 0, pktLen-12);
        return new Packet(pktType, seqNum, payload);
        
    }
    
    public static byte[] ENCODE(Packet pkt) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(pkt.getPacketLength()); // max 500 bytes for payload, 4 bytes for header
        buffer.putInt(pkt.getPacketType());
        buffer.putInt(pkt.getPacketLength());
        buffer.putInt(pkt.getSeqNumber());
        buffer.put(pkt.getPayload(), 0, pkt.getPayload().length);
        return buffer.array();
        
    }
    
    public int getPacketType() {
        return packetType; 
    }
    
    public int getSeqNumber() {
        return seqNumber;
    }
    
    
    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public int getPacketLength() {
        return packetLength;
    }
    
}

