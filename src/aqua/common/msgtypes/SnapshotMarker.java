package aqua.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class SnapshotMarker implements Serializable {

    protected final String senderId;

    public SnapshotMarker(String senderId) {

        this.senderId = senderId;
    }

    public String getSenderId() {

        return senderId;
    }

}
