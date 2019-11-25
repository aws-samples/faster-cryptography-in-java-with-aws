// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import reactor.core.scheduler.Schedulers;

@SpringBootApplication
public class FasterCryptographyInJavaService {
    private static final Logger LOG = LogManager.getLogger();
    private static final Path MEM_STATS_PATH = Paths.get("/sys/fs/cgroup/memory/memory.stat");

    public static void main(String[] args) {
        Schedulers.enableMetrics();
        logBasicSystemStats();

        SpringApplication.run(FasterCryptographyInJavaService.class, args);
    }

    private static void logBasicSystemStats() {
        final Runtime r = Runtime.getRuntime();
        LOG.info("Available cores: {}", r.availableProcessors());

        try {
            if (Files.isReadable(MEM_STATS_PATH)) {
                final BufferedReader bufReader = Files.newBufferedReader(MEM_STATS_PATH, StandardCharsets.UTF_8);
                final List<String> memStats = bufReader.lines()
                        .filter(s -> s.startsWith("hierarchical_mem"))
                        .collect(toList());
                LOG.info("Container memory limit in bytes:\n{}", Joiner.on("\n").join(memStats));
            }
        } catch (final Throwable t) {
            LOG.info("Failed to print memory stats, ignoring", t);
        }
    }

}
