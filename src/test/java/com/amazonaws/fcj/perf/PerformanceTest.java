// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.perf;

import com.amazonaws.fcj.FileMetadata;
import com.amazonaws.fcj.TestUtils;
import com.amazonaws.fcj.tags.PerfTest;

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
public class PerformanceTest {
    private static final Logger LOG = LogManager.getLogger();

    private final WebClient client = WebClient.create("http://localhost:8080");

    @Test
    void parallelUpload() {
        final int filesCount = 32;
        final DataSize fileSize = DataSize.ofMegabytes(128);
        final int parallelism = 4;

        final Scheduler scheduler = Schedulers.parallel();
        Flux.range(1, filesCount)
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
                .bodyToMono(FileMetadata.class);
    }
}
