package aqua.common.security;

import aqua.common.msgtypes.DummyMessage;
import aqua.common.msgtypes.KeyExchangeMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.*;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

public class SecureEndpoint extends Endpoint {

    private static final String CRYPTO_ALGORITM = "RSA";
    private static final int KEY_SIZE = 4096;
    private final Endpoint endpoint;
    private Cipher decryptor;
    private Cipher encryptor;
    private KeyPair keyPair;
    private final Map<InetSocketAddress, Key> COMMUNICATION_PARTNERS;

    public SecureEndpoint(int port) {

        endpoint = new Endpoint(port);
        COMMUNICATION_PARTNERS = new HashMap<>();
        initializeEndpoint();
    }

    public SecureEndpoint() {

        endpoint = new Endpoint();
        COMMUNICATION_PARTNERS = new HashMap<>();
        initializeEndpoint();

    }

    private void initializeEndpoint() {

        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(CRYPTO_ALGORITM);
            keyPairGenerator.initialize(KEY_SIZE);
            keyPair = keyPairGenerator.generateKeyPair();
            decryptor = Cipher.getInstance(CRYPTO_ALGORITM);
            decryptor.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());


        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void send(InetSocketAddress address, Serializable payload) {

        if (!COMMUNICATION_PARTNERS.containsKey(address)) {

            endpoint.send(address, new KeyExchangeMessage(keyPair.getPublic()));
            Message message = endpoint.blockingReceive();

            if (message.getPayload() instanceof KeyExchangeMessage) {
                addNewCommunicationPartner(message);
                encrypt(address, payload);
            }

        } else encrypt(address, payload);
    }

    private void encrypt(InetSocketAddress address, Serializable payload) {

        try {

            encryptor = Cipher.getInstance(CRYPTO_ALGORITM);
            encryptor.init(Cipher.ENCRYPT_MODE, COMMUNICATION_PARTNERS.get(address));
            SealedObject s = new SealedObject(payload, encryptor);
            endpoint.send(address, s);

        } catch (IllegalBlockSizeException | IOException | NoSuchAlgorithmException
                | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    private Message decrypt(Message encryptedMessage) {

        if (encryptedMessage.getPayload() instanceof KeyExchangeMessage) {

            if (!COMMUNICATION_PARTNERS.containsKey(encryptedMessage.getSender())) {
                addNewCommunicationPartner(encryptedMessage);
            }

            endpoint.send(encryptedMessage.getSender(), new KeyExchangeMessage(keyPair.getPublic()));
            return new Message(new DummyMessage(), encryptedMessage.getSender());
        }

        SealedObject encryptedPayload = (SealedObject) encryptedMessage.getPayload();

        try {

            Serializable serializable = (Serializable) encryptedPayload.getObject(decryptor);

            return new Message(serializable, encryptedMessage.getSender());

        } catch (IllegalBlockSizeException | ClassNotFoundException | IOException | BadPaddingException e) {
            e.printStackTrace();
        }
        return new Message(null, null);
    }

    public void addNewCommunicationPartner(Message m) {

        COMMUNICATION_PARTNERS.put(m.getSender(), ((KeyExchangeMessage) m.getPayload()).getKey());
    }

    @Override
    public Message blockingReceive() {

        Message encryptedMessage = endpoint.blockingReceive();
        return decrypt(encryptedMessage);
    }

    @Override
    public Message nonBlockingReceive() {

        Message encryptedMessage = endpoint.nonBlockingReceive();
        return decrypt(encryptedMessage);
    }

}
