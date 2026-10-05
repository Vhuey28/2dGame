package world;

/**
 * A deterministic random source with a serializable long seed.
 * Simulation classes use this instead of Math.random() so that
 * bugs and scenarios can be reproduced from a saved seed.
 */
public final class SeededRandom {
    private final java.util.Random random;

    public SeededRandom(long seed) {
        this.random = new java.util.Random(seed);
    }

    public long getSeed() {
        return random.nextLong();
    }

    /** Set the seed after creation (e.g., after deserialization). */
    public void setSeed(long seed) {
        random.setSeed(seed);
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public int nextInt() {
        return random.nextInt();
    }

    public long nextLong() {
        return random.nextLong();
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public float nextFloat() {
        return random.nextFloat();
    }

    public int nextInt(int origin, int bound) {
        return origin + random.nextInt(bound - origin);
    }
}