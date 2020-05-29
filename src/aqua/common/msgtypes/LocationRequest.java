package aqua.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class LocationRequest implements Serializable {

    private final String fishId;

    public LocationRequest(String fishId) {

        this.fishId = fishId;
    }

    public String getFish() {

        return fishId;
    }

}
