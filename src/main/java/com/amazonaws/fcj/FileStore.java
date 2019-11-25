// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileStore {
    Mono<FileMetadata> storeFile(Flux<DataBuffer> fileStream, Long contentLength, String contentType);

    ResponseEntity<Flux<byte[]>> getFile(String fileId);

    default void destroy() {}
}
