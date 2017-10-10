package net.jpountz.xxhash;

import java.util.Arrays;

import static net.jpountz.xxhash.XXHashConstants.PRIME64_1;
import static net.jpountz.xxhash.XXHashConstants.PRIME64_2;

/**
 * Internal state for {@link StreamingXXHash64} java implementations. Meant to be serializable.
 */
public class XXHash64JavaState implements XXHash64State {
    final long seed;
    final byte[] memory;

    int memSize;
    long v1, v2, v3, v4;
    long totalLen;

    /** Creates a new "initial" state for the given seed */
    public XXHash64JavaState(long seed) {
        this.seed = seed;
        memory = new byte[32];
        reset();
    }

    /** Copy constructor. New state is completely detached from original */
    XXHash64JavaState(XXHash64JavaState toCopy) {
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
        v1 = seed + PRIME64_1 + PRIME64_2;
        v2 = seed + PRIME64_2;
        v3 = seed + 0;
        v4 = seed - PRIME64_1;
        totalLen = 0;
        memSize = 0;
    }

}
