// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.cdk;

public class InvalidStageException extends RuntimeException {
    public InvalidStageException(final String message) {
        super(message);
    }
}
