// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import software.amazon.awscdk.core.App;

public class FcjApp {
    public static void main(final String[] argv) {
        App app = new App();

        new FcjSvcStack(app, "fcj-svc");
        new FcjDeploymentStack(app, "fcj-deployment-infra");
        new FcjDevEnvStack(app, "fcj-dev-env");

        // required until https://github.com/aws/jsii/issues/456 is resolved
        app.synth();
    }
}
