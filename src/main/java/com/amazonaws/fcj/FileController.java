// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import com.amazonaws.fcj.utils.Utils;

import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController("/")
public class FileController {

    private static final Logger LOG = LogManager.getLogger(FileController.class);

    private final FileStore fileStore;

    @Autowired
    public FileController(final FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @PostMapping("/file")
    public Mono<FileMetadata> uploadFile(@RequestBody final Flux<DataBuffer> body,
                                         @RequestHeader("Content-Length") final Long contentLength,
                                         @RequestHeader("Content-Type") final String contentType) {
        return fileStore.storeFile(body, contentLength, contentType);
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Flux<byte[]>> downloadFile(@PathVariable String id) {
        return fileStore.getFile(id);
    }

    @PostMapping("/hash")
    public Mono<String> doHash(@RequestBody final Flux<DataBuffer> body,
                               @RequestHeader("Content-Length") Long contentLength) {
        return body
                .subscribeOn(Schedulers.elastic())
                .concatMap(buf -> Flux.generate(
                        buf::readableByteCount, (readableBytesCnt, sink) -> {
                            if (readableBytesCnt > 0) {
                                final int readSize = Math.min(1024, readableBytesCnt);
                                final byte[] arr = new byte[readSize];
                                buf.read(arr);
                                sink.next(arr);
                            } else {
                                DataBufferUtils.release(buf);
                                sink.complete();
                            }
                            return buf.readableByteCount();
                        }))
                .cast(byte[].class)
                .concatMap(arr -> Mono.subscriberContext().map(ctx -> {
                    final MessageDigest md = ctx.get("md");
                    md.update(arr);
                    return arr;
                }))
                .then(Mono.subscriberContext().map(ctx -> {
                    final MessageDigest md = ctx.get("md");
                    return Hex.encodeHexString(md.digest());
                }))
                .subscriberContext(ctx -> ctx.put("md", Utils.getMessageDigestForDefaultHash()));
    }
}
