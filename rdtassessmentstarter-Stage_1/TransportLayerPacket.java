import java.util.Arrays;

public class TransportLayerPacket {


    private int seqnum;
    private int acknum;
    private int chksum;

    byte[] data;


    public TransportLayerPacket(TransportLayerPacket pkt) {
        this.data = Arrays.copyOf(pkt.getData(), pkt.getData().length);
        this.seqnum = pkt.getSeqnum();
        this.acknum = pkt.getAcknum();
        this.chksum = pkt.getChksum();
    }

    public TransportLayerPacket(byte[] data) {
        this.data = data;
        generateChksum();
    }

    public TransportLayerPacket(int seqnum, int acknum, byte[] data) {
        this.data = Arrays.copyOf(data, data.length);
        this.seqnum = seqnum;
        this.acknum = acknum;
        generateChksum();
    }

    public void setSeqnum(int seqnum) {
        this.seqnum = seqnum;
    }

    public void setAcknum(int acknum) {
        this.acknum = acknum;
    }

    public byte[] getData() {
        return this.data;
    }

    public int getSeqnum(){
        return this.seqnum;
    }

    public int getAcknum(){
        return this.acknum;
    }

    public int getChksum() {
        return chksum;
    }

    public void incrementSeqnum(){
        this.seqnum = (this.seqnum + 1)%2;
    }

    public void incrementAcknum(){
        this.acknum = (this.acknum +1)%2;
    }

    public void generateChksum(){
        int checksum = seqnum + acknum;

        for (byte a : data) {
            checksum += a;
        }

        checksum = ((1 << 16) - 1) ^ checksum; //perform two's complement

        this.chksum = checksum;
    }

}
