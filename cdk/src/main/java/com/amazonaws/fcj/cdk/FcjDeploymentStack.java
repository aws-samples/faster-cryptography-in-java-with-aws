// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;

/**
 * This stack is responsible for things associated with deploying FCJ service. In particular, creating a container
 * registry.
 */
public class FcjDeploymentStack extends Stack {
    public FcjDeploymentStack(final Construct scope, final String id) {
        super(scope, id);

        final Repository fcjContainerRepo = new Repository(
                this, "FcjContainerRepo",
                RepositoryProps.builder()
                        .repositoryName("com.amazonaws.fcj/" + ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, this))
                        .lifecycleRules(ImmutableList.of(
                                LifecycleRule.builder().maxImageAge(Duration.days(7)).build()))
                        .build());
    }
}
