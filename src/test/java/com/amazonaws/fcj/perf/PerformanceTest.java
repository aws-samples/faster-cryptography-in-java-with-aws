// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.perf;

import com.amazonaws.fcj.FileMetadata;
import com.amazonaws.fcj.TestUtils;
import com.amazonaws.fcj.tags.PerfTest;

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@PerfTest
class PerformanceTest {
    private static final Logger LOG = LogManager.getLogger();

    private final String baseUrl;
    private final WebClient client;

    private final int fileCount;
    private final DataSize fileSize;

    PerformanceTest() {
        baseUrl = TestUtils.getEnvVar("FCJ_TEST_BASE_URL","http://localhost:8080");
        client = WebClient.create(baseUrl);

        fileCount = TestUtils.getEnvVar("FCJ_TEST_FILE_COUNT", Integer::parseInt, 32);
        fileSize = TestUtils.getEnvVar("FCJ_TEST_FILE_SIZE", DataSize::parse, DataSize.ofMegabytes(16));
    }

    @Test
    void parallelUpload() {
        LOG.info("Uploading {} files of size {} to {}", fileCount, fileSize, baseUrl);
        final int parallelism = 4;
        final Scheduler scheduler = Schedulers.parallel();
        Flux.range(0, fileCount)
                .parallel(parallelism).runOn(scheduler)
                .concatMap(i -> {
                    LOG.info("Starting to upload file #{} of size {}", i, fileSize);
                    return uploadRandomFile(fileSize).doOnSuccess(
                            fm -> LOG.info("Successfully uploaded file #{} as {} with ETag {}",
                                           i, fm.getId(), fm.getEtag()));
                })
                .then().block();
    }

    Mono<FileMetadata> uploadRandomFile(final DataSize fileSize) {
        final Flux<byte[]> randomFlux = TestUtils.getRandomFlux(fileSize);
        return client.post()
                .uri("/file")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileSize.toBytes())
                .accept(MediaType.APPLICATION_JSON)
                .body(randomFlux, byte[].class)
                .retrieve()
                .bodyToMono(FileMetadata.class)
                .retryBackoff(2, Duration.ofSeconds(3));
    }
}
