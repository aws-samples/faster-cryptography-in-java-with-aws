// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.integ;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider;
import com.amazonaws.fcj.FileMetadata;
import com.amazonaws.fcj.TestUtils;
import com.amazonaws.fcj.tags.IntegTest;
import com.amazonaws.fcj.utils.Utils;

import java.security.MessageDigest;
import java.time.Duration;

import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;

@IntegTest
class RoundtripIntegrationTest {

    private static final Logger LOG = LogManager.getLogger();
    private static final Duration HTTP_OP_TIMEOUT = Duration.ofMinutes(5);
    private final WebClient client = WebClient.create("http://localhost:8080");

    @BeforeAll
    static void enableAccp() {
        AmazonCorrettoCryptoProvider.install();
    }

    /**
     * This test streams random data to the service while hashing them. Then it downloads the data back and checks
     * the hash.
     */
    @Test
    void uploadDownloadFileCheckHash() {
        final DataSize contentLength = DataSize.ofMegabytes(4);
        final MessageDigest md = Utils.getMessageDigestForDefaultHash();
        final Flux<byte[]> randomHashingFlux = TestUtils.getRandomFlux(contentLength)
                .transformDeferred(f -> f.doOnNext(md::update));

        final FileMetadata fm = client.post()
                .uri("/file")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(contentLength.toBytes())
                .accept(MediaType.APPLICATION_JSON)
                .body(randomHashingFlux, byte[].class)
                .retrieve()
                .bodyToMono(FileMetadata.class)
                .block(HTTP_OP_TIMEOUT);

        assertNotNull(fm);
        final byte[] expectedDigest = md.digest();
        LOG.info("Successfully uploaded file with hash {}", Hex.encodeHexString(expectedDigest));
        md.reset();

        LOG.info("Downloading file with ID {}", fm.getId());
        client.get()
                .uri("/file/{id}", fm.getId())
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToFlux(byte[].class)
                .transformDeferred(f -> f.doOnNext(md::update))
                .blockLast(HTTP_OP_TIMEOUT);

        assertArrayEquals(expectedDigest, md.digest());
    }
}
