// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

@Component
public class CloudWatchEmitter {
    private static final Logger LOG = LogManager.getLogger();

    private final CloudWatchAsyncClient cloudWatchClient;
    private final String namespace;
    private final List<Dimension> dimensions;

    public CloudWatchEmitter(final CloudWatchAsyncClient cloudWatchClient,
                             @Qualifier("cloudWatchNamespace") final String namespace,
                             final List<Dimension> dimensions) {
        this.cloudWatchClient = cloudWatchClient;
        this.namespace = namespace;
        this.dimensions = new ArrayList<>(dimensions);
    }

    public Mono<PutMetricDataResponse> putDurationMetricData(final String metricName, final Duration duration) {
        final MetricDatum datum = MetricDatum.builder()
                .metricName(metricName)
                .unit(StandardUnit.MICROSECONDS)
                // CloudWatch doesn't support nanoseconds
                .value((double) TimeUnit.NANOSECONDS.toMicros(duration.toNanos()))
                .dimensions(dimensions)
                .build();
        final PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(namespace)
                .metricData(datum)
                .build();
        return Mono.fromFuture(cloudWatchClient.putMetricData(request))
                .doOnSuccess(resp -> LOG.info("Successfully submitted metric data to CloudWatch, request ID: {}",
                                              resp.responseMetadata().requestId()));
    }
}
