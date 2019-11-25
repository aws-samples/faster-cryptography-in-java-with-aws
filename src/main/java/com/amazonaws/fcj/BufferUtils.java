// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Collection of utility methods to work around a JDK 9+ bug that causes JDK 9 and above to generate byte code
 * incompatible with JDK 8 even if JDK 9 is configured to produce byte code compatible with JDK 8.
 */
public class BufferUtils {
    /**
     * Equivalent to calling {@link ByteBuffer#flip()} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer flip(final ByteBuffer buff) {
        ((Buffer) buff).flip();
        return buff;
    }

    /**
     * Equivalent to calling {@link ByteBuffer#rewind()} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer rewind(final ByteBuffer buff) {
        ((Buffer) buff).rewind();
        return buff;
    }

    /**
     * Equivalent to calling {@link ByteBuffer#mark()} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer mark(final ByteBuffer buff) {
        ((Buffer) buff).mark();
        return buff;
    }

    /**
     * Equivalent to calling {@link ByteBuffer#reset()} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer reset(final ByteBuffer buff) {
        ((Buffer) buff).reset();
        return buff;
    }

    /**
     * Equivalent to calling {@link ByteBuffer#clear()} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer clear(final ByteBuffer buff) {
        ((Buffer) buff).clear();
        return buff;
    }

    /**
     * Equivalent to calling {@link ByteBuffer#position(int)} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer position(final ByteBuffer buff, final int newPosition) {
        ((Buffer) buff).position(newPosition);
        return buff;
    }

    /**
     * Equivalent to calling {@link ByteBuffer#limit(int)} but in a manner which is
     * safe when compiled on Java 9 or newer but used on Java 8 or older.
     */
    public static ByteBuffer limit(final ByteBuffer buff, final int newLimit) {
        ((Buffer) buff).limit(newLimit);
        return buff;
    }
}
