// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.utils;

import static java.lang.String.format;

import com.amazonaws.fcj.exceptions.FcjServiceException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

public class Utils {
    public static final String QUOTE = "\"";

    private static final String ETAG_ALGORITHM = "MD5";

    /**
     * Our default cryptographic hash function.
     */
    private static final String DEFAULT_CRYPTOGRAPHIC_HASH = "SHA-256";

    /**
     * Concatenates all byte arrays in the list together and returns the result.
     *
     * @param buffers The list of byte arrays to concatenate together.
     * @return Byte array containing all byte arrays from the list.
     */
    public static byte[] concat(final List<byte[]> buffers) {
        Objects.requireNonNull(buffers, "The list of buffers to concatenate must not be null");
        int n = 0;
        for (final byte[] buf : buffers) {
            n += buf.length;
        }
        final byte[] all = new byte[n];
        int i = 0;
        for (final byte[] buf : buffers) {
            System.arraycopy(buf, 0, all, i, buf.length);
            i += buf.length;
        }
        return all;
    }

    /**
     * Writes all bytes from the source byte array at an offset in the destination byte array. This method is useful for
     * combining chunks of data
     *
     * @param src The source byte array to write bytes from.
     * @param dst The destination array to write bytes to.
     * @param dstOffset Offset in the destination array to write at.
     * @return Position of the last written byte in the destination array.
     */
    public static int writeTo(final byte[] src, final byte[] dst, final int dstOffset) {
        System.arraycopy(src, 0, dst, dstOffset, src.length);
        return dstOffset + src.length;
    }

    public static byte[] computeETagChecksum(final byte[] arr) {
        return getMessageDigestForETag().digest(arr);
    }

    public static MessageDigest getMessageDigestForETag() {
        return getMessageDigest(ETAG_ALGORITHM);
    }

    public static MessageDigest getMessageDigestForDefaultHash() {
        return getMessageDigest(DEFAULT_CRYPTOGRAPHIC_HASH);
    }

    private static MessageDigest getMessageDigest(final String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new FcjServiceException(format(
                    "The is no JCE provider installed that supports algorithm \"%s\"", algorithm), e);
        }
    }
}
