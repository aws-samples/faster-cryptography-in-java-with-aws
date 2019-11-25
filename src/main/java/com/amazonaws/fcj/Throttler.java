// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import com.amazonaws.fcj.exceptions.FcjServiceException;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

class Throttler {
    private final int maxPermitsInUse;
    private final AtomicInteger permitsInUse = new AtomicInteger(0);
    private final Queue<SinkCompleter> permitQueue = new ConcurrentLinkedQueue<>();

    Throttler(final int maxPermitsInUse) {
        this.maxPermitsInUse = maxPermitsInUse;
    }

    private static class SinkCompleter {
        private MonoSink<Void> sink;

        void setSink(final MonoSink<Void> sink) {
            this.sink = sink;
        }

        void finish() {
            sink.success();
        }
    }

    Mono<Void> acquire() {
        if (permitsInUse.incrementAndGet() > maxPermitsInUse) {
            final SinkCompleter cm = new SinkCompleter();
            if (permitQueue.offer(cm)) {
                return Mono.create(cm::setSink);
            }
            // If our queue has a limited capacity and that capacity is exceeded we'll emit an error.
            return Mono.error(new FcjServiceException("Too many upload requests are queued"));
        } else {
            return Mono.empty();
        }
    }

    void release() {
        final SinkCompleter completer = permitQueue.poll();
        if (completer != null) {
            completer.finish();
        }
    }
}
