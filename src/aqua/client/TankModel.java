package aqua.client;

import aqua.common.Direction;
import aqua.common.FishModel;
import aqua.common.msgtypes.SnapshotToken;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class TankModel extends Observable implements Iterable<FishModel> {

    protected InetSocketAddress leftNeighbor;
    protected InetSocketAddress rightNeighbor;
    protected static final Random random = new Random();
    protected volatile String id;
    protected volatile boolean token;
    private static final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    protected volatile int fishCounter = 0;
    private RecordingMode recordingMode;
    private volatile int localState = 0;
    protected final Timer timer;
    protected final Set<FishModel> fishies;
    protected final ClientCommunicator.ClientForwarder forwarder;
    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    private int globalState = 0;
    private CountDownLatch latch;

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {

        fishies = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.forwarder = forwarder;
        timer = new Timer();
        latch = new CountDownLatch(1);
        token = false;
        recordingMode = RecordingMode.IDLE;
    }

    public int getGlobalState() {

        return globalState;
    }

    /**
     * @param id the client's ID
     */
    synchronized void onRegistration(String id) {

        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), random.nextInt(HEIGHT - FishModel.getYSize()));
    }

    /**
     * Spawns a new fish, if the maximum limit on fishes in a tank isn't exceeded.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public synchronized void newFish(int x, int y) {

        if (fishies.size() < MAX_FISHIES) {
            x = Math.min(x, WIDTH - FishModel.getXSize() - 1);
            y = Math.min(y, HEIGHT - FishModel.getYSize());

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    random.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {

        if (fish.getDirection() == Direction.LEFT) {

            if (recordingMode == RecordingMode.BOTH || recordingMode == RecordingMode.RIGHT) {
                localState++;
            }

        } else {

            if (recordingMode == RecordingMode.BOTH || recordingMode == RecordingMode.LEFT) {
                localState++;
            }

        }

        fish.setToStart();
        fishies.add(fish);
    }

    /**
     * Saves the InetSocketAddress of the left and right neighbor in the ring
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

    /**
     *
     */
    synchronized void receiveSnapshotMarker(InetSocketAddress sender) {

        // if a snapshot hasn't been initiated yet
        if (recordingMode == RecordingMode.IDLE) {

            localState += fishies.size();

            if (sender.equals(leftNeighbor)) {
                recordingMode = RecordingMode.RIGHT;
            } else {
                recordingMode = RecordingMode.LEFT;
            }

            forwarder.sendSnapshotMarker(leftNeighbor);
            forwarder.sendSnapshotMarker(rightNeighbor);

        } else if (recordingMode == RecordingMode.BOTH) {

            if (sender.equals(leftNeighbor)) {
                recordingMode = RecordingMode.RIGHT;
            } else {
                recordingMode = RecordingMode.LEFT;
            }

        } else {

            // if one channel has already finished recording and
            // the other channel recording gets a message by the corresponding neighbor
            if (recordingMode == RecordingMode.LEFT && sender.equals(leftNeighbor) ||
                    recordingMode == RecordingMode.RIGHT && sender.equals(rightNeighbor)) {

                recordingMode = RecordingMode.IDLE;

                // decreases the count of the latch, releasing the thread waiting for the local snapshot to be done
                latch.countDown();

            }
        }

    }

    /**
     * Handles the token when received by the clientReciever. If the sender has the same ID as the client, the creation of
     * the global snapshot is finished and gets saved. Otherwise, a new thread gets started,
     * waiting for the local snapshot to be done using the {@code CountDownLatch} API. When the local snapshot is done,
     * the {@code localState} gets added to the current value of the token and gets sent to the client's left neighbor.
     *
     * @param snapshotToken the token gathering local snapshots from each client to create a global snapshot
     */
    synchronized void handleSnapshotToken(SnapshotToken snapshotToken) {

        if (snapshotToken.getInitiatorId().equals(this.id)) {

            globalState = snapshotToken.getValue();

        } else {

            singleThreadExecutor.execute(() -> {

                try {

                    // todo replace latch mechanism with empty while loop
                    boolean localSnapshotDone = latch.await(500, TimeUnit.MILLISECONDS);

                    if (localSnapshotDone) {

                        snapshotToken.setValue(snapshotToken.getValue() + localState);
                        forwarder.sendSnapshotToken(leftNeighbor, snapshotToken);

                    }

                } catch (InterruptedException consumed) {

                    // allow thread to terminate

                } finally {

                    resetCountDownLatch();

                }

            });
        }

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
     * Updates the position of the fishes and eventually hands them off, when they hit an edge.
     * A fish may only be handed off if the client currently holds the token; otherwise the fish cannot be handed off and
     * will reverse and swim in the other direction.
     */
    private synchronized void updateFishies() {

        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()) {

                if (hasToken()) {

                    Direction direction = fish.getDirection();

                    if (direction == Direction.LEFT) {

                        forwarder.handOff(fish, leftNeighbor);

                    } else if (direction == Direction.RIGHT) {

                        forwarder.handOff(fish, rightNeighbor);

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
     * Sends a message to deregister the executing client.
     */
    public synchronized void finish() {

        forwarder.deregister(id);
    }

    /**
     * Initiates a global snapshot of the system
     */
    public synchronized void initiateSnapshot() {

        try {

            localState += fishies.size();
            recordingMode = RecordingMode.BOTH;
            forwarder.sendSnapshotMarker(leftNeighbor);
            forwarder.sendSnapshotMarker(rightNeighbor);

            // todo replace latch mechanism with empty while loop
            boolean localSnapshotDone = latch.await(500, TimeUnit.MILLISECONDS);

            if (localSnapshotDone) {

                forwarder.sendSnapshotToken(leftNeighbor, new SnapshotToken(id, localState));

            }

        } catch (InterruptedException consumed) {

            // allow method to terminate

        } finally {

            resetCountDownLatch();

        }

    }

    /**
     * Creates a new CountDownLatch as they may not be reused after the count is zero
     */
    private void resetCountDownLatch() {

        if (latch.getCount() == 0) {

            latch = new CountDownLatch(1);

        }
    }

}