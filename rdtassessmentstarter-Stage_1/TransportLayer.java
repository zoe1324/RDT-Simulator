public abstract class TransportLayer {

    String name;
    NetworkSimulator simulator;

    public TransportLayer(String name, NetworkSimulator simulator) {
        this.name = name;
        this.simulator = simulator;
    }

    public abstract void init();

    public abstract void rdt_send(byte[] data);

    public abstract void rdt_receive(TransportLayerPacket pkt);

    public abstract void timerInterrupt();

    public String getName() {
        return this.name;
    }

    protected boolean isCorrupt(TransportLayerPacket pkt) {
        int receivedCheckSum = pkt.getChksum();
        int calculatedCheckSum = pkt.getAcknum() + pkt.getSeqnum();

        byte[] data = pkt.getData();
        for (byte a : data) {
            calculatedCheckSum += a;
        }

        if (receivedCheckSum + calculatedCheckSum == 65535) {
            //System.out.println(Integer.toBinaryString(receivedCheckSum + calculatedCheckSum));
            return false;
        } else {
            //System.out.println(Integer.toBinaryString(receivedCheckSum + calculatedCheckSum));
            return true;
        }
    }

    /*
     * Prints information to the console alongside the current packet's data, seqnum and acknum (for debugging).
     */
    public void log(TransportLayerPacket pkt, String msg){
        System.out.println("[Data: " + new String(pkt.getData()) + "; Seq: " + pkt.getSeqnum() + "; Ack: " + pkt.getAcknum() + "] " + msg);
    }

}
