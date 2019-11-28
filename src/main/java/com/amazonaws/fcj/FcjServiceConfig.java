// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import static java.lang.String.format;

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider;
import com.amazonaws.fcj.exceptions.FcjServiceException;
import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.AliasListEntry;
import com.amazonaws.services.kms.model.ListAliasesRequest;
import com.amazonaws.services.kms.model.ListAliasesResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.config.EnableWebFlux;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Configuration
@EnableWebFlux
class FcjServiceConfig {

    private static final Logger LOG = LogManager.getLogger(FcjServiceConfig.class);

    public static final String SERVICE_NAME = "faster-cryptography-in-java";

    @Autowired
    private Environment env;

//    @Bean
//    @Lazy(false) // Disable lazy loading just in case the context has lazy loading on by default.
//    void enableAccp() {
//        AmazonCorrettoCryptoProvider.install();
//        AmazonCorrettoCryptoProvider.INSTANCE.assertHealthy();
//        LOG.info("Amazon Corretto Crypto Provider {} was successfully installed",
//                 AmazonCorrettoCryptoProvider.INSTANCE.getVersionStr());
//    }

    @Bean
    String profileSuffix() {
        final String[] activeProfiles = env.getActiveProfiles();
        return (activeProfiles.length != 0) ? (activeProfiles[0]) : "";
    }

    @Bean
    S3AsyncClient getS3Client() {
        return S3AsyncClient.create();
    }

    @Bean
    AWSSecurityTokenService getStsClient() {
        return AWSSecurityTokenServiceClientBuilder.defaultClient();
    }

    @Bean
    String fileBucketName(final S3AsyncClient s3,
                          @Qualifier("profileSuffix") final String profileSuffix,
                          @Qualifier("awsAccountId") final String awsAccountId,
                          @Qualifier("awsRegion") final String awsRegion)
            throws ExecutionException, InterruptedException {
        final String bucketName = Joiner.on("-").join(SERVICE_NAME, profileSuffix, awsAccountId, awsRegion);
        LOG.info("File bucket name: {}", bucketName);
        s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).get();
        return bucketName;
    }

    @Bean
    String awsAccountId(final AWSSecurityTokenService sts) {
        final GetCallerIdentityResult callerIdentity = sts.getCallerIdentity(new GetCallerIdentityRequest());
        return callerIdentity.getAccount();
    }

    @Bean
    String awsRegion() {
        return new DefaultAwsRegionProviderChain().getRegion();
    }

    @Bean
    AWSKMS getKmsClient(@Qualifier("awsRegion") final String awsRegion) {
        return AWSKMSClientBuilder.standard().withRegion(awsRegion).build();
    }

    @Bean
    AwsCrypto getEsdk() {
        return new AwsCrypto();
    }

    @Bean
    KmsMasterKeyProvider getKmsMkp(@Qualifier("kmsKeyArn") final String kmsKeyArn) {
        return KmsMasterKeyProvider.builder().withKeysForEncryption(kmsKeyArn).build();
    }

    /**
     * Specifies which KMS alias will be used to discover the actual KMS we'll use.
     */
    @Bean
    String kmsKeyAlias(@Qualifier("profileSuffix") final String profileSuffix) {
        return "alias/" + SERVICE_NAME + "-" + profileSuffix;
    }

    @Bean
    String kmsKeyArn(final AWSKMS kmsClient,
                     @Qualifier("kmsKeyAlias") final String kmsKeyAlias,
                     final String awsRegion) {
        final Optional<String> kmsKeyArn = findKmsKeyArnFromAlias(kmsClient, kmsKeyAlias, awsRegion);
        if (kmsKeyArn.isPresent()) {
            LOG.info("Using KMS key {} (from alias: {})", kmsKeyArn.get(), kmsKeyAlias);
            return kmsKeyArn.get();
        }
        throw new FcjServiceException("Failed to find an appropriate KMS key");
    }

    private Optional<String> findKmsKeyArnFromAlias(final AWSKMS kmsClient,
                                                    final String aliasName,
                                                    final String awsRegion) {
        final ListAliasesRequest listAliasesRequest = new ListAliasesRequest();
        do {
            final ListAliasesResult listAliasesResult = kmsClient.listAliases(listAliasesRequest);
            final Optional<AliasListEntry> found = listAliasesResult.getAliases().stream()
                    .filter(e -> aliasName.equals(e.getAliasName()))
                    .findFirst();
            if (found.isPresent()) {
                final String arnPrefix = getArnPrefix(found.get().getAliasArn());
                return Optional.of(kmsKeyIdToArn(arnPrefix, found.get().getTargetKeyId()));
            }
            listAliasesRequest.setMarker(listAliasesResult.getNextMarker());
            if (!listAliasesResult.isTruncated()) {
                break;
            }
        } while (true);
        LOG.info("Failed to find KMS alias \"{}\" in region {}", aliasName, awsRegion);
        return Optional.empty();
    }

    private String kmsKeyIdToArn(final String arnPrefix, final String kmsKeyId) {
        return format("%s:key/%s", arnPrefix, kmsKeyId);
    }

    /**
     * Naive helper method to get ARN prefix from a full ARN. For example,
     * "arn:aws:kms:us-west-2:249645522726:alias/foo"
     * gets converted to "arn:aws:kms:us-west-2:249645522726".
     */
    private String getArnPrefix(final String aliasArn) {
        return aliasArn.substring(0, aliasArn.lastIndexOf(":"));
    }

    @Bean
    CloudWatchAsyncClient cloudWatchClient(final String awsRegion) {
        return CloudWatchAsyncClient.builder().region(Region.of(awsRegion)).build();
    }

    @Bean
    List<Dimension> cloudWatchDimensions(final String profileSuffix, final String awsRegion) {
        List<Dimension> dimensions = new ArrayList<>();
        dimensions.add(Dimension.builder().name("Stage").value(profileSuffix).build());
        dimensions.add(Dimension.builder().name("Region").value(awsRegion).build());
        return dimensions;
    }

    @Bean
    String cloudWatchNamespace(final String profileSuffix) {
        return Joiner.on("-").join(SERVICE_NAME, profileSuffix);
    }
}
