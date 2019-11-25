// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.amazonaws.fcj.exceptions.InvalidIdException;
import com.amazonaws.fcj.tags.UnitTest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

@UnitTest
class FileMetadataTest {

    public static final String CONTENT_TYPE = "image/jpeg";
    public static final long CONTENT_LENGTH = 42;
    public static final FileMetadata FILE_METADATA = FileMetadata.newFile(CONTENT_TYPE, CONTENT_LENGTH);

    @Test
    void roundtrip() {
        final String contentType = "image/jpeg";
        final long contentLength = 42;
        final FileMetadata fm = FileMetadata.newFile(contentType, contentLength);
        final FileMetadata fm2 = new FileMetadata(fm.getId(),
                                                  fm.getContentType(),
                                                  fm.getContentLength(),
                                                  fm.getUploadTimestamp());
        assertEquals(fm, fm2);
    }

    @Test
    void mangledBaseId() {
        final String mangledId = "AceWMn0s9FJdlWTJ6A_DKUoHxEqd2jX5SrBjnYnkSTY=";
        final String excMsg = assertThrows(InvalidIdException.class, () -> new FileMetadata(
                mangledId,
                FILE_METADATA.getContentType(),
                FILE_METADATA.getContentLength(),
                FILE_METADATA.getUploadTimestamp())).getMessage();
        assertThat(excMsg).startsWith("ID has incorrect checksum:");
    }

    @Test
    void wrongIdVersion() {
        final Charset idCharset = StandardCharsets.US_ASCII;
        final byte[] idBytes = FILE_METADATA.getId().getBytes(idCharset);
        idBytes[0]++;
        final String mangledId = new String(idBytes, idCharset);
        final String excMsg = assertThrows(InvalidIdException.class, () -> new FileMetadata(
                mangledId,
                FILE_METADATA.getContentType(),
                FILE_METADATA.getContentLength(),
                FILE_METADATA.getUploadTimestamp())).getMessage();
        assertThat(excMsg).startsWith("Wrong ID version:");
    }

    @Test
    void wrongIdLength() {
        final String excMsg = assertThrows(InvalidIdException.class, () -> new FileMetadata(
                "someid",
                FILE_METADATA.getContentType(),
                FILE_METADATA.getContentLength(),
                FILE_METADATA.getUploadTimestamp())).getMessage();
        assertThat(excMsg).startsWith("Wrong ID length:");
    }

    @Test
    void equalsTest() {
        EqualsVerifier.forClass(FileMetadata.class).suppress(Warning.NONFINAL_FIELDS).verify();
    }
}
