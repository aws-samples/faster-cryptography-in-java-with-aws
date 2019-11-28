#!/usr/bin/env bash
set -euf -o pipefail  # Makes bash behave sanely, see https://sipb.mit.edu/doc/safe-shell/

# This script sets up the development environment inside of a Cloud9 instance based on Amazon Linux 2018.03 AMI.

# Install Amazon Corretto
if ! sudo yum list installed | grep "java-1.8.0-amazon-corretto-devel.x86_64"; then
  CORRETTO_8_URL="https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/java-1.8.0-amazon-corretto-devel-1.8.0_232.b09-1.x86_64.rpm"
  CORRETTO_8_PATH="/tmp/java-corretto-8.rpm"

  wget -O "$CORRETTO_8_PATH" "$CORRETTO_8_URL"
  sudo yum install -y "$CORRETTO_8_PATH"

  java -version
else
  echo "Amazon Corretto is already installed, skipping"
fi


# push-image.sh depends on jq
sudo yum install -y jq

# Upgrade Node
nvm install 12
nvm alias default 12
nvm alias stable 12

# Install CDK
npm install -g aws-cdk
cdk --version

# Use instance credentials to access codecommit
git config --global credential.helper '!aws codecommit credential-helper $@'
git config --global credential.UseHttpPath true
