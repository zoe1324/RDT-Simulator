import java.util.ArrayList;
import java.util.Arrays;

public class TransportLayerSender extends TransportLayer {

    private int currentSeqNum;  // sequence number of last sent packet
    private int currentAckNum;  // acknum of last sent packet
    private byte[] currentData; // data contained in last sent packet
    private int nextSeqNum;     // sequence number of next packet to be sent, also acts as the expected acknum
    private int lastAcknum;     // last valid acknum that has been received from the receiver
    private TransportLayerPacket packet; // most recently sent packet (REFERENCE, not VALUE)

    // false when packet has been sent but no ACK received
    // true when ACK has been received for packet, or nothing has been sent yet
    private boolean readyToSend = true;

    private ArrayList<byte[]> queue;

    public TransportLayerSender(String name, NetworkSimulator simulator) {
        super(name, simulator);
        this.currentSeqNum = 0;
        this.nextSeqNum = 0;
        this.lastAcknum = 0;
        queue = new ArrayList<>();
    }

    @Override
    public void init() {
        System.out.println("Transport layer with name: \"" + name + "\" has been initialised");
    }

    /*
     * Returns the next piece of data to be sent if the quu is populated, otherwise returns null
     */
    private byte[] takeFromQueue(){
        if(queue.size() > 0) {
            byte nextData[] = queue.get(0);
            queue.remove(0);
            return nextData;
        }
        return null;
    }

    /*
     * Sends data to the Network Layer. Called by rdt_send.
     */
    private void sendData(byte[] data){

        TransportLayerPacket sendPacket = new TransportLayerPacket(currentSeqNum, lastAcknum, data);
        this.nextSeqNum = (this.currentSeqNum + 1)%2;
        log(sendPacket, "Sending Packet from Sender.");
        this.simulator.sendToNetworkLayer(this, sendPacket);
        setCurrentPacket(currentSeqNum, lastAcknum, data);
        packet = sendPacket;

        readyToSend = false;

        simulator.startTimer(this, 100.0);
    }

    private TransportLayerPacket getCurrentPacket() {
        return new TransportLayerPacket(currentSeqNum, currentAckNum, currentData);
    }

    private void setCurrentPacket(int seq, int ack, byte[] data){
        currentSeqNum = seq;
        currentAckNum = ack;
        currentData = Arrays.copyOf(data, data.length);
    }

    /*
     * Will prepare and send a packet of data to the network layer if the sender is not in a "Stop and wait" state.
     * Creates a packet with the next appropriate seqnum
     */
    @Override
    public void rdt_send(byte[] data) {

        if (readyToSend) {
            if(queue.size() > 0) {
                queue.add(data);
                data = queue.get(0);
                queue.remove(0);
            }

            sendData(data);

        } else {
            System.out.println("────> New send request added to queue (" + new String(data) + ") - sender in Stop state.");
            queue.add(data);
        }

    }

    /*
     * Handles packets returned from the Receiver. Checks if the ACK received is what is expected ( "current seqnum +
     * number of bytes of data in the sent packet" i.e. nextSeqNum). If ACK is as expected, the seqnum for the next
     * packet is set and the "Stop and wait" state is suspended by setting readyToSend flag to "true".
     */
    @Override
    public void rdt_receive(TransportLayerPacket pkt) {

        log(pkt, "ACK Received.");

        if (isCorrupt(pkt)){

            log(pkt, "Received packet is corrupt.");
            simulator.stopTimer(this);
            simulator.startTimer(this, 100.0);
            this.simulator.sendToNetworkLayer(this, getCurrentPacket());
            log(getCurrentPacket(), "Retransmitting packet.");

        } else if ( pkt.getAcknum() == this.nextSeqNum ) {

            // ACK is for last transmitted packet
            log(pkt, "└── ACK is correct.\n");
            this.currentSeqNum = this.nextSeqNum;
            this.lastAcknum = pkt.getAcknum();
            readyToSend = true; // receiver got the packet successfully, so we can set readyToSend back to True
            simulator.stopTimer(this);

            // sends the next piece of data if the queue is populated
            byte[] queueNext = takeFromQueue();
            if (queueNext != null){
                sendData(queueNext); // bypass rdt_send() queue check which adds new request to queue to favour old ones
            }
        } else {
            log(pkt, "└── Invalid ACK: expected " + this.nextSeqNum + ".");
            simulator.stopTimer(this);
            simulator.startTimer(this, 100.0);
            this.simulator.sendToNetworkLayer(this, getCurrentPacket());
            log(getCurrentPacket(), "Retransmitting packet.");
        }


    }

    @Override
    public void timerInterrupt() {
        log(packet, "(Timer Interrupt! Retransmitting packet.)");
        simulator.sendToNetworkLayer(this, getCurrentPacket());
        simulator.startTimer(this, 100.0);
    }
}
