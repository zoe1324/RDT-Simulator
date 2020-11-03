import java.util.ArrayList;

public class TransportLayerReceiver extends TransportLayer {

    public TransportLayerPacket packet; // most recently received inorder uncorrupted packet
    private int expectedSeqnum; //next sequence number expected to be received

    private boolean readyToProcess;

    private ArrayList<TransportLayerPacket> queue;

    public TransportLayerReceiver(String name, NetworkSimulator simulator) {
        super(name, simulator);
        queue = new ArrayList<>();
        readyToProcess = true;
        expectedSeqnum = 0;

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

        if (readyToProcess){

                readyToProcess = false;
                log(pkt, "Packet Received by Receiver.");

                //if packet is not corrupt
                if(!isCorrupt(pkt)) {

                    //if the received packet is the expected packet
                    if (pkt.getSeqnum() == this.expectedSeqnum) {
                        //extract and deliver data
                        byte[] receiveData = pkt.getData();
                        simulator.sendToApplicationLayer(this, receiveData); //send data to application layer


                        // Send back ACK for received seqnum
                        TransportLayerPacket returnPacket = new TransportLayerPacket(pkt);
                        returnPacket.incrementAcknum();
                        returnPacket.generateChksum();
                        log(returnPacket, "└── Packet O.K. Returning packet to sender with ACK set.");
                        simulator.sendToNetworkLayer(this, returnPacket);


                        expectedSeqnum++;

                        //only update packet held in receiver if it is valid
                        packet = returnPacket;

                    }else {
                        //resend the latest uncorrupted inorder packet
                        if(packet != null){
                            log(pkt, "└── Packet is out of order. Retransmitting ACK for last inorder packet.");
                            simulator.sendToNetworkLayer(this, this.packet);
                        }else{
                            log(pkt,"└──Packet is out of order. No packet to retransmit.");

                        }
                    }
                }else{
                    if(packet != null){
                        log(pkt,"└──Packet is corrupted. Retransmitting ACK for last inorder packet.");
                        simulator.sendToNetworkLayer(this, this.packet);
                    }else{
                        log(pkt,"└──Packet is corrupted. No packet to retransmit.");
                    }
                }

                readyToProcess = true;

                if (queue.size() > 0){
                    TransportLayerPacket next = queue.get(0);
                    queue.remove(0);
                    rdt_receive(next);
                }

            } else {
                queue.add(pkt);
        }


    }

    @Override
    public void timerInterrupt() {
    }
}