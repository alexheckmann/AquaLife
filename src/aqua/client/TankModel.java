package aqua.client;

import aqua.common.Direction;
import aqua.common.FishModel;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TankModel extends Observable implements Iterable<FishModel> {

    protected InetSocketAddress leftNeighbor;
    protected InetSocketAddress rightNeighbor;
    protected int fishCounter = 0;
    protected volatile String id;
    protected volatile boolean token;
    private SnapshotMode snapshotMode;
    private int snapshot = 0;
    protected final Timer timer;
    protected final Set<FishModel> fishies;
    protected final ClientCommunicator.ClientForwarder forwarder;
    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        fishies = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.forwarder = forwarder;
        timer = new Timer();
        token = false;
        snapshotMode = SnapshotMode.IDLE;
    }

    /**
     *
     * @param id
     */
    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    /**
     * Spawns a new fish, if the maximum limit on fishes in a tank isn't exceeded.
     * @param x x coordinate
     * @param y y coordinate
     */
    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = Math.min(x, WIDTH - FishModel.getXSize() - 1);
            y = Math.min(y, HEIGHT - FishModel.getYSize());

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
    }

    /**
     *
     * @param leftNeighbor InetSocketAddress of the left neighbor in the ring
     * @param rightNeighbor InetSocketAddress of the right neighbor in the ring
     */
    synchronized void receiveNeighbor(InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {

        this.leftNeighbor = leftNeighbor;
        this.rightNeighbor = rightNeighbor;

    }

    /**
     *
     */
    synchronized void receiveToken() {

        token = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                token = false;
                forwarder.sendToken(leftNeighbor);
            }
        }, 2000);

    }

    public boolean hasToken() {
        return token;
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    /**
     *
     */
    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()) {

                if (hasToken()) {

                    Direction direction = fish.getDirection();

                    switch (direction) {
                        case LEFT:
                            forwarder.handOff(fish, leftNeighbor);
                            break;
                        case RIGHT:
                            forwarder.handOff(fish, rightNeighbor);
                            break;
                    }
                } else {

                    fish.reverse();

                }

            }

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {

        updateFishies();
        setChanged();
        notifyObservers();

    }

    protected void run() {
        forwarder.register();

        try {

            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }

        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    /**
     * Tells the broker to deregister the executing client.
     */
    public synchronized void finish() {
        forwarder.deregister(id);
    }

    protected synchronized void initiateSnapshot(SnapshotMode mode) {

        snapshotMode = mode;
        snapshot += fishCounter;

    }

}