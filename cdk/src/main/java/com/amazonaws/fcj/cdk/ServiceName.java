// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

import com.google.common.base.CaseFormat;

import software.amazon.awscdk.core.Construct;

public class ServiceName {
    /**
     * This method should be the only way to get the name of our service. It forces the caller to think about and
     * specify what case format the base name should.
     *
     * @param caseFormat The case format of the base name.
     * @return Base name of this service.
     */
    static String getWithStage(final CaseFormat caseFormat, final Construct construct) {
        final String baseName = "faster-cryptography-in-java-" + StageHelper.getStage(construct);
        final CaseFormat baseNameCaseFormat = CaseFormat.LOWER_HYPHEN;
        if (caseFormat.equals(baseNameCaseFormat)) {
            return baseName;
        }
        return baseNameCaseFormat.to(caseFormat, baseName);
    }
}
