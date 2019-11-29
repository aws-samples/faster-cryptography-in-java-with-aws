// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import static java.lang.String.format;

import com.amazonaws.fcj.exceptions.FcjServiceException;
import com.amazonaws.fcj.utils.Utils;
import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CryptoOutputStream;
import com.amazonaws.encryptionsdk.kms.KmsMasterKey;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.unit.DataSize;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

@Repository
public class S3FileStore implements FileStore {
    private static final Logger LOG = LogManager.getLogger();
    private static final long MEGABYTE = 1024 * 1024;

    private static final String S3_OBJECT_KEY_PREFIX = "files/";
    private static final int ENCRYPTION_BUFFER_SIZE_BYTES = 16_384;

    private static final DataSize BUFFER_THRESHOLD = DataSize.ofMegabytes(6);

    /**
     * Sets the maximum number of parallel part uploads for a single request.
     */
    private static final int UPLOAD_PART_PARALLELISM = 4;

    /**
     * When a new sub-flux for upload part starts, it needs to send a request for more "data events" downstream so that
     * it has something to process (this is called prefetch). Reactor uses 32 as the default prefetch number. Since our
     * data events are BUFFER_THRESHOLD large (6 MB at the time I'm writing this), it means each upload part sub-flux
     * would have 192 MB of data prefetched. That's quite excessive so we're capping it at just one prefetched data
     * event.
     */
    private static final int UPLOAD_PART_PARALLEL_PREFETCH = 1;

    private final Scheduler scheduler = Schedulers.parallel();

    private final S3AsyncClient s3;

    private final AwsCrypto esdk;

    private final KmsMasterKeyProvider kmsMasterKeyProvider;

    private final CloudWatchEmitter cloudWatchEmitter;

    /**
     * The name of the bucket where our files are stored. Keep in mind bucket names are global so this will contain
     * unique identifiers such as account number and region.
     */
    private final String fileBucketName;

    @Autowired
    S3FileStore(final S3AsyncClient s3,
                final AwsCrypto esdk,
                final KmsMasterKeyProvider kmsMasterKeyProvider,
                final CloudWatchEmitter cloudWatchEmitter,
                final String fileBucketName) {
        this.s3 = s3;
        this.esdk = esdk;
        this.kmsMasterKeyProvider = kmsMasterKeyProvider;
        this.cloudWatchEmitter = cloudWatchEmitter;
        this.fileBucketName = fileBucketName;
    }

    private static String createMultipartETag(final byte[] checksum, final int partCount) {
        return Utils.QUOTE + Hex.encodeHexString(checksum) + "-" + partCount + Utils.QUOTE;
    }

    private static String createETag(final byte[] checksum) {
        return Utils.QUOTE + Hex.encodeHexString(checksum) + Utils.QUOTE;
    }

    @Override
    public Mono<FileMetadata> storeFile(final Flux<DataBuffer> fileStream,
                                        final Long contentLength,
                                        final String contentType) {
        MediaType.parseMediaType(contentType); // sanity check
        final FileMetadata fileMetadata = FileMetadata.newFile(contentType, contentLength);

        final ByteArrayOutputStream esdkOutputStream = new ByteArrayOutputStream(ENCRYPTION_BUFFER_SIZE_BYTES * 2);
        final Map<String, String> encryptionContext = fileMetadata.toEncryptionContext();
        LOG.info("Starting file upload for {}, encryptionContext={}", fileMetadata.getId(), encryptionContext);
        final CryptoOutputStream<KmsMasterKey> encryptingOutputStream = esdk.createEncryptingStream(
                kmsMasterKeyProvider, esdkOutputStream, encryptionContext);
        // This is a hint to the ESDK to help with reducing buffer reallocations. In a nutshell, when the ESDK knows
        // how much data to expect it can choose the right size for its buffers at the beginning rather than changing
        // it later and incurring the cost of reallocation.
        encryptingOutputStream.setMaxInputLength(contentLength);

        final String objectPath = S3_OBJECT_KEY_PREFIX + fileMetadata.getId();
        final Mono<CreateMultipartUploadResponse> uploadInitMono = Mono.fromFuture(
                s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                                                 .bucket(fileBucketName)
                                                 .key(objectPath)
                                                 .contentType(fileMetadata.getContentType())
                                                 .build()));

        final AtomicInteger partCounter = new AtomicInteger(0);
        final AtomicLong timeSpentWithCryptoNanos = new AtomicLong(0);
        final AtomicLong plaintextBytesCounter = new AtomicLong(0);

        return fileStream
                // Ensure the streaming operations are running on the scheduler (thread pool) we specify rather than
                // subscriber's thread.
                .subscribeOn(scheduler)
                // Increment the counter for plaintext data. We use this for metrics.
                .doOnNext(buf -> plaintextBytesCounter.addAndGet(buf.readableByteCount()))
                // Add handlers that encrypt data passing through the
                .transformDeferred(flux -> addEncryptionHandlers(flux, encryptingOutputStream, esdkOutputStream,
                                                                 timeSpentWithCryptoNanos))
                // At this point in the flux ("pipeline"), data are encrypted but are in many small chunks. For
                // simplicity we're using S3 UploadPart API for all data and that API requires that all but the last
                // part is at least 5 MB large so we'll buffer the encrypted chunks into a list until a threshold is
                // reached.
                .bufferUntil(new SizeMoreThan<>(BUFFER_THRESHOLD.toBytes(), arr -> arr.length))
                // Concatenate all the chunks from the list into one large byte array.
                .map(Utils::concat)
                // Wait until the CreateMultiPartUpload call finishes. We can't start uploading parts before then.
                .delayUntil(part -> uploadInitMono)
                // Number the part. This MUST be done before we parallelize because S3 requires that only the last
                // part can be less than 5MB. If we increment part numbers in parallel we can have a situation where,
                // for example, next to last part is the last remaining bit, which ends up smaller than 5MB and we
                // get an exception from S3!
                .map(partBytes -> new Part(partBytes, partCounter.incrementAndGet()))
                // Split this flux of PartUpload structures into UPLOAD_PART_PARALLELISM rails (sub-fluxes) and
                // sequentially start initiating uploads for items on each rail.
//                .parallel(UPLOAD_PART_PARALLELISM, UPLOAD_PART_PARALLEL_PREFETCH).runOn(scheduler)
                .concatMap(part -> initiatePartUpload(part, uploadInitMono))
                // Collect all the ongoing chunk uploads into a list. This basically means the flux will wait until
                // all part uploads were initiated.
//                .sequential()
                // This is where we pause until all part uploads are finished.
                .collectList()
                // Now that we have all the completed parts, we'll complete the multi-part upload.
                .flatMap(partUploads -> completeMultipartUpload(partUploads, uploadInitMono, fileMetadata))
                .delayUntil(fm -> recordCryptoSpeed("encrypt", timeSpentWithCryptoNanos, plaintextBytesCounter));
    }

    @Override
    public ResponseEntity<Flux<byte[]>> getFile(final String fileId) {
        final ByteArrayOutputStream decryptedOutputStream = new ByteArrayOutputStream(ENCRYPTION_BUFFER_SIZE_BYTES * 2);
        final CryptoOutputStream<KmsMasterKey> decryptingOutputStream =
                esdk.createDecryptingStream(kmsMasterKeyProvider, decryptedOutputStream);

        final String objectPath = S3_OBJECT_KEY_PREFIX + fileId;

        final AtomicReference<MediaType> contentType = new AtomicReference<>();

        final GetObjectRequest getObjReq = GetObjectRequest.builder()
                .bucket(fileBucketName)
                .key(objectPath)
                .build();

        final AtomicLong timeSpentWithCryptoNanos = new AtomicLong(0);
        final AtomicLong plaintextBytesCounter = new AtomicLong(0);

        return Flux.<ByteBuffer>create(emitter -> s3.getObject(
                getObjReq, new GetObjectResponseAsyncResponseTransformer(
                        emitter, getObjResp -> decryptingOutputStream.setMaxInputLength(getObjResp.contentLength()))))
                .subscribeOn(scheduler)
                .transformDeferred(flux -> addDecryptionHandlers(
                        flux, decryptingOutputStream, decryptedOutputStream, timeSpentWithCryptoNanos))
                .doOnNext(buf -> plaintextBytesCounter.addAndGet(buf.length))
                .transformDeferred(f -> delayFluxCompletionUntil(
                        f, recordCryptoSpeed("decrypt", timeSpentWithCryptoNanos, plaintextBytesCounter)))
                .as(flux -> ResponseEntity.ok().contentType(contentType.get()).body(flux));
    }

    /**
     * Delays <i>completion</i> of the {@code flux} until a given {@code delayTrigger} completes.
     *
     * @param flux The flux to delay completion of.
     * @param delayTrigger The publisher that needs to complete before the flux will complete.
     * @param <T> Type of the elements in the flux we're delaying.
     * @return The delayed flux.
     */
    private <T> Flux<T> delayFluxCompletionUntil(Flux<T> flux, Publisher<?> delayTrigger) {
        return flux.materialize()
                .delayUntil(s -> {
                    if (!s.isOnComplete()) {
                        return Mono.empty();
                    }
                    return delayTrigger;
                })
                .dematerialize();
    }

    private Mono<Void> recordCryptoSpeed(final String operation,
                                         final AtomicLong timeSpentWithCryptoNanos,
                                         final AtomicLong byteCount) {
        final double megabytesProcessed = (double) byteCount.get() / MEGABYTE;
        if (megabytesProcessed == 0) {
            // Looks like we have processed zero megabytes which is odd, let's not emit any speed metrics.
            return Mono.empty();
        }
        final Duration timePerMb =
                Duration.ofNanos((long) ((double) timeSpentWithCryptoNanos.get() / megabytesProcessed));
        LOG.info("Crypto speed metric: timeSpentWithCrypto={}, byteCount={}, timePerMb={}",
                 Duration.ofNanos(timeSpentWithCryptoNanos.get()), byteCount.get(), timePerMb);
        final String metricName = String.format("%s.duration.perMb", operation);
        return cloudWatchEmitter.putDurationMetricData(metricName, timePerMb).then();
    }

    /**
     * Completes the entire multipart upload and returns file metadata we'll give to the caller.
     */
    private Mono<FileMetadata> completeMultipartUpload(
            final List<PartUpload> partUploads,
            final Mono<CreateMultipartUploadResponse> uploadInitFuture,
            final FileMetadata fileMetadata) {
        return uploadInitFuture.flatMap(uploadInitResp -> {
            final String bucketName = uploadInitResp.bucket();
            final String objectPath = uploadInitResp.key();
            final String uploadId = uploadInitResp.uploadId();

            final MessageDigest overallChecksum = Utils.getMessageDigestForETag();

            // We need to sort the list of uploads by part number. First, S3 requires the list of completed parts to be
            // sorted by part number. Second, we need to hash them together in the right order!
            partUploads.sort(Comparator.comparingInt(PartUpload::getPartNumber));
            final List<CompletedPart> completedParts = new ArrayList<>(partUploads.size());
            for (PartUpload partUpload : partUploads) {
                completedParts.add(partUpload.asCompletedPart());
                overallChecksum.update(partUpload.getPartChecksum());
            }

            final CompleteMultipartUploadRequest completeUploadReq = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectPath)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();
            LOG.info("Completing multipart upload: bucketName={}, objectPath={}, uploadId={}, parts={}",
                     bucketName, objectPath, uploadId, completedParts);

            return Mono.fromFuture(s3.completeMultipartUpload(completeUploadReq))
                    .handle((completedUpload, sink) -> {
                        final String localEtag = createMultipartETag(overallChecksum.digest(), completedParts.size());
                        final String remoteEtag = completedUpload.eTag();
                        if (localEtag.equals(remoteEtag)) {
                            LOG.info("Finished multipart upload of a new object: " +
                                             "bucketName={}, objectPath={}, ETag={}",
                                     bucketName, objectPath, localEtag);
                            fileMetadata.setEtag(remoteEtag);
                            sink.next(fileMetadata);
                        } else {
                            sink.error(new FcjServiceException(format(
                                    "Upload ETag mismatch: bucketName=%s, objectPath=%s, localETag=%s remoteETag=%s",
                                    bucketName, objectPath, localEtag, remoteEtag)));
                        }
                    });
        });
    }

    private Flux<byte[]> closeCryptoStreamOnComplete(final Flux<byte[]> flux,
                                                     final CryptoOutputStream<?> cryptoOutputStream,
                                                     final ByteArrayOutputStream byteArrayOutputStream,
                                                     final AtomicLong timeSpentWithCryptoNanos) {
        return flux
                .materialize()
                .concatMap(signal -> Flux.create(sink -> {
                    if (signal.getType().equals(SignalType.ON_COMPLETE)) {
                        final byte[] remainingChunk;
                        try {
                            // Close the crypto operation stream so that the ciphertext trailer (for encryption)
                            // or the rest if plaintext (for decryption) is written out.
                            final long startNanos = System.nanoTime();
                            cryptoOutputStream.close();
                            timeSpentWithCryptoNanos.addAndGet(System.nanoTime() - startNanos);
                            remainingChunk = byteArrayOutputStream.toByteArray();
                            byteArrayOutputStream.close();
                        } catch (final IOException e) {
                            sink.error(e);
                            return;
                        }
                        if (remainingChunk.length > 0) {
                            sink.next(Signal.next(remainingChunk));
                        }
                    }
                    sink.next(signal); // Forward the original signal to the outer flux.
                    sink.complete(); // Complete this inner flux so that the outer flux can continue.
                }))
                .dematerialize();
    }

    private Flux<byte[]> addEncryptionHandlers(final Flux<DataBuffer> flux,
                                               final CryptoOutputStream<?> encryptingOutputStream,
                                               final ByteArrayOutputStream encryptedDataBuffer,
                                               final AtomicLong timeSpentWithCryptoNanos) {
        return flux
                .concatMap(buf -> Flux.<byte[]>create(sink -> {
                    try {
                        while (buf.readableByteCount() > 0) {
                            final int readSize = Math.min(ENCRYPTION_BUFFER_SIZE_BYTES, buf.readableByteCount());
                            final byte[] plaintextChunk = new byte[readSize];
                            buf.read(plaintextChunk);
                            final long startEnc = System.nanoTime();
                            encryptingOutputStream.write(plaintextChunk);
                            timeSpentWithCryptoNanos.addAndGet(System.nanoTime() - startEnc);
                            final byte[] encryptedChunk = encryptedDataBuffer.toByteArray();
                            sink.next(encryptedChunk);
                            encryptedDataBuffer.reset();
                        }
                    } catch (final IOException e) {
                        sink.error(e);
                        return;
                    } finally {
                        // The incoming DataBuffer needs to be manually released because Spring/Netty loses
                        // track of it :(. See https://stackoverflow.com/a/51321602 for more details.
                        DataBufferUtils.release(buf);
                    }
                    sink.complete();
                }))
                .transformDeferred(f -> closeCryptoStreamOnComplete(
                        f, encryptingOutputStream, encryptedDataBuffer, timeSpentWithCryptoNanos));
    }

    private Flux<byte[]> addDecryptionHandlers(final Flux<ByteBuffer> flux,
                                               final CryptoOutputStream<?> decryptingOutputStream,
                                               final ByteArrayOutputStream decryptedDataBuffer,
                                               final AtomicLong timeSpentWithCryptoNanos) {
        return flux
                .concatMap(buf -> Flux.<byte[]>create(sink -> {
                    while (buf.remaining() > 0) {
                        try {
                            final byte[] ciphertextChunk;
                            // If the incoming ByteBuffer has an underlying byte array it's much more efficient to
                            // reuse it.
                            if (buf.hasArray() &&
                                    buf.remaining() == buf.array().length &&
                                    buf.position() == 0 &&
                                    buf.arrayOffset() == 0 &&
                                    buf.remaining() < ENCRYPTION_BUFFER_SIZE_BYTES) {
                                ciphertextChunk = buf.array();
                                BufferUtils.position(buf, buf.limit());
                            } else {
                                final int readSize = Math.min(ENCRYPTION_BUFFER_SIZE_BYTES, buf.remaining());
                                ciphertextChunk = new byte[readSize];
                                buf.get(ciphertextChunk);
                            }
                            long start = System.nanoTime();
                            decryptingOutputStream.write(ciphertextChunk);
                            timeSpentWithCryptoNanos.addAndGet(System.nanoTime() - start);
                            final byte[] plaintextChunk = decryptedDataBuffer.toByteArray();
                            decryptedDataBuffer.reset();
                            if (plaintextChunk.length != 0) {
                                // We don't want to send empty chunks upstream.
                                sink.next(plaintextChunk);
                            }
                        } catch (final IOException e) {
                            sink.error(e);
                        }
                    }
                    sink.complete();

                }))
                .transformDeferred(f -> closeCryptoStreamOnComplete(
                        f, decryptingOutputStream, decryptedDataBuffer, timeSpentWithCryptoNanos));
    }

    /**
     * Kicks off the actual uploading of the part and returns a class that represents the process ({@link PartUpload}.
     *
     * @param part The part to upload.
     * @param uploadInitMono The {@link Mono} we need to get the response to CreateMultiPartUpload from S3.
     * @return Another {@link Mono} containing state related to uploading this part.
     */
    private Mono<PartUpload> initiatePartUpload(
            final Part part,
            final Mono<CreateMultipartUploadResponse> uploadInitMono) {
        return uploadInitMono
                .map(uploadInitResp -> new PartUpload(uploadInitResp, part))
                .flatMap(partUpload -> {
                    LOG.info("Initiating {}", partUpload);
                    final byte[] partChecksum = Utils.computeETagChecksum(part.getPartBytes());
                    partUpload.setPartChecksum(partChecksum);
                    final UploadPartRequest uploadPartRequest = partUpload.createUploadPartRequest();
                    return Mono.fromFuture(s3.uploadPart(uploadPartRequest,
                                                         new ByteArrayAsyncRequestBody(part.getPartBytes())))
                            .handle((resp, sink) -> completePartUpload(resp, sink, partUpload));
                });
    }

    /**
     * This function is called when a part finishes uploading. It's primary function is to verify the ETag of the part
     * we just uploaded.
     */
    private void completePartUpload(final UploadPartResponse uploadPartResponse,
                                    final SynchronousSink<PartUpload> sink,
                                    final PartUpload partUpload) {
        final String partEtag = uploadPartResponse.eTag();
        partUpload.setPartEtag(partEtag);
        final String checksumEtag = createETag(partUpload.getPartChecksum());
        if (checksumEtag.equals(partEtag)) {
            LOG.info("Finished {}", partUpload);
            sink.next(partUpload);
        } else {
            sink.error(new FcjServiceException(format(
                    "ETag mismatch when uploading part %s, our ETag was %s but S3 returned %s",
                    partUpload.getPartNumber(), checksumEtag, partEtag)));
        }
    }

    /**
     * A predicate to test how many bytes we have already accumulated.
     */
    static class SizeMoreThan<T> implements Predicate<T> {

        private final long thresholdBytes;
        private final Function<T, Integer> sizeSupplier;

        private long byteCounter = 0;

        SizeMoreThan(final long thresholdBytes, final Function<T, Integer> sizeSupplier) {
            this.thresholdBytes = thresholdBytes;
            this.sizeSupplier = sizeSupplier;
        }

        @Override
        public boolean test(final T thing) {
            byteCounter += sizeSupplier.apply(thing);
            if (byteCounter < thresholdBytes) {
                // We haven't exceeded the threshold.
                return false;
            }
            byteCounter = 0;
            return true;
        }
    }

    /**
     * A trivial implementation of {@link AsyncResponseTransformer} that subscribes to the {@link
     * org.reactivestreams.Publisher} supplied by the AWS SDK. When the SDK publisher sends a piece of data this class
     * simply forwards it to the provided sink.
     */
    private static class GetObjectResponseAsyncResponseTransformer
            implements AsyncResponseTransformer<GetObjectResponse, Object> {
        private final FluxSink<ByteBuffer> sink;
        private final Consumer<GetObjectResponse> responseConsumer;

        private GetObjectResponseAsyncResponseTransformer(final FluxSink<ByteBuffer> sink,
                                                          final Consumer<GetObjectResponse> responseConsumer) {
            this.sink = sink;
            this.responseConsumer = responseConsumer;
        }

        @Override
        public CompletableFuture<Object> prepare() {
            return CompletableFuture.completedFuture(new Object());
        }

        @Override
        public void onResponse(final GetObjectResponse response) {
            responseConsumer.accept(response);
        }

        @Override
        public void onStream(final SdkPublisher<ByteBuffer> publisher) {
            publisher.subscribe(new Subscriber<ByteBuffer>() {
                @Override
                public void onSubscribe(final Subscription s) {
                    sink.onRequest(s::request);
                    sink.onCancel(s::cancel);
                }

                @Override
                public void onNext(final ByteBuffer buf) {
                    sink.next(buf);
                }

                @Override
                public void onError(final Throwable t) {
                    LOG.info("An error occured when streaming GetObject body", t);
                    sink.error(t);
                }

                @Override
                public void onComplete() {
                    sink.complete();
                }
            });
        }

        @Override
        public void exceptionOccurred(final Throwable error) {
            sink.error(error);
        }
    }

    private static class Part {
        private final byte[] partBytes;
        private final int partNumber;

        Part(final byte[] partBytes, final int partNumber) {
            this.partBytes = partBytes;
            this.partNumber = partNumber;
        }

        public byte[] getPartBytes() {
            return partBytes;
        }

        public int getPartNumber() {
            return partNumber;
        }

        public int getPartLength() {
            return partBytes.length;
        }
    }

    /**
     * Represents the state around uploading the part but not the actual content of the part.
     */
    private static class PartUpload {
        private final String bucketName;
        private final String objectPath;
        private final String uploadId;
        private final int partNumber;
        private long partLength;
        private byte[] partChecksum;
        private String partEtag;

        PartUpload(final CreateMultipartUploadResponse uploadInitResp,
                   final Part part) {
            this.bucketName = uploadInitResp.bucket();
            this.objectPath = uploadInitResp.key();
            this.uploadId = uploadInitResp.uploadId();
            this.partNumber = part.getPartNumber();
            this.partLength = part.getPartLength();
        }

        UploadPartRequest createUploadPartRequest() {
            return UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(objectPath)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(partLength)
                    .build();
        }

        int getPartNumber() {
            return partNumber;
        }

        public void setPartEtag(final String partEtag) {
            this.partEtag = partEtag;
        }

        byte[] getPartChecksum() {
            return partChecksum;
        }

        public void setPartChecksum(final byte[] partChecksum) {
            this.partChecksum = partChecksum;
        }

        CompletedPart asCompletedPart() {
            return CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(partEtag)
                    .build();
        }

        @Override
        public String toString() {
            return "PartUpload{" +
                    "bucketName='" + bucketName + '\'' +
                    ", objectPath='" + objectPath + '\'' +
                    ", uploadId='" + uploadId + '\'' +
                    ", partNumber=" + partNumber +
                    ", partEtag='" + partEtag + '\'' +
                    ", partLength='" + partLength + '\'' +
                    '}';
        }


    }
}
