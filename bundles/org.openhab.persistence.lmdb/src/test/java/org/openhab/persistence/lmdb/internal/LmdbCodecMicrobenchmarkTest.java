/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.persistence.lmdb.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import java.util.Date;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.OnOffType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Focused microbenchmarks for codec throughput and read latency.
 */
@NonNullByDefault
class LmdbCodecMicrobenchmarkTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbCodecMicrobenchmarkTest.class);

    @Test
    @Disabled("Manual microbenchmark")
    void benchmarkBinaryCodecRoundtrip() {
        int iterations = 200_000;

        LmdbItem item = new LmdbItem();
        item.setName("bench-item");
        item.setState(OnOffType.ON);
        item.setLastState(OnOffType.OFF);
        item.setTimestamp(new Date(1700000000000L));
        item.setLastStateChange(new Date(1700000001000L));

        int size = LmdbRecordCodec.valueEncodedSize(item);
        ByteBuffer buffer = ByteBuffer.allocate(size);

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            buffer.clear();
            LmdbRecordCodec.encodeValue(buffer, item);
            buffer.flip();
            LmdbItem decoded = LmdbRecordCodec.decodeValue(item.getName(), buffer);
            assertNotNull(decoded);
        }
        long elapsedNanos = System.nanoTime() - start;

        double seconds = elapsedNanos / 1_000_000_000.0;
        double throughput = iterations / seconds;
        double avgMicros = (elapsedNanos / 1000.0) / iterations;

        LOGGER.info("LMDB codec benchmark: iterations={}, throughput={} ops/s, avg={} µs/op", iterations,
                (long) throughput, avgMicros);
    }
}
