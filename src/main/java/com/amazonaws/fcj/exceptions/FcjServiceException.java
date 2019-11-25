// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.exceptions;

/**
 * General exception when something goes wrong in our own service code.
 */
public class FcjServiceException extends RuntimeException {
    public FcjServiceException(final String message) {
        super(message);
    }

    public FcjServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public FcjServiceException(final Throwable cause) {
        super(cause);
    }
}
