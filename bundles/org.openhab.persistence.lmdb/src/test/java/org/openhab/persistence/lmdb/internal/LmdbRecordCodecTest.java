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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import java.util.Date;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;

@NonNullByDefault
class LmdbRecordCodecTest {

    @Test
    void valueRoundtripShouldRecreateItem() {
        LmdbItem item = new LmdbItem();
        item.setName("myItem");
        item.setState(OnOffType.ON);
        item.setTimestamp(new Date(1700000000000L));
        item.setLastState(PercentType.valueOf("10"));
        item.setLastStateChange(new Date(1700000001000L));

        ByteBuffer encoded = ByteBuffer.allocate(LmdbRecordCodec.valueEncodedSize(item));
        LmdbRecordCodec.encodeValue(encoded, item);
        encoded.flip();

        LmdbItem decoded = LmdbRecordCodec.decodeValue("myItem", encoded);
        assertNotNull(decoded);
        assertThat(decoded.getName(), is(equalTo(item.getName())));
        assertThat(decoded.getState(), is(equalTo(item.getState())));
        assertThat(decoded.getTimestamp(), is(equalTo(item.getTimestamp())));
        assertThat(decoded.getLastState(), is(equalTo(item.getLastState())));
        assertThat(decoded.getLastStateChange(), is(equalTo(item.getLastStateChange())));
    }

    @Test
    void metadataEncodingShouldMatchValidation() {
        LmdbItem item = new LmdbItem();
        item.setName("myItem");
        item.setState(OnOffType.OFF);
        item.setTimestamp(new Date(1700000000000L));

        ByteBuffer encoded = ByteBuffer.allocate(LmdbRecordCodec.metadataEncodedSize());
        LmdbRecordCodec.encodeMetadata(encoded, item);
        encoded.flip();

        assertThat(LmdbRecordCodec.isValidMetadata(encoded), is(true));
    }
}
