public class TransportLayerReceiver extends TransportLayer {

    public TransportLayerPacket packet; // most recently received packet
    private int lastAcknum;     // last acknum sent
    private int lastSeqnum;     // last seqnum received
    private int expectedSeqNum;
    public TransportLayerReceiver(String name, NetworkSimulator simulator) {
        super(name, simulator);
        expectedSeqNum = 0;
    }

    @Override
    public void init() {
        System.out.println("Transport layer with name: \"" + name + "\" has been initialised");
    }



    @Override
    public void rdt_send(byte[] data) {
    }

    /*
     * Sends received messages to Application layer and sends ACK back to Sender
     */
    @Override
    public void rdt_receive(TransportLayerPacket pkt) {

        // Only process if packet has not already been received
        if ( pkt.getSeqnum() == expectedSeqNum ) {

            packet = pkt;


            // Handle incoming data from network layer
            byte[] receiveData = pkt.getData();
            log(pkt, "Packet Received by Receiver.");

            if (!isCorrupt(pkt)) {

                lastSeqnum = pkt.getSeqnum(); // record successful receipt of packet

                simulator.sendToApplicationLayer(this, receiveData);

                // Send back ACK for received seqnum
                TransportLayerPacket returnPacket = new TransportLayerPacket(pkt.getSeqnum(), pkt.getAcknum(), "OK".getBytes());
                returnPacket.incrementAcknum();
                lastAcknum = returnPacket.getAcknum();
                expectedSeqNum = returnPacket.getAcknum();

                log(returnPacket, "└── Packet O.K. Returning packet to sender with ACK set.");
                returnPacket.generateChksum();
                simulator.sendToNetworkLayer(this, returnPacket);

            } else { //if packet is corrupt, send back ack for last received packet
                TransportLayerPacket returnPacket = new TransportLayerPacket(pkt);
                returnPacket.setAcknum(lastAcknum);
                log(returnPacket, "└── Packet is corrupt! Returning packet to sender with ACK for last received packet.");
                simulator.sendToNetworkLayer(this, returnPacket);
            }

        } else {
            TransportLayerPacket returnPacket = new TransportLayerPacket(pkt.getSeqnum(), lastAcknum, "OK".getBytes());
            returnPacket.setAcknum(lastAcknum);
            log(returnPacket, "└── Packet already received (or invalid seqnum). Returning ACK for last received packet.");
            simulator.sendToNetworkLayer(this, returnPacket);
        }

    }

    @Override
    public void timerInterrupt() {
        System.out.println("Time Interrupt!");
//        simulator.stopTimer(this);
        simulator.sendToNetworkLayer(this, packet);
        simulator.startTimer(this, 100.0);
    }
}