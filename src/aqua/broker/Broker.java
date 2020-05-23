package aqua.broker;

import aqua.broker.poison.PoisonPill;
import aqua.common.Direction;
import aqua.common.FishModel;
import aqua.common.Properties;
import aqua.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {

    private volatile boolean stopRequested;
    private final Endpoint endpoint;
    private final ClientCollection<InetSocketAddress> availableClients;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final int THREAD_POOL_SIZE = (int) (Runtime.getRuntime().availableProcessors() / 0.5);

    public Broker() {

        endpoint = new Endpoint(Properties.PORT);
        availableClients = new ClientCollection<>();
        stopRequested = false;

    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void setStopRequested(boolean stopRequested) {
        this.stopRequested = stopRequested;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public ClientCollection<InetSocketAddress> getAvailableClients() {
        return availableClients;
    }

    public void broker() {

        // using the executor framework to manage a thread pool of fixed size
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        Thread terminateServerThread = new Thread(() -> {

            JOptionPane.showMessageDialog(null, "Press OK button to terminate server.");
            setStopRequested(true);

        });

        terminateServerThread.start();

        while (!stopRequested) {

            Message message = endpoint.blockingReceive();
            executorService.execute(new BrokerTask(message));

        }

        executorService.shutdown();

    }


    private class BrokerTask implements Runnable {

        private final Message message;

        private BrokerTask(Message incomingMessage) {
            message = incomingMessage;
        }

        @Override
        public void run() {

            Serializable payload = message.getPayload();

            if (payload instanceof RegisterRequest) {

                register(message);

            } else if (payload instanceof DeregisterRequest) {

                deregister(message);

            } else if (payload instanceof HandoffRequest) {

                handoffFish(message);

            } else if (payload instanceof PoisonPill) {

                System.exit(0);

            }

        }

        public synchronized void register(Message message) {

            InetSocketAddress sender = message.getSender();
            int tankCount = availableClients.size() + 1;
            availableClients.add("tank" + tankCount, sender);

            // give token to first client
            if (availableClients.size() == 1) {

                endpoint.send(sender, new Token());

            }

            endpoint.send(sender, new RegisterResponse("tank" + tankCount));

            InetSocketAddress leftNeighbor = availableClients.getLeftNeighborOf(sender);
            InetSocketAddress rightNeighbor = availableClients.getRightNeighborOf(sender);

            endpoint.send(sender, new NeighborUpdate(leftNeighbor, rightNeighbor));
            endpoint.send(leftNeighbor, new NeighborUpdate(availableClients.getLeftNeighborOf(leftNeighbor), sender));
            endpoint.send(rightNeighbor, new NeighborUpdate(sender, availableClients.getRightNeighborOf(rightNeighbor)));


        }

        public synchronized void deregister(Message message) {

            InetSocketAddress sender = message.getSender();
            InetSocketAddress leftNeighbor = availableClients.getLeftNeighborOf(sender);
            InetSocketAddress rightNeighbor = availableClients.getRightNeighborOf(sender);

            endpoint.send(leftNeighbor, new NeighborUpdate(availableClients.getLeftNeighborOf(leftNeighbor), rightNeighbor));
            endpoint.send(rightNeighbor, new NeighborUpdate(leftNeighbor, availableClients.getRightNeighborOf(rightNeighbor)));
            availableClients.remove(availableClients.indexOf(sender));

        }

        /**
         * Hands off the fish referenced in the message to the correct neighbor depending on the swim direction of the fish.
         * @param message the message sent by a client; contains the address of the sender and the fish which will be handed off.
         */
        public synchronized void handoffFish(Message message) {

            Serializable payload = message.getPayload();
            InetSocketAddress sender = message.getSender();

            // making use of a read write lock to enable multithreaded execution
            lock.writeLock().lock();

            FishModel fish = ((HandoffRequest) payload).getFish();
            Direction exitDirection = fish.getDirection();

            InetSocketAddress handoffTarget;
            if (exitDirection == Direction.LEFT) {
                handoffTarget = availableClients.getLeftNeighborOf(availableClients.indexOf(sender));
            } else {
                handoffTarget = availableClients.getRightNeighborOf(availableClients.indexOf(sender));
            }

            endpoint.send(handoffTarget, payload);
            lock.writeLock().unlock();
        }

    }

    public static void main(String[] args) {

        Broker broker = new Broker();

        broker.broker();

    }

}
