package aqua.common;

/**
 * Contains all possible swimming directions used by {@code fish} objects
 */
public enum Direction {
    LEFT(-1), RIGHT(+1);

    private int vector;

    private Direction(int vector) {

        this.vector = vector;
    }

    public int getVector() {

        return vector;
    }

    public Direction reverse() {

        return this == LEFT ? RIGHT : LEFT;
    }

}
