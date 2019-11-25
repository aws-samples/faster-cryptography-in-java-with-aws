// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import com.amazonaws.fcj.exceptions.InvalidIdException;
import com.amazonaws.fcj.utils.Utils;
import com.amazonaws.util.Base32;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FileMetadata {
    private static final byte[] ID_VERSION = new byte[]{1};
    private static final int BASE_ID_LEN_BYTES = 27;
    private static final int ID_CHECKSUM_LEN_BYTES = 4;
    private static final ThreadLocal<SecureRandom> RNG = ThreadLocal.withInitial(SecureRandom::new);

    private String id;
    private String contentType;
    private Long contentLength;
    private Instant uploadTimestamp;
    private String etag;

    public FileMetadata() {
    }

    public FileMetadata(final String id,
                        final String contentType,
                        final Long contentLength,
                        final Instant uploadTimestamp) {
        verifyIdIntegrity(id);
        this.id = id;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.uploadTimestamp = uploadTimestamp;
    }

    public static FileMetadata newFile(final String contentType, final Long contentLength) {
        return new FileMetadata(generateId(), contentType, contentLength, Instant.now());
    }

    /**
     * ID structure: version (1 byte), base ID (random, 27 bytes), checksum (4 bytes), 32 bytes in total
     */
    @SuppressWarnings("UnstableApiUsage")
    private static String generateId() {
        final byte[] idBase = new byte[BASE_ID_LEN_BYTES];
        RNG.get().nextBytes(idBase);
        final byte[] id = new byte[ID_VERSION.length + BASE_ID_LEN_BYTES + ID_CHECKSUM_LEN_BYTES];
        int idOffset = 0;
        idOffset = Utils.writeTo(ID_VERSION, id, idOffset);
        idOffset = Utils.writeTo(idBase, id, idOffset);
        final byte[] idChecksum = Hashing.murmur3_32().hashBytes(id, 0, idOffset).asBytes();
        Utils.writeTo(idChecksum, id, idOffset);
        return bytesToId(id);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void verifyIdIntegrity(final String id) {
        final byte[] wholeId = idToBytes(id);
        if (wholeId.length != ID_VERSION.length + BASE_ID_LEN_BYTES + ID_CHECKSUM_LEN_BYTES) {
            throw new InvalidIdException("Wrong ID length: " + wholeId.length);
        }
        if (wholeId[0] != ID_VERSION[0]) {
            throw new InvalidIdException("Wrong ID version: " + wholeId[0]);
        }
        final byte[] idComputedChecksum = Hashing.murmur3_32()
                .hashBytes(wholeId, 0, ID_VERSION.length + BASE_ID_LEN_BYTES)
                .asBytes();
        final int checksumStartsAt = ID_VERSION.length + BASE_ID_LEN_BYTES;
        final byte[] incomingIdChecksum = Arrays.copyOfRange(
                wholeId, checksumStartsAt, checksumStartsAt + ID_CHECKSUM_LEN_BYTES);
        if (!Arrays.equals(idComputedChecksum, incomingIdChecksum)) {
            throw new InvalidIdException(
                    "ID has incorrect checksum: " + Base32.encodeAsString(idComputedChecksum));
        }
    }

    static byte[] idToBytes(final String id) {
        return BaseEncoding.base64Url().decode(id);
    }

    static String bytesToId(final byte[] idBytes) {
        return BaseEncoding.base64Url().encode(idBytes);
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public Instant getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(final Instant uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(final Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(final String etag) {
        this.etag = etag;
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "id='" + id + '\'' +
                ", contentType=" + contentType +
                ", contentLength=" + contentLength +
                ", uploadTimestamp=" + uploadTimestamp.toString() +
                ", etag=" + etag +
                '}';
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof FileMetadata)) return false;
        final FileMetadata that = (FileMetadata) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(contentLength, that.contentLength) &&
                Objects.equals(uploadTimestamp, that.uploadTimestamp) &&
                Objects.equals(etag, that.etag);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, contentType, contentLength, uploadTimestamp, etag);
    }

    public Map<String, String> toEncryptionContext() {
        final Map<String, String> ec = new HashMap<>();
        ec.put("file-id", id);
        ec.put("content-type", contentType);
        ec.put("content-length", contentLength.toString());
        ec.put("upload-timestamp", uploadTimestamp.toString());
        return ec;
    }
}
