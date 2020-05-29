package aqua.client;

import aqua.common.Direction;
import aqua.common.FishModel;
import aqua.common.msgtypes.SnapshotMarker;
import aqua.common.msgtypes.SnapshotToken;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TankModel extends Observable implements Iterable<FishModel> {

    protected InetSocketAddress leftNeighbor;
    protected InetSocketAddress rightNeighbor;
    protected static final Random random = new Random();
    protected volatile String id;
    protected volatile boolean token;
    private static final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    protected volatile int fishCounter = 0;
    public boolean waitingForIdle = true;
    private volatile int localState = 0;
    protected final Timer timer;
    protected final Set<FishModel> fishies;
    protected final ClientCommunicator.ClientForwarder forwarder;
    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    private int globalState = 0;
    public boolean showDialog;
    public boolean initiator;
    protected RecordingMode recordingMode;

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {

        fishies = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.forwarder = forwarder;
        timer = new Timer();
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
        //newFish(WIDTH - FishModel.getXSize(), random.nextInt(HEIGHT - FishModel.getYSize()));
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
    synchronized void receiveSnapshotMarker(InetSocketAddress sender, SnapshotMarker snapshotMarker) {

        // if a snapshot hasn't been initiated yet
        if (recordingMode == RecordingMode.IDLE) {

            localState += fishies.size();

            if (!leftNeighbor.equals(rightNeighbor)) {

                if (sender.equals(leftNeighbor)) {
                    recordingMode = RecordingMode.RIGHT;
                } else {
                    recordingMode = RecordingMode.LEFT;
                }

            } else {
                recordingMode = RecordingMode.BOTH;
            }

            if (leftNeighbor.equals(rightNeighbor)) {
                forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
            } else {
                forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
                forwarder.sendSnapshotMarker(rightNeighbor, snapshotMarker);
            }
        } else {
            if (!leftNeighbor.equals(rightNeighbor)) {

                if (sender.equals(leftNeighbor)) {

                    switch (recordingMode) {
                        case BOTH:
                            recordingMode = RecordingMode.RIGHT;
                            break;
                        case LEFT:
                            recordingMode = RecordingMode.IDLE;
                            break;
                    }

                } else {

                    switch (recordingMode) {
                        case BOTH:
                            recordingMode = RecordingMode.LEFT;
                            break;
                        case RIGHT:
                            recordingMode = RecordingMode.IDLE;
                            break;
                    }

                }

            } else {
                recordingMode = RecordingMode.IDLE;
            }

            if (initiator && recordingMode == RecordingMode.IDLE) {
                forwarder.sendSnapshotToken(leftNeighbor, new SnapshotToken(this.id, localState));
            }
        }

    }

    /**
     * Handles the token when received by the clientReciever.
     *
     * @param snapshotToken the token gathering local snapshots from each client to create a global snapshot
     */
    synchronized void handleSnapshotToken(SnapshotToken snapshotToken) {

        waitingForIdle = true;

        singleThreadExecutor.execute(() -> {

            while (waitingForIdle) {
                if (recordingMode == RecordingMode.IDLE) {

                    snapshotToken.addValue(localState);
                    forwarder.sendSnapshotToken(leftNeighbor, snapshotToken);
                    waitingForIdle = false;

                }
            }
        });

        if (initiator) {

            initiator = false;
            showDialog = true;
            globalState = snapshotToken.getValue();

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

                    if (recordingMode != RecordingMode.IDLE) {
                        localState--;
                    }

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

            if (fish.disappears()) {

                it.remove();
            }
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

        singleThreadExecutor.shutdown();
        forwarder.deregister(id);
    }

    /**
     * Initiates a global snapshot of the system
     */
    public synchronized void initiateSnapshot() {

        if (recordingMode == RecordingMode.IDLE) {
            localState = fishies.size();
            recordingMode = RecordingMode.BOTH;
            initiator = true;
            forwarder.sendSnapshotMarker(leftNeighbor, new SnapshotMarker(this.id));
            forwarder.sendSnapshotMarker(rightNeighbor, new SnapshotMarker(this.id));
        }

    }

}