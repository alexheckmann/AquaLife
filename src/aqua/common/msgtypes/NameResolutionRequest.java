package aqua.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class NameResolutionRequest implements Serializable {

    private final String requestId;
    private final String tankId;

    public NameResolutionRequest(String requestId, String tankId) {

        this.requestId = requestId;
        this.tankId = tankId;
    }

    public String getRequestId() {

        return requestId;
    }

    public String getTankId() {

        return tankId;
    }

}
