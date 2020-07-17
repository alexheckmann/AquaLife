package aqua.common.msgtypes;

import java.io.Serializable;
import java.security.Key;

public class KeyExchangeMessage implements Serializable {

    private Key key;

    public KeyExchangeMessage(Key key) {
        this.key = key;
    }

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }
}
