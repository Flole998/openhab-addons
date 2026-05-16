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

import java.util.Date;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.persistence.PersistenceItemInfo;

class LmdbItemInfo implements PersistenceItemInfo {

    private final String name;

    LmdbItemInfo(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable Integer getCount() {
        return Integer.valueOf(1);
    }

    @Override
    public @Nullable Date getEarliest() {
        return null;
    }

    @Override
    public @Nullable Date getLatest() {
        return null;
    }
}
