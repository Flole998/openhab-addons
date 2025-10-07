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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link Raise3dBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author copilot-swe-agent[bot] - Initial contribution
 */
@NonNullByDefault
public class Raise3dBindingConstants {

    private static final String BINDING_ID = "raise3d";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_PRINTER = new ThingTypeUID(BINDING_ID, "printer");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_PRINTER);

    // Channel IDs - Job Information
    public static final String CHANNEL_FILE_NAME = "fileName";
    public static final String CHANNEL_JOB_ID = "jobId";
    public static final String CHANNEL_JOB_STATUS = "jobStatus";
    public static final String CHANNEL_PRINT_PROGRESS = "printProgress";
    public static final String CHANNEL_PRINTED_LAYER = "printedLayer";
    public static final String CHANNEL_PRINTED_TIME = "printedTime";
    public static final String CHANNEL_TOTAL_LAYER = "totalLayer";
    public static final String CHANNEL_TOTAL_TIME = "totalTime";

    // Channel IDs - System Information
    public static final String CHANNEL_SERIAL_NUMBER = "serialNumber";
    public static final String CHANNEL_API_VERSION = "apiVersion";
    public static final String CHANNEL_BATTERY = "battery";
    public static final String CHANNEL_BRIGHTNESS = "brightness";
    public static final String CHANNEL_DATE_TIME = "dateTime";
    public static final String CHANNEL_FIRMWARE_VERSION = "firmwareVersion";
    public static final String CHANNEL_LANGUAGE = "language";
    public static final String CHANNEL_MACHINE_ID = "machineId";
    public static final String CHANNEL_MACHINE_IP = "machineIp";
    public static final String CHANNEL_MACHINE_NAME = "machineName";
    public static final String CHANNEL_MODEL = "model";
    public static final String CHANNEL_NOZZLES_NUM = "nozzlesNum";
    public static final String CHANNEL_STORAGE_AVAILABLE = "storageAvailable";
    public static final String CHANNEL_UPDATE = "update";
    public static final String CHANNEL_VERSION = "version";

    // Channel IDs - Running Status
    public static final String CHANNEL_RUNNING_STATUS = "runningStatus";
    public static final String CHANNEL_FAN_CUR_SPEED = "fanCurSpeed";
    public static final String CHANNEL_FAN_TAR_SPEED = "fanTarSpeed";
    public static final String CHANNEL_FEED_CUR_RATE = "feedCurRate";
    public static final String CHANNEL_FEED_TAR_RATE = "feedTarRate";
    public static final String CHANNEL_HEATBED_CUR_TEMP = "heatbedCurTemp";
    public static final String CHANNEL_HEATBED_TAR_TEMP = "heatbedTarTemp";
}
