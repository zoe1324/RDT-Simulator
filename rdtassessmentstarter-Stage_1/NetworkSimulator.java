import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Simulate a network with variable reliability.
 *
 */
public class NetworkSimulator {

    private int numMessages;
    private double lossProb;
    private double corruptProb;
    private double lambda;
    private int debugLevel;
    public static final Random rng;
    private TransportLayer sender;
    private TransportLayer receiver;
    private PriorityQueue<Event> eventQueue;
    double simulationTime;
    boolean bidirectional;
    int numLost;
    int numCorrupt;
    int messagesSent;

    /**
     * Create a new instance of the NetworkSimulator class.  This instance will
     * send numMessages unique messages from a simulated layer 5 process with
     * probability of loss or corruption of those messages (at the network layer)
     * as defined by lossProb and corruptProb with a frequency based on lambda.
     *
     * The simulation is bi-directional (messages from layer 5 arrive at sender
     * and receiver) if bidirectional is true.
     *
     * Additional debugging messages will be produced if debugLevel is set > 0,
     * with increasing verbosity up to 3.
     *
     * @param numMessages the number of messages to send during the simulation
     * @param lossProb the probability of packet loss at the network layer
     * expressed as a decimal value between 0 and 1.
     * @param corruptProb the probability of packet corruption at the network
     * layer.  Packets will be corrupted in either sequence number, ack number
     * payload, but never more than 1 field will be corrupted in a given event.
     * @param lambda the rate (partial) at which new messages arrive from the
     * application layer within the simulated environment.
     * @param bidirectional if true application layer messages will arrive at
     * either transport layer endpoint with roughly a 50% probability.
     * @param debugLevel verbosity of output from the simulator.
     */
    public NetworkSimulator(int numMessages, double lossProb, double corruptProb, double lambda, boolean bidirectional, int debugLevel) {
        this.numMessages = numMessages;
        this.lossProb = lossProb;
        this.corruptProb = corruptProb;
        this.lambda = lambda;
        this.bidirectional = bidirectional;
        this.debugLevel = debugLevel;

        this.eventQueue = new PriorityQueue<Event>();

        // initialize event counters
        numLost = 0;
        numCorrupt = 0;
    }

    /**
     * Attach a TransportLayer instance to this simulation as the designated
     * "sender".  The sender designation is only relevant if bidirectional is
     * false.  In which case all layer 5 events will occur at the object passed
     * as an argument to this method.
     *
     * @param sender the "sender" side of the simulation.
     */
    public void setSender(TransportLayer sender) {
        this.sender = sender;
    }

    /**
     * Attach a TransportLayer instance to this simulation as the designated
     * "receiver".  This designation is only relevant if the bidirectional
     * option was false at instantiation.  In that case the "receiver" object
     * will only encounter layer 3 events (data from the network).
     * @param receiver
     */
    public void setReceiver(TransportLayer receiver) {
        this.receiver = receiver;
    }

    /**
     * Called once per instance to begin the simulation process.
     */
    public void runSimulation() {

        if (sender == null || receiver == null) {
            throw new IllegalStateException("sim run without sender or receiver.");
        }

        messagesSent = 1;
        simulationTime = 0.0;

        sender.init();
        receiver.init();

        //add event to event queue
        generateNextArrival();

        Event evt;
        while (eventQueue.size() > 0) {

            if (debugLevel > 2) {
                printEventQueue();
            }

            evt = eventQueue.poll();
            simulationTime = evt.getEvTime();        /* update time to next event time */

            if (evt.getEvType() == EventType.FROM_LAYER5) {
                if (messagesSent < numMessages) {
                    generateNextArrival();   /* set up future arrival */
                }
                byte[] msg = randomLetters();
                messagesSent++;
                if (evt.getEvEntity().equals(sender)) {
                    sender.rdt_send(msg);
                } else {
                    receiver.rdt_send(msg);
                }
            } else if (evt.getEvType() == EventType.FROM_LAYER3) {
                if (evt.getEvEntity().equals(sender)) {  /* deliver packet by calling */
                    sender.rdt_receive(evt.getPkt());  /* appropriate entity */
                } else {
                    receiver.rdt_receive(evt.getPkt());
                }
            } else if (evt.getEvType() == EventType.TIMER_INTERRUPT) {
                if (evt.getEvEntity().equals(sender)) {
                    sender.timerInterrupt();
                } else {
                    receiver.timerInterrupt();
                }
            } else {
                System.out.println("INTERNAL PANIC: unknown event type \n");
                System.exit(1);
            }
        }
    }

    /**
     * A utility method for TransportLayer objects to stop a timer event (timeout)
     * previously scheduled with startTimer(TransportLayer, double).  If no
     * timer event was previously scheduled for 't' then this method will only
     * print a warning.
     * @param t the TransportLayer instance that previously called startTimer(...)
     */
    public void stopTimer(TransportLayer t) {
        boolean removed = false;
        if (debugLevel > 2) {
            System.out.format("        (%.2f) NetworkSimulator: STOP TIMER.\n", simulationTime);
        }
        Iterator<Event> i = eventQueue.iterator();
        while(i.hasNext()) {
            Event e = i.next();
            if (e.getEvType() == EventType.TIMER_INTERRUPT && e.getEvEntity().equals(t)) {
                i.remove();
                removed = true;
            }
        }

        if (!removed) {
            System.out.println("Warning: unable to cancel timer for " + t.getName() + " as it doesn't seem to exist.");
        }
    }

    /**
     * A utility method for TransportLayer objects to start a timer event.  The
     * timeout event will occur at the current simulation time + the value of
     * the increment argument.  When this event is processed from the event queue
     * the result is a call to t.timerInterrupt()
     * @param t the TransportLayer instance to notify on expiration of this timer
     * @param increment the amount of time into the future at which this timer
     * should trigger.  A call to startTimer(t, 100.0) will cause a timeout at
     * 100.0 time units from the current simulation time.
     */
    public void startTimer(TransportLayer t, double increment) {
        if (debugLevel > 2) {
            System.out.format("        (%.2f) NetworkSimulator: START TIMER.\n", simulationTime);
        }
        for (Event e : eventQueue) {
            if (e.getEvType() == EventType.TIMER_INTERRUPT && e.getEvEntity().equals(t)) {
                System.out.println("Attempting to start timer for " + t.getName() + " when one already exists.");
                return;
            }
        }
        eventQueue.add(new Event(simulationTime + increment, EventType.TIMER_INTERRUPT, t));
    }

    /**
     * A utility method used by TransportLayer objects to inject data into
     * the network layer.  Data must be packetized and may be lost or corrupted
     * based on the probabilities given in lossProb and corruptProb when this
     * NetworkSimulation was instantiated.
     * @param source the TransportLayer sending pkt.  The source field is used
     * to determine where the data goes when it arrives at the other end of the
     * simulated network.
     * @param pkt the TransportLayerPacket to send via unreliable transport.
     */
    public void sendToNetworkLayer(TransportLayer source, TransportLayerPacket pkt) {
        // network loses packets with a probability of lossProb
        if (rng.nextDouble() < lossProb) {
            numLost++;
            if (debugLevel > 0) {
                System.out.format("        (%.2f) NetworkSimulator: %s losing packet: (%s)\n", simulationTime, source.getName(), pkt);

            }
            return;
        }
        TransportLayerPacket pktCopy = new TransportLayerPacket(pkt);
        if (rng.nextDouble() < corruptProb) {

            numCorrupt++;
            double x;
            if ((x = rng.nextDouble()) < .75) { // payload
                if (debugLevel > 0) {
                    System.out.format("        (%.2f) NetworkSimulator: %s corrupting packet payload: (%s)\n", simulationTime, source.getName(), pkt);
                }
                byte[] pktData = pktCopy.getData();
                for (int i = (rng.nextInt(4) + 1); i >= 0; i--) {
                    pktData[rng.nextInt(pktData.length)] = (byte) (rng.nextInt(26) + 97);
                }
            } else if (x < .875) { // seqnum
                if (debugLevel > 0) {
                    System.out.format("        (%.2f) NetworkSimulator: %s corrupting packet seqnum: (%s)\n", simulationTime, source.getName(), pkt);
                }
                pktCopy.setSeqnum(-99999); // should never be negative...
            } else { // acknum
                if (debugLevel > 0) {
                    System.out.format("        (%.2f) NetworkSimulator: %s corrupting packet acknum: (%s)\n", simulationTime, source.getName(), pkt);
                }
                pktCopy.setAcknum(-99999); // should never be negative...
            }
        }
        double lastTime = simulationTime; // where we're at right now...
        for (Event e : eventQueue) {
            if (e.getEvType() != EventType.TIMER_INTERRUPT && e.getEvTime() > lastTime) {
                lastTime = e.getEvTime();
            }
        }

        if (debugLevel > 1) {
            System.out.format("        (%.2f) NetworkSimulator.sendToNetworkLayer(%s, %s)\n", simulationTime, source.getName(), pktCopy.toString());
        }
        eventQueue.add(new Event((lastTime + (1 + 2 * rng.nextFloat())), EventType.FROM_LAYER3, (source.equals(sender) ? receiver : sender), pktCopy));
    }

    /**
     * A utility method used by TransportLayer objects to deliver data to the
     * simulated application layer.  This method just acts as a data sink.
     * @param source the TransportLayer instance sending the traffic.
     * @param data the data to be delivered.
     */
    public void sendToApplicationLayer(TransportLayer source, byte[] data) {
        if (debugLevel > 0) {
            System.out.format("        (%.2f) NetworkSimulator.sendToApplicationLayer(%s, %s)\n", simulationTime, source.getName(), new String(data));
        }
    }

    /**
     * A utility method that clients of the simulator may use to visualize the
     * event queue for debugging purposes.  This will happen automatically at
     * debugLevel > 2.
     */
    public void printEventQueue() {
        StringBuilder sb = new StringBuilder();
        Event[] events = new Event[0];
        events = eventQueue.toArray(events);
        Arrays.sort(events);
        sb.append("        Event Queue {\n");
        for (int i = 0; i < events.length; i++) {
            sb.append("                ");
            sb.append(events[i]);
            sb.append("\n");
        }
        sb.append("        }");
        System.out.println(sb.toString());
    }

    /**
     * A private class used to generate the simulation.  Students should not
     * modify or utilize this class.
     */
    private static class Event implements Comparable<Event> {

        private double evTime;
        private EventType evType;
        private TransportLayer evEntity;
        private TransportLayerPacket pkt;

        public Event(double evTime, EventType evType, TransportLayer evEntity) {
            this(evTime, evType, evEntity, null);
        }

        public Event(double evTime, EventType evType, TransportLayer evEntity, TransportLayerPacket pkt) {
            this.evTime = evTime;
            this.evType = evType;
            this.evEntity = evEntity;
            this.pkt = pkt;
        }

        /**
         * @return the evTime
         */
        public double getEvTime() {
            return evTime;
        }

        /**
         * @return the evType
         */
        public EventType getEvType() {
            return evType;
        }

        /**
         * @return the evEntity
         */
        public TransportLayer getEvEntity() {
            return evEntity;
        }

        /**
         * @return the pkt
         */
        public TransportLayerPacket getPkt() {
            return pkt;
        }

        public static String eventTypeToString(EventType t) {
            switch (t) {
                case TIMER_INTERRUPT:
                    return "TIMER_INTERRUPT";
                case FROM_LAYER5:
                    return "FROM_LAYER5";
                case FROM_LAYER3:
                    return "FROM_LAYER3";
                default:
                    return "";
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("EVENT(time=");
            sb.append(NumberFormat.getInstance().format(evTime));
            sb.append(", type=");
            sb.append(eventTypeToString(evType));
            sb.append(", pkt=");
            sb.append((pkt != null) ? pkt.toString() : "[no data])");
            return sb.toString();
        }

        public int compareTo(Event o) {
            if (this.evTime < o.evTime) {
                return -1;
            } else if (this.evTime > o.evTime) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public static byte[] randomLetters() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < 20; i++) {
            bos.write(rng.nextInt(26) + 97);
        }
        return bos.toByteArray();
    }

    /**
     * private utility method used in running the simulation.
     */
    private void generateNextArrival() {
        if (debugLevel > 2) {
            System.out.format("        (%.2f) NetworkSimulator: generateNextArrival()\n", simulationTime);
        }
        double x = lambda * rng.nextDouble() * 2;
        Event evt = new Event(simulationTime + x, EventType.FROM_LAYER5, (bidirectional && (rng.nextDouble() > 0.5)) ? receiver : sender);
        eventQueue.add(evt);
    }

    /**
     * Static initializer for the random number generator.
     */
    static {
        rng = new Random();
    }

    /**
     * The types of events that appear in the queue.
     */
    private enum EventType {

        TIMER_INTERRUPT, FROM_LAYER5, FROM_LAYER3
    };
}
