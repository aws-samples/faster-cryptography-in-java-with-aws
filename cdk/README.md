## Overview
This sub-project uses AWS Cloud Development Kit to create resources necessary to run the service in an AWS account.

The CDK app creates the following resources:

* Amazon S3 bucket to store the encrypted files.
* AWS KMS master key to generate and protect data keys that encrypt the files.
* ECS Fargate cluster, task, and container to run the Docker image of the service.
* IAM Role the Fargate task uses to access other AWS resources like the bucket storing the files.
* Networking structures like a VPC, an AWS Application Load Balancer including its listener and target group.

Note, the CDK app deploys an existing image that lives in your [Amazon Elastic Container Registry](https://aws.amazon.com/ecr/). It does not build the image and such. That's a separate process captured in TODO.

## Usage
Make sure you have [AWS CDK installed](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html#getting_started_install).

To deploy the service:
```plain
cdk deploy --context="stage=desktop"
```
The stage parameter should be a lowercase string with ASCII letters numbers or a dash.

When you make a change you use `cdk diff --context="stage=desktop"` to see the difference between what you would deploy and what actually exists in your account.
