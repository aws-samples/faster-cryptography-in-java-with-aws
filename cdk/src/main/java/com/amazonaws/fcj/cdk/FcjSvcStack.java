// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import static java.lang.String.format;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.text.MessageFormat;
import java.util.Map;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.cloudwatch.Dashboard;
import software.amazon.awscdk.services.cloudwatch.DashboardProps;
import software.amazon.awscdk.services.cloudwatch.GraphWidget;
import software.amazon.awscdk.services.cloudwatch.GraphWidgetProps;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.MetricProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.iam.AccountRootPrincipal;
import software.amazon.awscdk.services.iam.CompositePrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyDocumentProps;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.kms.KeyProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketProps;

/**
 * This is a stack for the FCJ service itself.
 */
public class FcjSvcStack extends Stack {
    private static final Duration ECS_TASK_ROLE_MAX_SESSION_DURATION = Duration.hours(8);

    private static final int ECS_TASK_CPU_UNITS = 2048; // 2 vCPU
    private static final int ECS_TASK_MEMORY_LIMIT_MB = 4096;
    private static final String ECS_TASK_JVM_MAX_HEAP_SIZE = "-Xmx3g";

    private final String svcNameWithStage;

    public FcjSvcStack(final Construct parent, final String id) {
        super(parent, id, StackProps.builder()
                .stackName(ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, parent) + "-svc-stack")
                .description("Main service stack for " + ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, parent))
                .build());
        svcNameWithStage = ServiceName.getWithStage(CaseFormat.LOWER_HYPHEN, this);

        final Bucket mainStorageBucket = createMainStorageBucket();
        final Role ecsTaskRole = createEcsTaskRole();
        final Key kmsKey = createKmsKey();

        kmsKey.grant(ecsTaskRole, "kms:GenerateDataKey*", "kms:Decrypt", "kms:List*", "kms:Describe*").assertSuccess();
        mainStorageBucket.grantReadWrite(ecsTaskRole).assertSuccess();

        final IRepository ecrRepo = Repository.fromRepositoryName(
                this, "EcrRepo", "com.amazonaws.fcj/" + svcNameWithStage);

        final Vpc ecsTaskVpc = new Vpc(this, "EcsTaskVpc", VpcProps.builder().build());

        final Cluster ecsCluster = new Cluster(this, "EcsCluster", ClusterProps.builder()
                .clusterName(svcNameWithStage)
                .vpc(ecsTaskVpc)
                .build());

        // These two links are helpful to understanding what's happening inside the following ECS Pattern Construct:
        // https://github.com/aws/aws-cdk/blob/master/packages/%40aws-cdk/aws-ecs-patterns/lib/fargate/application-load-balanced-fargate-service.ts#L91
        // https://github.com/aws/aws-cdk/blob/master/packages/%40aws-cdk/aws-ecs-patterns/lib/base/application-load-balanced-service-base.ts#L262
        final ApplicationLoadBalancedFargateService lbFargateSvc =
                new ApplicationLoadBalancedFargateService(
                        this,
                        "FargateSvc",
                        ApplicationLoadBalancedFargateServiceProps.builder()
                                .cluster(ecsCluster)
                                .serviceName(svcNameWithStage)
                                .cpu(ECS_TASK_CPU_UNITS)
                                .memoryLimitMiB(ECS_TASK_MEMORY_LIMIT_MB)
                                .publicLoadBalancer(true)
                                .taskImageOptions(
                                        ApplicationLoadBalancedTaskImageOptions.builder()
                                                .image(ContainerImage.fromEcrRepository(ecrRepo, "latest"))
                                                .enableLogging(true)
                                                .taskRole(ecsTaskRole)
                                                .family(svcNameWithStage)
                                                .containerName(svcNameWithStage + "-container")
                                                .containerPort(8080)
                                                .environment(ImmutableMap.of(
                                                        "SPRING_PROFILES_ACTIVE", StageHelper.getStage(this),
                                                        "JVM_MAX_HEAP_SIZE", ECS_TASK_JVM_MAX_HEAP_SIZE))
                                                .build())
                                .build());

        lbFargateSvc.getTargetGroup().setHealthCheck(
                HealthCheck.builder()
                        .protocol(Protocol.HTTP)
                        .path("/actuator/health")
                        .healthyThresholdCount(3)
                        .unhealthyThresholdCount(2)
                        .interval(Duration.seconds(10))
                        .healthyHttpCodes("200")
                        .build());

        final String ecsConsoleUrl = MessageFormat.format(
                "https://{0}.console.aws.amazon.com/ecs/home?region={0}#/clusters/{1}/services",
                getRegion(), lbFargateSvc.getService().getServiceName());
        new CfnOutput(this, "EcsConsoleUrl", CfnOutputProps.builder()
                .value(ecsConsoleUrl)
                .description("AWS Console URL for the Fargate cluster hosting our sample service")
                .build());

        createDashboard();
    }

    private Bucket createMainStorageBucket() {
        final String bucketName = Joiner.on("-").join(svcNameWithStage, getAccount(), getRegion());
        return new Bucket(this, "MainStorageBucket", BucketProps.builder()
                .bucketName(bucketName)
                .publicReadAccess(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build());
    }

    private Role createEcsTaskRole() {
        return new Role(this, "FcjEcsTaskRole", RoleProps.builder()
                .assumedBy(new CompositePrincipal(new ServicePrincipal("ecs-tasks.amazonaws.com"),
                                                  new AccountRootPrincipal()))
                .maxSessionDuration(ECS_TASK_ROLE_MAX_SESSION_DURATION)
                .inlinePolicies(ImmutableMap.of("basic", createEcsTaskRoleBasicPolicy()))
                .roleName(format("%s-role", svcNameWithStage))
                .build());
    }

    private PolicyDocument createEcsTaskRoleBasicPolicy() {
        return new PolicyDocument(
                PolicyDocumentProps.builder()
                        .assignSids(true)
                        .statements(ImmutableList.of(
                                PolicyStatement.Builder.create()
                                        .effect(Effect.ALLOW)
                                        .actions(ImmutableList.of("cloudwatch:PutMetricData"))
                                        .conditions(ImmutableMap.of(
                                                "StringEquals",
                                                ImmutableMap.of("cloudwatch:namespace",
                                                                svcNameWithStage)))
                                        .resources(ImmutableList.of("*"))
                                        .build(),
                                PolicyStatement.Builder.create()
                                        .effect(Effect.ALLOW)
                                        .actions(ImmutableList.of("sts:GetCallerIdentity",
                                                                  "kms:ListAliases"))
                                        .resources(ImmutableList.of("*"))
                                        .build()))
                        .build());
    }

    private Key createKmsKey() {
        // The following policy allows the key administrator (account root) to manage, inspect, and delete the key
        // but it doesn't allow them to use the key (no Encrypt, Decrypt, GenerateDataKey, etc). Note that key
        // administrators can simply call PutKeyPolicy to also give themselves permission to use the key.
        final PolicyDocument defaultKeyPolicy = PolicyDocument.Builder.create()
                .assignSids(true)
                .statements(ImmutableList.of(new PolicyStatement(
                        PolicyStatementProps.builder()
                                .effect(Effect.ALLOW)
                                .resources(ImmutableList.of("*"))
                                .principals(ImmutableList.of(new AccountRootPrincipal()))
                                .actions(ImmutableList.of("kms:ScheduleKeyDeletion",
                                                          "kms:CancelKeyDeletion",
                                                          "kms:Create*",
                                                          "kms:RevokeGrant",
                                                          "kms:RetireGrant",
                                                          "kms:List*",
                                                          "kms:Get*",
                                                          "kms:Describe*",
                                                          "kms:Delete*",
                                                          "kms:PutKeyPolicy"))
                                .resources(ImmutableList.of("*"))
                                .build())))
                .build();
        return new Key(this, "MasterFileStorageKey", KeyProps.builder()
                .alias(svcNameWithStage)
                .description("KMS key to encrypt data stored in the main storage bucket")
                .policy(defaultKeyPolicy)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build());
    }

    /**
     * Creates a very simple CloudWatch dashboard.
     */
    private void createDashboard() {
        final Dashboard fcjDash = new Dashboard(this, "FcjDash", DashboardProps.builder()
                .dashboardName(svcNameWithStage)
                .build());

        final GraphWidget widget = new GraphWidget(
                GraphWidgetProps.builder()
                        .width(16)
                        .height(10)
                        .stacked(true)
                        .left(ImmutableList.of(createMetric("encrypt.duration.perMb")))
                        .build());

        fcjDash.addWidgets(widget);

        final String dashboardConsoleUrl = MessageFormat.format(
                "https://{0}.console.aws.amazon.com/cloudwatch/home?region={0}#dashboards:name={1}",
                getRegion(), svcNameWithStage);
        new CfnOutput(this, "FcjDashboard", CfnOutputProps.builder().value(dashboardConsoleUrl).build());
    }

    private Metric createMetric(final String metricName) {
        final Map<String, Object> dimensions = ImmutableMap.of(
                "Stage", StageHelper.getStage(this),
                "Region", getRegion());

        return new Metric(
                MetricProps.builder()
                        .metricName(metricName)
                        .statistic("p99")
                        .period(Duration.minutes(1))
                        .namespace(svcNameWithStage)
                        .dimensions(dimensions)
                        .build());
    }
}
