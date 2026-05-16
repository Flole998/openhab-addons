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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.State;
import org.openhab.core.types.TypeParser;

/**
 * Compact binary state codec based on pre-registered state classes.
 */
class BinaryStateCodec {

    @SuppressWarnings("unchecked")
    private static final Class<? extends State>[] ID_TO_TYPE = new Class[] { DecimalType.class, HSBType.class,
            OnOffType.class, PercentType.class, QuantityType.class, StringType.class, DateTimeType.class,
            OpenClosedType.class, UpDownType.class, StopMoveType.class, PlayPauseType.class, NextPreviousType.class,
            IncreaseDecreaseType.class, RewindFastforwardType.class, RawType.class };

    private static final Map<Class<? extends State>, Integer> TYPE_TO_ID = java.util.stream.IntStream
            .range(0, ID_TO_TYPE.length).boxed().collect(java.util.stream.Collectors
                    .toUnmodifiableMap(index -> ID_TO_TYPE[index], java.util.function.Function.identity()));

    private BinaryStateCodec() {
    }

    static int encodedSize(State state) {
        byte[] valueBytes = state.toFullString().getBytes(StandardCharsets.UTF_8);
        return 1 + 4 + valueBytes.length;
    }

    static void encode(ByteBuffer buffer, State state) {
        Integer typeId = TYPE_TO_ID.get(state.getClass());
        if (typeId == null || typeId > 255) {
            throw new IllegalArgumentException("Unsupported state type for LMDB codec: " + state.getClass().getName());
        }

        byte[] valueBytes = state.toFullString().getBytes(StandardCharsets.UTF_8);
        buffer.put((byte) (typeId & 0xFF));
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
    }

    static @Nullable State decode(ByteBuffer buffer) {
        int typeId = Byte.toUnsignedInt(buffer.get());
        if (typeId >= ID_TO_TYPE.length) {
            return null;
        }

        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            return null;
        }

        byte[] valueBytes = new byte[length];
        buffer.get(valueBytes);
        String valueAsString = new String(valueBytes, StandardCharsets.UTF_8);

        Class<? extends State> valueType = ID_TO_TYPE[typeId];
        Optional<State> parsed = Optional.ofNullable(TypeParser.parseState(List.of(valueType), valueAsString));
        return parsed.orElse(null);
    }
}
