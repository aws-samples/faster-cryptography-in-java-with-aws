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
2. Start your Cloud9 IDE [TODO: link to cloudformation]
3. Run the following commands in your Cloud9 terminal to clone this repository
   and set up your Cloud9 environment to work with this project.
```
git clone git@github.com:aws-samples/faster-cryptography-in-java-with-aws.git
cd faster-cryptography-in-java-with-aws
./setup_env.sh
```

### Deploying the sample
The code sample uses [AWS Cloud Development Kit](https://aws.amazon.com/cdk/) to
deploy this code sample as a service running on AWS.

When the sample service is deployed to an AWS account, the particular resources
are always identified and differentiated by the stage parameter. Differentiating
resources by stage allows you to have multiple independent stacks of the sample
service in one AWS account. Whenever you use the `cdk` command, you must also
specify the stage as a context. So instead of running `cdk ls` to list stacks,
you must use `cdk ls --context "stage=beta"`.

To make things simpler, all of the following commands assume the `STAGE` shell
variable has been set. 
```
STAGE="beta"
```

To build the sample service, package it into a container, and push the container to a repository (in our case, Amazon ECR):
```
./push-image $STAGE
```

To deploy the sample service using the latest image in the container repository:
```
cdk deploy fcj-svc --context "stage=$STAGE"
```

Please note: when you push a new image and your infrastructure is already in
place (you have deployed "fcj-svc" stack), you have to manually kill the
currently running AWS Fargate task in your service. A new task will be
automatically started with the new image. This workaround is needed because we
don't have build & deployment automation in place for this workshop.

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
* [Amazon ECS](https://aws.amazon.com/ecs/) is used to run the Docker container
  containing the sample.
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

## License
"Faster Cryptography in Java with AWS" code sample is licensed under Apache 2.0.
