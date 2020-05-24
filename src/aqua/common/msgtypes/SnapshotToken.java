package aqua.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class SnapshotToken implements Serializable {

    private String initiatorId;
    private int value;

    public SnapshotToken(String initiatorId, int value) {

        this.initiatorId = initiatorId;
        this.value = value;

    }

    public String getInitiatorId() {

        return initiatorId;

    }

    public void setInitiatorId(String initiatorId) {

        this.initiatorId = initiatorId;

    }

    public int getValue() {

        return value;

    }

    public void setValue(int value) {

        this.value = value;

    }

}
