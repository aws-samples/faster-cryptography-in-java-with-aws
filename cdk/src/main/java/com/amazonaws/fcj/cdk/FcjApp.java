// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import software.amazon.awscdk.core.App;

public class FcjApp {
    public static void main(final String[] argv) {
        App app = new App();

        // This is basically just Cloud9 developer environment.
        new FcjDevEnvStack(app, "fcj-dev-env");

        // Build stack is responsible for taking the source code in CodeCommit, building a container out of it, and
        // pushing said container to container repository.
        new FcjBuildStack(app, "fcj-build");

        // Service stack is responsible for running the container from the container repository.
        new FcjSvcStack(app, "fcj-svc");

        // required until https://github.com/aws/jsii/issues/456 is resolved
        app.synth();
    }
}
