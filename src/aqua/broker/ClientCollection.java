package aqua.broker;

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

    public ClientCollection<E> add(String id, E client) {

        clients.add(new Client(id, client));
        return this;
    }

    public ClientCollection<E> remove(int index) {

        clients.remove(index);
        return this;
    }

    public int indexOf(String id) {

        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).id.equals(id))
                return i;
        return -1;
    }

    public int indexOf(E client) {

        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).client.equals(client))
                return i;
        return -1;
    }

    /**
     * @param index index
     * @return the object at the specified position
     */
    public E getClient(int index) {

        return clients.get(index).client;
    }

    public E getClient(String id) {

        return clients.get(clients.indexOf(id)).client;
    }

    public int size() {

        return clients.size();
    }

    public E getLeftNeighborOf(int index) {

        return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
    }

    public E getLeftNeighborOf(E e) {

        return getLeftNeighborOf(indexOf(e));
    }

    public E getRightNeighborOf(int index) {

        return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
    }

    public E getRightNeighborOf(E e) {

        return getRightNeighborOf(indexOf(e));
    }

    private class Client {

        final String id;
        final E client;

        Client(String id, E client) {

            this.id = id;
            this.client = client;
        }

    }

}
