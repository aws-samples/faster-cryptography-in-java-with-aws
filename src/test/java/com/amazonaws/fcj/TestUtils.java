// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.springframework.util.unit.DataSize;

import reactor.core.publisher.Flux;

public class TestUtils {
    private static final ThreadLocal<SecureRandom> DRBG = ThreadLocal.withInitial(SecureRandom::new);
    private static final DataSize DEFAULT_CHUNK_SIZE = DataSize.ofKilobytes(64);

    private TestUtils() {
    }

    public static SecureRandom getThreadLocalDrbg() {
        return DRBG.get();
    }

    public static Flux<byte[]> getRandomFlux(final DataSize totalSize) {
        if (totalSize.isNegative()) {
            throw new IllegalArgumentException("Data size for random flux must not be negative");
        }
        final int chunkSizeBytes = Math.toIntExact(Math.min(DEFAULT_CHUNK_SIZE.toBytes(), totalSize.toBytes()));
        final AtomicLong byteCounter = new AtomicLong(totalSize.toBytes());
        final AtomicLong chunkCounter = getChunkCount(chunkSizeBytes, totalSize);
        return Flux.create(sink -> {
            sink.onRequest(n -> {
                for (int i = 0; i < n; i++) {
                    final long chunkNumber = chunkCounter.getAndDecrement();
                    if (chunkNumber <= 0) {
                        sink.complete();
                        break;
                    } else {
                        final long remainingBytes = byteCounter.getAndUpdate(curVal -> curVal - chunkSizeBytes);
                        final int byteCount = Math.toIntExact(Math.min(remainingBytes, chunkSizeBytes));
                        sink.next(getRandomBytes(byteCount));
                        if (chunkNumber == 1) {
                            sink.complete();
                            break;
                        }
                    }
                }
            });
        });
    }

    public static byte[] getRandomBytes(final int byteCount) {
        final byte[] randomBytes = new byte[byteCount];
        getThreadLocalDrbg().nextBytes(randomBytes);
        return randomBytes;
    }

    private static AtomicLong getChunkCount(final int chunkSizeBytes, final DataSize totalSize) {
        final double chunkCountFp = (double) totalSize.toBytes() / chunkSizeBytes;
        return new AtomicLong((long) Math.ceil(chunkCountFp));
    }

    public static String getEnvVar(final String varName, final String defaultVal) {
        return getEnvVar(varName, Function.identity(), defaultVal);
    }

    public static <T> T getEnvVar(final String varName, final Function<String, T> converter, final T defaultVal) {
        final String val = System.getenv(varName);
        if (val == null) {
            return defaultVal;
        }
        return converter.apply(val);
    }
}
