/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.homematic.internal.misc;

/**
 * Exception if something happens in the communication to the Homematic gateway.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class HomematicClientException extends Exception {
    private static final long serialVersionUID = 76348991234346L;

    public HomematicClientException(String message) {
        super(message);
    }

    public HomematicClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
