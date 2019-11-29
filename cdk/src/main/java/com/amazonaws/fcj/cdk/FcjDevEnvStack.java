// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import java.time.Duration;

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
                .stackName("Dev-Env-for-FCJ-in-Java-with-AWS")
                .build());

        final Vpc devVpc = new Vpc(this, "dev-vpc");

        final CfnParameter ideNameParam = new CfnParameter(this, "C9IDEName", CfnParameterProps.builder()
                .description("Development environment for Faster Cryptography in Java with AWS")
                .defaultValue("Faster Cryptography in Java with AWS")
                .minLength(1)
                .maxLength(60)
                .build());

        new CfnEnvironmentEC2(this, "fcj-c9-ide",
                              CfnEnvironmentEC2Props.builder()
                                      .name(ideNameParam.getValueAsString())
                                      .subnetId(devVpc.getPublicSubnets().iterator().next().getSubnetId())
                                      .instanceType("t3.medium")
                                      .automaticStopTimeMinutes(Duration.ofHours(4).toMinutes())
                                      .build());
    }
}
