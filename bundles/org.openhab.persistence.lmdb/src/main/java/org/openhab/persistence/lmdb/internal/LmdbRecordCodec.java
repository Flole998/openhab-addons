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

import java.nio.ByteBuffer;
import java.util.Date;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.State;

/**
 * Binary record codec for LMDB payload and metadata entries.
 */
class LmdbRecordCodec {

    private static final byte VALUE_FORMAT_VERSION = 1;
    private static final byte META_FORMAT_VERSION = 1;

    private static final long NULL_TIME = Long.MIN_VALUE;

    private LmdbRecordCodec() {
    }

    static int valueEncodedSize(LmdbItem item) {
        int size = 1 + Long.BYTES + Long.BYTES;
        size += BinaryStateCodec.encodedSize(item.getState());
        State lastState = item.getLastState();
        size += 1;
        if (lastState != null) {
            size += BinaryStateCodec.encodedSize(lastState);
        }
        return size;
    }

    static int metadataEncodedSize() {
        return 1 + Long.BYTES + Long.BYTES;
    }

    static void encodeValue(ByteBuffer buffer, LmdbItem item) {
        buffer.put(VALUE_FORMAT_VERSION);
        buffer.putLong(item.getTimestamp().toInstant().toEpochMilli());
        buffer.putLong(item.getLastStateChange() != null ? item.getLastStateChange().toInstant().toEpochMilli() : NULL_TIME);
        BinaryStateCodec.encode(buffer, item.getState());
        State lastState = item.getLastState();
        if (lastState == null) {
            buffer.put((byte) 0);
        } else {
            buffer.put((byte) 1);
            BinaryStateCodec.encode(buffer, lastState);
        }
    }

    static void encodeMetadata(ByteBuffer buffer, LmdbItem item) {
        buffer.put(META_FORMAT_VERSION);
        buffer.putLong(item.getTimestamp().toInstant().toEpochMilli());
        buffer.putLong(item.getLastStateChange() != null ? item.getLastStateChange().toInstant().toEpochMilli() : NULL_TIME);
    }

    static @Nullable LmdbItem decodeValue(String name, ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.get() != VALUE_FORMAT_VERSION) {
            return null;
        }

        if (buffer.remaining() < (Long.BYTES * 2 + 1)) {
            return null;
        }

        LmdbItem item = new LmdbItem();
        item.setName(name);

        long timestamp = buffer.getLong();
        long lastStateChange = buffer.getLong();
        item.setTimestamp(new Date(timestamp));
        item.setLastStateChange(lastStateChange == NULL_TIME ? null : new Date(lastStateChange));

        State state = BinaryStateCodec.decode(buffer);
        if (state == null) {
            return null;
        }
        item.setState(state);

        byte hasLastState = buffer.get();
        if (hasLastState == 1) {
            State decodedLastState = BinaryStateCodec.decode(buffer);
            item.setLastState(decodedLastState);
        } else {
            item.setLastState(null);
        }

        return item.isValid() ? item : null;
    }

    static boolean isValidMetadata(ByteBuffer buffer) {
        if (buffer.remaining() < metadataEncodedSize()) {
            return false;
        }
        return buffer.get() == META_FORMAT_VERSION;
    }
}
