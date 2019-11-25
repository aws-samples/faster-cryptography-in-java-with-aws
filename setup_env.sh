#!/usr/bin/env bash
set -euf -o pipefail  # Makes bash behave sanely, see https://sipb.mit.edu/doc/safe-shell/

# This script sets up the development environment inside of a Cloud9 instance based on Amazon Linux 2018.03 AMI.

########
# Java #
########

if ! sudo yum list installed | grep "java-1.8.0-amazon-corretto-devel.x86_64"; then
  CORRETTO_8_URL="https://d3pxv6yz143wms.cloudfront.net/8.232.09.1/java-1.8.0-amazon-corretto-devel-1.8.0_232.b09-1.x86_64.rpm"
  CORRETTO_8_PATH="/tmp/java-corretto-8.rpm"

  wget -O "$CORRETTO_8_PATH" "$CORRETTO_8_URL"
  sudo yum install -y "$CORRETTO_8_PATH"

  java -version
else
  echo "Amazon Corretto is already installed, skipping"
fi


##########
# Gradle #
##########

GRADLE_INSTALL_DIR="$HOME/.gradle"
GRADLE_VERSION="6.0.1"
#command -v gradle
if ! command -v gradle && [ ! -e "$GRADLE_INSTALL_DIR" ]; then
  # If gradle is not installed, download and install it.

  GRADLE_DOWNLOAD_PATH="/tmp/gradle-$GRADLE_VERSION-bin.zip"
  wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$GRADLE_DOWNLOAD_PATH"

  mkdir -p "$GRADLE_INSTALL_DIR"
  unzip -d "$GRADLE_INSTALL_DIR" "$GRADLE_DOWNLOAD_PATH"
  rm "$GRADLE_DOWNLOAD_PATH"

  GRADLE_VERSION_DIR="$GRADLE_INSTALL_DIR/gradle-$GRADLE_VERSION/bin"
  echo "export PATH=\"\$PATH:$GRADLE_VERSION_DIR\"" >> ~/.bashrc
  export PATH="$PATH:$GRADLE_VERSION_DIR"
  gradle --version
else
  echo "Gradle is already installed, skipping"
fi
