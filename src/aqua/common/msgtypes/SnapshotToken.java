package aqua.common.msgtypes;

import aqua.client.SnapshotMode;

import java.io.Serializable;

@SuppressWarnings("serial")
public class SnapshotToken implements Serializable {

    private int fishSum;

    public SnapshotToken(){
        fishSum = 0;
    }

    public int getFishSum() {
        return fishSum;
    }

    public void setFishSum(int fishSum) {
        this.fishSum = fishSum;
    }
}
