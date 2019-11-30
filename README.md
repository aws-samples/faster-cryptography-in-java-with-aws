# Faster Cryptography in Java with AWS

## Overview
This *code sample* shows how to use [Amazon Corretto Crypto
Provider](https://github.com/corretto/amazon-corretto-crypto-provider) and [AWS
Encryption SDK for Java](https://github.com/aws/aws-encryption-sdk-java) to
speed up cryptographic operations in a simple REST service. The sample service
acts as an encrypting proxy for Amazon S3. You can stream a large file up to
Amazon S3 maximum object size limit using `HTTP POST /file`. The service will
client-side encrypt your stream on the fly using AWS Encryption SDK for Java and
forwards the encrypted stream to Amazon S3. When the file is uploaded, the
sample service will respond with a unique ID. The ID can be used to retrieve the
file later using `HTTP GET /file/{id}`. The service will cache only small chunks
of the streamed content during the upload to ensure the service can run with a
relatively small amount of memory.

The service emits various metrics to Amazon CloudWatch. Notably, it emits how
much CPU time was spent encrypting/decrypting a unit of data so that we can
measure how much Amazon Corretto Crypto Provider speeds up our service.

## Instructions
### Dev Environment Setup
1. Log in to your AWS account.
1. Create your Cloud9 IDE. You can do it manully or click
   [here](https://us-west-2.console.aws.amazon.com/cloudformation/home?region=us-west-2#/stacks/create/review?templateURL=https://faster-cryptography-in-java-with-aws.s3-us-west-2.amazonaws.com/cf-templates/fcj-dev-env.template.json&stackName=Dev-Env-for-FCJ-in-Java-with-AWS)
   to use a prepared AWS CloudFormation template.
1. If you don't have it open already, open the [Cloud9
   console](https://us-west-2.console.aws.amazon.com/cloud9/home?region=us-west-2#)
   and launch your Cloud9 IDE.
1. Run the following commands in the terminal of your Cloud9 IDE to clone this
   repository and set up your environment to work with this project.
```
git clone https://github.com/aws-samples/faster-cryptography-in-java-with-aws.git
cd faster-cryptography-in-java-with-aws
./setup_env.sh
```

### Building the Sample Code
The code sample uses [AWS Cloud Development Kit](https://aws.amazon.com/cdk/) to
deploy this code sample as a service running on AWS.

When the sample service is deployed to an AWS account, the names of its
resources (e.g., AWS Fargate cluster) always include the stage parameter. This
helps differentiate them from resources belonging to another instance.
Differentiating resources by stage allows you to have multiple independent
stacks of the sample service in one AWS account. Whenever you use the `cdk`
command, you must also specify the stage as a context. So instead of running
`cdk ls` to list stacks, you must use `cdk ls --context "stage=alpha"`.

To make things simpler, all of the following commands assume the `STAGE`
environment variable has been set in your shell.
```
STAGE="alpha"
```

The following commands deploy the build stack and push the code to the stack's
CodeCommit repository. The build stack is driven by a pipeline (AWS
CodePipeline) that takes the source code, builds a container, and pushes the
container to a container registry (Amazon ECR).
```
cd cdk
cdk deploy fcj-build --context "stage=$STAGE"
# CloudFormation output "FcjSourceRepoCloneUrlHttp" contains the CodeCommit URL
# to use as remote in the following command.
git remote add fcj-$STAGE <FILL IN codecommit remote url from CF output>
git push fcj-$STAGE
```

There are two more CloudFormation outputs you should explore at this point:
1. Pipeline console URL shows you the pipeline and how the build process is
   coming along.
2. Container registry (Amazon ECR) console URL shows you containers in your
   registry.

### Deploying the Sample Code
When your pipeline finishes pushing the container to the registry, you are ready
to deploy the service stack. Service stack is responsible taking the container
from the container registry, running it using AWS Fargate, and making it
available on the network through a load balancer.
```
# Make sure you are still in the cdk directory
cdk deploy fcj-svc --context "stage=$STAGE"
```

AWS Fargate will automatically use the latest container in the registry when
starting a new task. However, AWS Fargate will not automatically replace
currently running tasks when a new container is pushed into the container
registry. The solution to this problem is to add AWS CodeDeploy with Blue/Green
Deployments to the end of your pipeline. Unfortunately, at the time of writing
this is not yet supported in AWS CDK.

For the purposes of our workshop, we'll simply poke AWS Fargate using AWS CLI
and force a new deployment:
```
aws ecs update-service --cluster faster-cryptography-in-java-$STAGE --service faster-cryptography-in-java-$STAGE --force-new-deployment
```

### Measuring Performance
Performance test can be started by invoking Gradle task `perfTest` (see below).
The tests are configured using environment variables (with some sensible
defaults).
```
# Make sure you're in the project root directory.
export FCJ_TEST_BASE_URL="http://your-lb.amazonaws.com/"
FCJ_TEST_FILE_COUNT=10 FCJ_TEST_FILE_SIZE=32MB ./gradlew perfTest
```

When the performance test has run its course, check out your CloudWatch metrics.
The sample service emits two metrics called `encrypt.duration.perMb` and
`decrypt.duration.perMb`.

You can use the following link to CloudWatch console if your stack runs in
us-west-2 and your stage is "alpha":
```
https://us-west-2.console.aws.amazon.com/cloudwatch/home?region=us-west-2#metricsV2:graph=~(view~'timeSeries~stacked~false~metrics~(~(~'faster-cryptography-in-java-alpha~'encrypt.duration.perMb~'Stage~'alpha~'Region~'us-west-2))~region~'us-west-2);query=~'*7bfaster-cryptography-in-java-alpha*2cRegion*2cStage*7d
```

### Enabling Amazon Corretto Crypto Provider
Now that we have the ability to measure performance of our system, we can enable
ACCP and see what difference it's going to make.

Open file
[FcjServiceConfig](https://github.com/aws-samples/faster-cryptography-in-java-with-aws/blob/master/src/main/java/com/amazonaws/fcj/FcjServiceConfig.java#L56)
and find a bean called "enableAccp". Uncomment it to enable ACCP. Deploy the
sample again (see above), measure performance again, and see if it made any
difference!

## Under the Hood
* [Amazon Corretto Crypto
  Provider](https://github.com/corretto/amazon-corretto-crypto-provider) speeds
  up common cryptographic algorithms such as AES-GCM.
* [AWS Encryption SDK for Java](https://github.com/aws/aws-encryption-sdk-java)
  defines the ciphertext format, manages encryption keys through KMS, and
  interfaces with Amazon Corretto Crypto Provider through Java Cryptographic
  Architecture interfaces
* [Amazon Corretto](https://aws.amazon.com/corretto/) is the OpenJDK
  distribution the code sample runs on.
* [AWS Key Management Service](https://aws.amazon.com/kms/) is the key
  management service that protects the entire hierarchy of encryption keys in
  the system.
* [Amazon CloudWatch](https://aws.amazon.com/cloudwatch/) aggregates metrics
  submitted by the service and provides insight into its performance.
* [Amazon ECS](https://aws.amazon.com/ecs/) and [AWS
  Fargate](https://aws.amazon.com/fargate/) are used to run the container
  containing the sample.
* [AWS CodeCommit](https://aws.amazon.com/codecommit/), [AWS
  CodeBuild](https://aws.amazon.com/codebuild/), and [AWS
  CodePipeline](https://aws.amazon.com/codepipeline/) are used together to build
  the source code, package the result into a container, and push it into the
  container registry ([Amazon Elastic Container
  Registry](https://aws.amazon.com/ecr/)).
* [Project Reactor](https://projectreactor.io/) makes it easy to use reactive
  programming in Java. Reactive applications "react" to changes such as I/O
  events without actively waiting (blocking) a thread until a change happens. It
  implements [Reactive Streams](https://www.reactive-streams.org/) for wide
  compatibility with other libraries.
* [Spring
  WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html)
  is a fully non-blocking modern web framework. The service uses Spring WebFlux
  to execute its business logic in response to client requests.
* [Docker](https://www.docker.com/) takes care of the environment the service
  runs in.

## Caveats
This sample is NOT a production-ready service that can be deployed anywhere
as-is and do something useful in a secure manner. It's a *demo application*
showcasing use of certain technologies listed above.

The deployed sample service does not offer any transport security (TLS) for
traffic between the client and the service. The service also does not perform
any authentication or authorization of calls to its front-end API. Anyone can
upload and download anything. Anyone listening on the network between the client
and the service can see data in plaintext. This is intentional to keep things
simple. The objective is to showcase other parts of the stack. On the other
hand, all network traffic between the service and AWS KMS, Amazon S3, and Amazon
CloudWatch is encrypted using TLS.

## Testing
* Unit, integration, and performance tests are located in
  `src/test/java/com/amazonaws/fcj/` and annotated with `@UnitTest`,
  `@IntegTest`, and `@PerfTest`. Corresponding Gradle tasks to run these tests
  are `test`, `integTest`, and `perfTest`.
* Curl can be used to test the service as well:
 * Upload a file: `curl --verbose -H "Content-Type: application/octet-stream"
   --data-binary @myfile http://localhost:8080/file`
 * Download a file: `curl --verbose -o /local/path/to/my-file
   http://localhost:8080/file/my-file-id`

## What's a Reactive System?
Modern Java libraries such as [AWS SDK for Java
2.0](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/welcome.html)
incorporate support for *reactive* processing. In reactive systems, subscribers
(consumers of data) drive data processing by notifying producers (using an
event) they are ready to receive a certain amount of data. A reactive producer
reacts to requests from a subscriber. When there is demand and the producer has
something to produce, it sends the data as an event. The subscriber then reacts
to the response. Producers and subscribers never have to wait for each other and
block the thread they are running on. Instead, control is handed to them by a
*scheduler* in response to an event.

Contrast this with a typical procedural model where the producer of information
does the computation, passes it to the consumer (say a method call), and waits
until the consumer can produce a response.

In practical terms, reactive systems allow us to write services where a thread
never blocks waiting for a response. Processing is defined in small chunks which
are executed when the prerequisite data (e.g., a response to a remote network
call) is available.

All of this allows us to build scalable systems that stay responsive under high
load, are resilient to failure, and respond to customer requests with lower
latency.

To learn more, take a look at [Reactive
Manifesto](https://www.reactivemanifesto.org/) and [Reactive
Streams](https://www.reactive-streams.org/).

The sample service uses reactive components extensively, in particular the
S3FileStore class. Understanding reactive systems is by no means necessary for
understanding ACCP. It's just bonus content.

## License
"Faster Cryptography in Java with AWS" code sample is licensed under Apache 2.0.
