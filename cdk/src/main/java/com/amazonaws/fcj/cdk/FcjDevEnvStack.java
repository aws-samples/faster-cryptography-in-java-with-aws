// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import static java.lang.String.format;

import java.time.Duration;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.CfnParameter;
import software.amazon.awscdk.core.CfnParameterProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.cloud9.CfnEnvironmentEC2;
import software.amazon.awscdk.services.cloud9.CfnEnvironmentEC2Props;
import software.amazon.awscdk.services.ec2.Vpc;

public class FcjDevEnvStack extends Stack {
    public FcjDevEnvStack(final Construct scope, final String id) {
        super(scope, id, StackProps.builder()
                .stackName("Faster-Cryptography-in-Java-with-AWS-Dev-Env")
                .build());

        final Vpc devVpc = new Vpc(this, "dev-vpc");

        final CfnParameter ideNameParam = new CfnParameter(this, "C9IdeName", CfnParameterProps.builder()
                .description("Development environment for Faster Cryptography in Java with AWS")
                .defaultValue("Faster Cryptography in Java with AWS")
                .minLength(1)
                .maxLength(60)
                .build());

        final CfnEnvironmentEC2 cfnEnv = new CfnEnvironmentEC2(
                this,
                "FcjIde",
                CfnEnvironmentEC2Props.builder()
                        .name(ideNameParam.getValueAsString())
                        .subnetId(devVpc.getPublicSubnets().iterator().next().getSubnetId())
                        .instanceType("t3.medium")
                        .automaticStopTimeMinutes(Duration.ofHours(4).toMinutes())
                        .build());

        final String cfnUrl = format("https://%s.console.aws.amazon.com/cloud9/ide/%s", getRegion(), cfnEnv.getRef());
        new CfnOutput(this, "IdeUrl", CfnOutputProps.builder()
                .description("URL to open your Cloud9 IDE")
                .value(cfnUrl)
                .build());
    }
}
