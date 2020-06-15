package aqua.broker;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or
 * externally synchronized.
 */

public class ClientCollection<E> {

    private final List<Client> clients;

    public ClientCollection() {

        clients = new ArrayList<>();
    }

    public ClientCollection<E> add(String id, E e, Timestamp timestamp) {

        clients.add(new Client(id, e, timestamp));
        return this;
    }

    public ClientCollection<E> update(E e, Timestamp timestamp) {

        clients.get(indexOf(e)).setTimestamp(timestamp);
        return this;
    }

    public ClientCollection<E> remove(int index) {

        clients.remove(index);
        return this;
    }

    public boolean contains(E e) {

        return clients.contains(e);
    }

    public int indexOf(String id) {

        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).id.equals(id))
                return i;
        return -1;
    }

    public int indexOf(E client) {

        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).address.equals(client))
                return i;
        return -1;
    }

    /**
     * @param index index
     * @return the object at the specified position
     */
    public E getClient(int index) {

        return clients.get(index).address;
    }

    public E getClient(String id) {

        return clients.get(clients.indexOf(id)).address;
    }

    public int size() {

        return clients.size();
    }

    public E getLeftNeighborOf(int index) {

        return index == 0 ? clients.get(clients.size() - 1).address : clients.get(index - 1).address;
    }

    public E getLeftNeighborOf(E e) {

        return getLeftNeighborOf(indexOf(e));
    }

    public E getRightNeighborOf(int index) {

        return index < clients.size() - 1 ? clients.get(index + 1).address : clients.get(0).address;
    }

    public E getRightNeighborOf(E e) {

        return getRightNeighborOf(indexOf(e));
    }

    public void checkLease() {

        clients.removeIf(client -> {

            Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
            long timeDifference = currentTimestamp.getTime() - client.timestamp.getTime();

            return timeDifference > Broker.LEASE_DURATION;
        });
    }

    private class Client {

        final String id;
        final E address;
        Timestamp timestamp;

        Client(String id, E address, Timestamp timestamp) {

            this.id = id;
            this.address = address;
            this.timestamp = timestamp;
        }

        public void setTimestamp(Timestamp timestamp) {

            this.timestamp = timestamp;
        }

    }

}
