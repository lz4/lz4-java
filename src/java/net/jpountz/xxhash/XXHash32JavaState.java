package net.jpountz.xxhash;

import java.util.Arrays;

import static net.jpountz.xxhash.XXHashConstants.PRIME1;
import static net.jpountz.xxhash.XXHashConstants.PRIME2;

/**
 * Internal state for {@link StreamingXXHash32} java implementations. Meant to be serializable.
 */
public class XXHash32JavaState implements XXHash32State {
    final int seed;
    final byte[] memory;

    int memSize;
    int v1, v2, v3, v4;
    long totalLen;

    /** Creates a new "initial" state for the given seed */
    public XXHash32JavaState(int seed) {
        this.seed = seed;
        memory = new byte[16];
        reset();
    }

    /** Copy constructor. New state is completely detached from original */
    XXHash32JavaState(XXHash32JavaState toCopy) {
        this.seed = toCopy.seed;
        this.memory = Arrays.copyOf(toCopy.memory, toCopy.memory.length);

        // Mutable fields
        this.memSize = toCopy.memSize;
        this.totalLen = toCopy.totalLen;
        this.v1 = toCopy.v1;
        this.v2 = toCopy.v2;
        this.v3 = toCopy.v3;
        this.v4 = toCopy.v4;
    }

    void reset() {
        v1 = seed + PRIME1 + PRIME2;
        v2 = seed + PRIME2;
        v3 = seed + 0;
        v4 = seed - PRIME1;
        totalLen = 0;
        memSize = 0;
    }

}

