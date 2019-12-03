// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.text.MessageFormat;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.CodeCommitSourceProps;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.Project;
import software.amazon.awscdk.services.codebuild.ProjectProps;
import software.amazon.awscdk.services.codebuild.Source;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.PipelineProps;
import software.amazon.awscdk.services.codepipeline.StageOptions;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildActionProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceActionProps;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;

/**
 * This stack is responsible for building FCJ service and pushing the resulting container to container registry (Amazon
 * ECR).
 */
public class FcjBuildStack extends Stack {

    private static final Duration MAX_IMAGE_AGE = Duration.days(7);

    public FcjBuildStack(final Construct scope, final String id) {
        super(scope, id, StackProps.builder()
                .stackName(ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, scope) + "-build-stack")
                .description("Builds source into a container and pushes the container to ECR")
                .build());
        final String svcName = ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, this);

        final software.amazon.awscdk.services.codecommit.Repository fcjSourceRepo =
                new software.amazon.awscdk.services.codecommit.Repository(
                        this,
                        "FcjSourceRepo",
                        software.amazon.awscdk.services.codecommit.RepositoryProps.builder()
                                .description("Source repository for " + svcName)
                                .repositoryName(svcName)
                                .build());

        new CfnOutput(this, "FcjSourceRepoCloneUrlHttp", CfnOutputProps.builder()
                .value(fcjSourceRepo.getRepositoryCloneUrlHttp())
                .build());

        final Repository fcjContainerRepo = new Repository(
                this, "FcjContainerRepo",
                RepositoryProps.builder()
                        .repositoryName("com.amazonaws.fcj/" + ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, this))
                        .lifecycleRules(ImmutableList.of(
                                LifecycleRule.builder()
                                        .maxImageAge(MAX_IMAGE_AGE)
                                        .build()))
                        .build());

        final String containerRepoConsoleUrl = MessageFormat.format(
                "https://{0}.console.aws.amazon.com/ecr/repositories/{1}/?region={0}",
                getRegion(), fcjContainerRepo.getRepositoryName());
        new CfnOutput(this, "FcjEcrConsoleUrl", CfnOutputProps.builder()
                .value(containerRepoConsoleUrl)
                .build());


        final Project fcjBuildProject = new Project(this, "fcjBuildProject", ProjectProps.builder()
                .projectName(svcName)
                .environment(BuildEnvironment.builder()
                                     .buildImage(LinuxBuildImage.AMAZON_LINUX_2)
                                     .privileged(true)
                                     .build())
                .source(Source.codeCommit(CodeCommitSourceProps.builder().repository(fcjSourceRepo).build()))
                .environmentVariables(ImmutableMap.of(
                        "IMAGE_NAME",
                        BuildEnvironmentVariable.builder().value("com.amazonaws.fcj/faster-cryptography-in-java").build(),
                        "REPO_URI",
                        BuildEnvironmentVariable.builder().value(fcjContainerRepo.getRepositoryUri()).build(),
                        "IMAGE_TAG",
                        BuildEnvironmentVariable.builder().value("latest").build()
                ))
                .build());

        fcjContainerRepo.grantPullPush(fcjBuildProject);

        final Pipeline fcjPipeline = new Pipeline(this, "FcjPipeline", PipelineProps.builder()
                .pipelineName(svcName)
                .build());

        final String pipelineConsoleUrl = MessageFormat.format(
                "https://{0}.console.aws.amazon.com/codesuite/codepipeline/pipelines/{1}/view?region={0}",
                getRegion(), fcjPipeline.getPipelineName());
        new CfnOutput(this, "FcjPipelineUrl", CfnOutputProps.builder()
                .value(pipelineConsoleUrl)
                .build());

        final Artifact sourceArtifact = Artifact.artifact(svcName + "-source");

        fcjPipeline.addStage(StageOptions.builder()
                                     .stageName("Source")
                                     .actions(ImmutableList.of(new CodeCommitSourceAction(
                                             CodeCommitSourceActionProps.builder()
                                                     .actionName("Source")
                                                     .repository(fcjSourceRepo)
                                                     .branch("master")
                                                     .output(sourceArtifact)
                                                     .build())))
                                     .build());

        fcjPipeline.addStage(StageOptions.builder()
                                     .stageName("BuildPushImage")
                                     .actions(ImmutableList.of(new CodeBuildAction(
                                             CodeBuildActionProps.builder()
                                                     .actionName("Build")
                                                     .project(fcjBuildProject)
                                                     .input(sourceArtifact)
                                                     .build())))
                                     .build());
    }
}
