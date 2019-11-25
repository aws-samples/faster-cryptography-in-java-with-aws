// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj.exceptions;

/**
 * Thrown when the file ID is invalid.
 */
public class InvalidIdException extends FcjServiceException {
    public InvalidIdException(final String message) {
        super(message);
    }

    public InvalidIdException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public InvalidIdException(final Throwable cause) {
        super(cause);
    }
}
