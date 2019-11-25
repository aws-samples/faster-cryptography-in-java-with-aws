// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import java.time.Duration;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.cloud9.CfnEnvironmentEC2;
import software.amazon.awscdk.services.cloud9.CfnEnvironmentEC2Props;
import software.amazon.awscdk.services.ec2.Vpc;

public class FcjDevEnvStack extends Stack {
    public FcjDevEnvStack(final Construct scope, final String id) {
        super(scope, id);

        final Vpc devVpc = new Vpc(this, "dev-vpc");

        new CfnEnvironmentEC2(this, "fcj-c9-ide",
                              CfnEnvironmentEC2Props.builder()
                                      .name("Faster Cryptography in Java - Cloud9 IDE")
                                      .subnetId(devVpc.getPublicSubnets().iterator().next().getSubnetId())
                                      .instanceType("t3.medium")
                                      .automaticStopTimeMinutes(Duration.ofHours(4).toMinutes())
                                      .build());
    }
}
