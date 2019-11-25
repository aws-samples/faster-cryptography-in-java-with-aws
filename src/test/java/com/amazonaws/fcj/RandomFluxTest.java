// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazonaws.fcj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.fcj.tags.UnitTest;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

@UnitTest
class RandomFluxTest {
    @ParameterizedTest
    @ValueSource(strings = {"0B", "1B", "1KB", "1023KB", "17MB"})
    void returnsCorrectAmountOfData(final String dataSizeStr) {
        final DataSize dataSize = DataSize.parse(dataSizeStr);
        final AtomicLong byteCount = new AtomicLong(0);
        TestUtils.getRandomFlux(dataSize)
                .subscribe(arr -> byteCount.addAndGet(arr.length));
        assertEquals(dataSize.toBytes(), byteCount.get());
    }

    @Test
    void negativeSize() {
        assertThrows(IllegalArgumentException.class, () -> TestUtils.getRandomFlux(DataSize.ofBytes(-10)));
    }
}
