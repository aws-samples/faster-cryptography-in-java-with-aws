// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static software.amazon.awssdk.awscore.util.AwsHeader.AWS_REQUEST_ID;

import com.amazonaws.fcj.tags.UnitTest;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchResponse;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

@UnitTest
class CloudWatchEmitterTest {

    private static final String NAMESPACE = "UnitTest";

    private static final List<Dimension> DIMENSIONS = Collections.singletonList(
            Dimension.builder().name("Stage").value("test").build());

    private CloudWatchAsyncClient cloudWatchClient = mock(CloudWatchAsyncClient.class);

    private CloudWatchEmitter cloudWatchEmitter;

    @BeforeEach
    void setUp() {
        cloudWatchEmitter = new CloudWatchEmitter(cloudWatchClient, NAMESPACE, DIMENSIONS);
    }

    @Test
    void emitMetricDataTest() {
        final String metricName = "someMetric";
        final Duration duration = Duration.ofMillis(42);
        final String requestId = UUID.randomUUID().toString();
        final CloudWatchResponse putMetricDataResp = createResponseFromRequestId(requestId);

        doReturn(CompletableFuture.completedFuture(putMetricDataResp))
                .when(cloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
        final PutMetricDataResponse resp = cloudWatchEmitter.putDurationMetricData(metricName, duration).block();
        assertNotNull(resp);
        assertEquals(requestId, resp.responseMetadata().requestId());
        verify(cloudWatchClient).putMetricData(argThat((PutMetricDataRequest req) -> {
            assertEquals(NAMESPACE, req.namespace());
            assertEquals(1, req.metricData().size());
            final MetricDatum metricDatum = req.metricData().get(0);
            assertEquals(DIMENSIONS, metricDatum.dimensions());
            assertEquals(metricName, metricDatum.metricName());
            assertEquals(StandardUnit.MICROSECONDS, metricDatum.unit());
            assertEquals(TimeUnit.MILLISECONDS.toMicros(duration.toMillis()), metricDatum.value());
            return true;
        }));
    }

    private CloudWatchResponse createResponseFromRequestId(String requestId) {
        final DefaultAwsResponseMetadata responseMetadata =
                DefaultAwsResponseMetadata.create(Collections.singletonMap(
                AWS_REQUEST_ID,
                requestId));
        return PutMetricDataResponse.builder().responseMetadata(responseMetadata).build();
    }

}
