public class Main {

    public static void main(String[] args) {
        NetworkSimulator sim = new NetworkSimulator(3, 0, 0.5, 10.0, false, 0);

        sim.setSender(new TransportLayerSender("Sender", sim));

        sim.setReceiver(new TransportLayerReceiver("Receiver", sim));

        sim.runSimulation();
    }

}
