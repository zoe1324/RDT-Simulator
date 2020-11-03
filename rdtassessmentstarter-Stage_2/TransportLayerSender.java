import java.util.*;

public class TransportLayerSender extends TransportLayer {

    private int currentSeqNum;  // sequence number of last sent packet
    private int currentAckNum;  // acknum of last sent packet
    private byte[] currentData; // data contained in last sent packet
    private int nextSeqNum;     // sequence number of next packet to be sent, also acts as the expected acknum
    private TransportLayerPacket packet; // most recently sent packet (REFERENCE, not VALUE)
    private int windowSize;
    private int numSent; //number of packets sent - used to label packets for testing

    private int expectedAcknum; //expected acknum of the last packet sent
    private int sendBase;

    // false when packet has been sent but no ACK received
    // true when ACK has been received for packet, or nothing has been sent yet
    private boolean readyToSend = true;

    private ArrayList<byte[]> queue;
    private HashMap<Integer, TransportLayerPacket> sentPackets;

    public TransportLayerSender(String name, NetworkSimulator simulator) {
        super(name, simulator);
        this.currentSeqNum = 0;
        this.nextSeqNum = 0;
        this.expectedAcknum = 0;
        this.sendBase = 0;
        sentPackets = new HashMap<>();
        queue = new ArrayList<>();
        windowSize = 2;
        numSent = 0;
    }

    @Override
    public void init() {
        System.out.println("Transport layer with name: \"" + name + "\" has been initialised");
    }

    /*
     * Returns the next piece of data to be sent if the queue is populated, otherwise returns null
     */
    private byte[] takeFromQueue(){
        if(queue.size() > 0) {
            System.out.println("taking packet from queue");
            byte nextData[] = queue.get(0);
            queue.remove(0);
            return nextData;
        }
        return null;
    }

    /*
     * Creates a packet using the passed in data and sends it across the network layer
     */
    @Override
    public void rdt_send(byte[] data) {
        if (readyToSend) {
            if(queue.size() > 0) {
                queue.add(data);
                data = queue.get(0);
                queue.remove(0);
                System.out.println("Packet sent from queue");
            }
            sendData(data);
        } else {
            System.out.println("────> New send request added to queue (" + new String(data) + ") - sender in Stop state.");
            queue.add(data);
        }
    }

    /*
     * Sends data to the Network Layer. Called by rdt_send.
     */
    private void sendData(byte[] data){

        //if theres space in the window send the packet
        if(nextSeqNum < (sendBase + windowSize) ) {

            //reassigned data with packet number for testing
            numSent++; // only used for testing
            data = ("Packet " + numSent).getBytes(); // only used for testing

            TransportLayerPacket sendPacket = new TransportLayerPacket(nextSeqNum, expectedAcknum, data);

            log(sendPacket, "Sending Packet from Sender.");
            this.simulator.sendToNetworkLayer(this, sendPacket);
            setCurrentPacket(nextSeqNum, expectedAcknum, data);
            sentPackets.put(nextSeqNum, getCurrentPacket()); //add packet to window
            packet = sendPacket;


            if (sentPackets.size() >= windowSize) {
                readyToSend = false;
            }
            if (sendBase == nextSeqNum) {
                simulator.startTimer(this, 1000.0);
                System.out.println("└──START TIMER");
            }

            this.nextSeqNum++;
            this.expectedAcknum= nextSeqNum;

        }
    }

    private TransportLayerPacket getCurrentPacket() {
        return new TransportLayerPacket(currentSeqNum, currentAckNum, currentData);
    }

    private void setCurrentPacket(int seq, int ack, byte[] data){
        currentSeqNum = seq;
        currentAckNum = ack;
        currentData = Arrays.copyOf(data, data.length);
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }



    /* Handles ACK packets returned from the Receiver. If the received ACK is not corrupt and is for a packet in the
    * window. The window is moved up to the packet after the ACKed packet.
     * */
    @Override
    public void rdt_receive(TransportLayerPacket pkt) {
        log(pkt, "ACK Received.");

        if (isCorrupt(pkt)) {
            log(pkt, "Received packet is corrupt.");
        } else {

            if (sentPackets.containsKey(pkt.getSeqnum())) {


                log(pkt, "└── ACK is correct.\n");

                //this is not pkt.getAcknum()+1 as we made our ACKs be sequenceNumber + 1
                sendBase = pkt.getAcknum();

                // remove ACKed packets from the window(sentPackets)
                Iterator it = sentPackets.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry packet = (Map.Entry) it.next();
                    String packetKey = packet.getKey().toString();
                    int pktKey = Integer.parseInt(packetKey);
                    if (pktKey < pkt.getAcknum()) {
                        log(pkt, "└── Removing: " + it.toString());
                        log(pkt, "└── Removing packet with sequence number:" + pktKey);
                        it.remove();
                    }
                }


                readyToSend = true; // receiver got the packet successfully, so we can set readyToSend back to True


                if(sendBase == nextSeqNum && sentPackets.size()==0){
                    simulator.stopTimer(this);
                    System.out.println("└──STOP TIMER no packets in window ");

                }else{
                    simulator.stopTimer(this);
                    simulator.startTimer(this,1000);
                    System.out.println("└──START TIMER FOR MOVED UP WINDOW");
                }

                // sends the next piece of data if the queue is populated
                byte[] queueNext = takeFromQueue();
                if (queueNext != null) {
                    sendData(queueNext); // bypass rdt_send() queue check which adds new request to queue to favour old ones
                }

            } else {
                log(pkt, "└── Invalid ACK.");
            }


        }
    }

    @Override
    public void timerInterrupt() {
        log(packet, "(Timer Interrupt! Retransmitting all packets in window.)");

        for (HashMap.Entry<Integer, TransportLayerPacket> entry : sentPackets.entrySet()){
            simulator.sendToNetworkLayer(this, entry.getValue());
            System.out.println("resending packet " + (entry.getValue().getSeqnum()+1) );
        }

        simulator.startTimer(this, 1000.0);
        System.out.println("└──START TIMER");
    }
}
