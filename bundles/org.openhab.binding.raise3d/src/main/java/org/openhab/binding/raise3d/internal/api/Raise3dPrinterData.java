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
package org.openhab.binding.raise3d.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link Raise3dPrinterData} class represents the combined data from all API endpoints.
 *
 * @author copilot-swe-agent[bot] - Initial contribution
 */
@NonNullByDefault
public class Raise3dPrinterData {
    // Job information
    @SerializedName("file_name")
    public @Nullable String fileName;

    @SerializedName("job_id")
    public @Nullable String jobId;

    @SerializedName("job_status")
    public @Nullable String jobStatus;

    @SerializedName("print_progress")
    public double printProgress;

    @SerializedName("printed_layer")
    public int printedLayer;

    @SerializedName("printed_time")
    public long printedTime;

    @SerializedName("total_layer")
    public int totalLayer;

    @SerializedName("total_time")
    public long totalTime;

    // System information
    @SerializedName("Serial_number")
    public @Nullable String serialNumber;

    @SerializedName("api_version")
    public @Nullable String apiVersion;

    @SerializedName("battery")
    public int battery;

    @SerializedName("brightness")
    public int brightness;

    @SerializedName("date_time")
    public @Nullable String dateTime;

    @SerializedName("firmware_version")
    public @Nullable String firmwareVersion;

    @SerializedName("language")
    public @Nullable String language;

    @SerializedName("machine_id")
    public @Nullable String machineId;

    @SerializedName("machine_ip")
    public @Nullable String machineIp;

    @SerializedName("machine_name")
    public @Nullable String machineName;

    @SerializedName("model")
    public @Nullable String model;

    @SerializedName("nozzies_num")
    public int nozzlesNum;

    @SerializedName("storage_available")
    public long storageAvailable;

    @SerializedName("update")
    public @Nullable String update;

    @SerializedName("version")
    public @Nullable String version;

    // Running status
    @SerializedName("running_status")
    public @Nullable String runningStatus;

    @SerializedName("fan_cur_speed")
    public int fanCurSpeed;

    @SerializedName("fan_tar_speed")
    public int fanTarSpeed;

    @SerializedName("feed_cur_rate")
    public int feedCurRate;

    @SerializedName("feed_tar_rate")
    public int feedTarRate;

    @SerializedName("heatbed_cur_temp")
    public int heatbedCurTemp;

    @SerializedName("heatbed_tar_temp")
    public int heatbedTarTemp;
}
