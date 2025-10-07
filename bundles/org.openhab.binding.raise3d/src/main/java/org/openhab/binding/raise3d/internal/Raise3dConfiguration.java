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
package org.openhab.binding.raise3d.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link Raise3dConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author copilot-swe-agent[bot] - Initial contribution
 */
@NonNullByDefault
public class Raise3dConfiguration {
    public static final String IP_ADDRESS = "ipAddress";
    public static final String PASSWORD = "password";
    public static final String REFRESH_INTERVAL = "refreshInterval";

    public @Nullable String ipAddress;
    public @Nullable String password;
    public int refreshInterval = 30;
}
