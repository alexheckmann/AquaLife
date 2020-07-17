package aqua.broker;

import aqua.broker.poison.PoisonPill;
import aqua.common.Direction;
import aqua.common.FishModel;
import aqua.common.Properties;
import aqua.common.msgtypes.*;
import aqua.common.security.SecureEndpoint;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {

    private static Broker instance;
    public static final int LEASE_DURATION = 2000;
    private static final int THREAD_POOL_SIZE = (int) (Runtime.getRuntime().availableProcessors() / 0.5);
    private final SecureEndpoint endpoint;
    private final ClientCollection<InetSocketAddress> availableClients;
    private final ReadWriteLock lock;
    private final Timer timer;
    private volatile boolean stopRequested;

    private Broker() {

        endpoint = new SecureEndpoint(Properties.PORT);
        availableClients = new ClientCollection<>();
        stopRequested = false;
        lock = new ReentrantReadWriteLock();
        timer = new Timer();

    }

    /**
     * @return the singleton instance of the Broker class
     */
    public static Broker getInstance() {

        if (instance == null) {
            instance = new Broker();
        }

        return instance;
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

        // commented out for convenience
        /*

        executorService.execute(() -> {

            JOptionPane.showMessageDialog(null, "Press OK button to terminate server.");
            setStopRequested(true);

        });

        */

        while (!stopRequested) {

            timer.schedule(new TimerTask() {


                @Override
                public void run() {

                    synchronized (availableClients) {
                        availableClients.checkLease();
                    }
                }

            }, LEASE_DURATION);
            Message message = endpoint.blockingReceive();
            executorService.execute(new BrokerTask(message));


        }

        executorService.shutdown();

    }

    public static void main(String[] args) {

        Broker broker = getInstance();

        broker.broker();

    }

    /**
     * A helper class used by the executor framework. Whenever the broker receives a new message,
     * a new {@code BrokerTask} gets passed to the executor service, enqueueing the task and eventually
     * handling the message.
     */
    private class BrokerTask implements Runnable {

        private final Message message;

        private BrokerTask(Message incomingMessage) {

            message = incomingMessage;
        }

        /**
         * Identifies the type of the received message and handles it appropriately.
         */
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

        /**
         * Handles the registration of the message's sender. The sender gets added to the list of available clients
         * if he isn't registered yet, otherwise the lease gets renewed. In the case of a newly registered client, the
         * client and its left and right neighbors get a {@code NeighborUpdate}
         * with the corresponding {@code InetSocketAddress} of their new neighbors,
         * also the client gets a {@code RegisterResponse} confirming the (soft-state)
         * registration for a given period of time.
         *
         * @param message The message received by the broker
         */
        public synchronized void register(Message message) {

            InetSocketAddress sender = message.getSender();
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            if (!availableClients.contains(sender)) {

                int tankCount = availableClients.size() + 1;
                availableClients.add("tank" + tankCount, sender, timestamp);

                // give token to first client
                if (availableClients.size() == 1) {

                    endpoint.send(sender, new Token());

                }

                endpoint.send(sender, new RegisterResponse("tank" + tankCount, LEASE_DURATION));

                InetSocketAddress leftNeighbor = availableClients.getLeftNeighborOf(sender);
                InetSocketAddress rightNeighbor = availableClients.getRightNeighborOf(sender);

                endpoint.send(sender, new NeighborUpdate(leftNeighbor, rightNeighbor));
                endpoint.send(leftNeighbor, new NeighborUpdate(availableClients.getLeftNeighborOf(leftNeighbor), sender));
                endpoint.send(rightNeighbor, new NeighborUpdate(sender, availableClients.getRightNeighborOf(rightNeighbor)));

            } else {

                availableClients.update(sender, timestamp);
            }
        }

        /**
         * Deregisters the client sending the message and informs the left and right neighbor
         * their new neighbors by sending a {@code NeighborUpdate}.
         *
         * @param message The message received by the broker
         */
        public synchronized void deregister(Message message) {

            InetSocketAddress sender = message.getSender();
            InetSocketAddress leftNeighbor = availableClients.getLeftNeighborOf(sender);
            InetSocketAddress rightNeighbor = availableClients.getRightNeighborOf(sender);

            endpoint.send(leftNeighbor, new NeighborUpdate(availableClients.getLeftNeighborOf(leftNeighbor), rightNeighbor));
            endpoint.send(rightNeighbor, new NeighborUpdate(leftNeighbor, availableClients.getRightNeighborOf(rightNeighbor)));
            availableClients.remove(availableClients.indexOf(sender));

        }

        /**
         * Hands off the fish referenced in the message to the correct neighbor
         * depending on the swim direction of the fish.
         *
         * @param message the message sent by a client; contains the address
         * of the sender and the fish which will be handed off.
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

}
