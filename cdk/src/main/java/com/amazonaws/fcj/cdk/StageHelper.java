// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import static java.lang.String.format;

import java.util.regex.Pattern;

import software.amazon.awscdk.core.Construct;

class StageHelper {
    private static final String STAGE_CTX_KEY = "stage";
    private static final Pattern STAGE_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+$");

    static String getStage(final Construct construct) {
        final String stage = (String) construct.getNode().tryGetContext(STAGE_CTX_KEY);
        if (stage == null) {
            throw new InvalidStageException(format(
                    "\"%s\" context key is not defined, make sure to call cdk with --context \"%s=somestage\"",
                    STAGE_CTX_KEY, STAGE_CTX_KEY));
        }
        if (!STAGE_PATTERN.matcher(stage).matches()) {
            throw new InvalidStageException(format("\"%s\" context key value (\"%s\") does not match pattern \"%s\"",
                                                   STAGE_CTX_KEY, stage, STAGE_PATTERN));
        }
        return stage;
    }
}
