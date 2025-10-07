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

import static org.openhab.binding.raise3d.internal.Raise3dBindingConstants.*;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.raise3d.internal.api.Raise3dApiClient;
import org.openhab.binding.raise3d.internal.api.Raise3dPrinterData;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Raise3dHandler} is responsible for handling commands and polling
 * the Raise3D printer.
 *
 * @author copilot-swe-agent[bot] - Initial contribution
 */
@NonNullByDefault
public class Raise3dHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(Raise3dHandler.class);
    private final HttpClient httpClient;

    private @Nullable ScheduledFuture<?> refreshJob;
    private @Nullable Raise3dApiClient apiClient;
    private Raise3dConfiguration config = new Raise3dConfiguration();

    public Raise3dHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // This binding is read-only, no commands to handle
    }

    @Override
    public void initialize() {
        config = getConfigAs(Raise3dConfiguration.class);

        String ipAddress = config.ipAddress;
        String password = config.password;

        if (ipAddress == null || ipAddress.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "IP address not configured");
            return;
        }

        if (password == null || password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Password not configured");
            return;
        }

        apiClient = new Raise3dApiClient(httpClient, ipAddress, password);
        updateStatus(ThingStatus.UNKNOWN);

        startRefreshJob();
    }

    @Override
    public void dispose() {
        stopRefreshJob();
        super.dispose();
    }

    private void startRefreshJob() {
        stopRefreshJob();
        int refreshInterval = config.refreshInterval;
        if (refreshInterval <= 0) {
            refreshInterval = 30;
        }

        refreshJob = scheduler.scheduleWithFixedDelay(this::refresh, 0, refreshInterval, TimeUnit.SECONDS);
        logger.debug("Started refresh job with interval {} seconds", refreshInterval);
    }

    private void stopRefreshJob() {
        ScheduledFuture<?> job = refreshJob;
        if (job != null) {
            job.cancel(true);
            refreshJob = null;
        }
    }

    private void refresh() {
        Raise3dApiClient client = apiClient;
        if (client == null) {
            return;
        }

        try {
            // Check if we need to authenticate or re-authenticate
            if (!client.validateToken()) {
                if (!client.authenticate()) {
                    handleOffline();
                    return;
                }
            }

            // Fetch printer data
            Raise3dPrinterData data = client.fetchPrinterData();
            if (data == null) {
                handleOffline();
                return;
            }

            // Successfully retrieved data, update status and channels
            updateStatus(ThingStatus.ONLINE);
            updateChannels(data);

        } catch (Exception e) {
            logger.debug("Error during refresh: {}", e.getMessage(), e);
            handleOffline();
        }
    }

    private void handleOffline() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to communicate with printer");

        // Set running_status to "offline" and print_progress to 100
        updateState(CHANNEL_RUNNING_STATUS, new StringType("offline"));
        updateState(CHANNEL_PRINT_PROGRESS, new DecimalType(100));
    }

    private void updateChannels(Raise3dPrinterData data) {
        // Job information
        if (data.fileName != null) {
            updateState(CHANNEL_FILE_NAME, new StringType(data.fileName));
        }
        if (data.jobId != null) {
            updateState(CHANNEL_JOB_ID, new StringType(data.jobId));
        }
        if (data.jobStatus != null) {
            updateState(CHANNEL_JOB_STATUS, new StringType(data.jobStatus));
        }
        updateState(CHANNEL_PRINT_PROGRESS, new DecimalType(data.printProgress));
        updateState(CHANNEL_PRINTED_LAYER, new DecimalType(data.printedLayer));
        updateState(CHANNEL_PRINTED_TIME, new QuantityType<>(data.printedTime / 1000, Units.SECOND));
        updateState(CHANNEL_TOTAL_LAYER, new DecimalType(data.totalLayer));
        updateState(CHANNEL_TOTAL_TIME, new QuantityType<>(data.totalTime / 1000, Units.SECOND));

        // System information
        if (data.serialNumber != null) {
            updateState(CHANNEL_SERIAL_NUMBER, new StringType(data.serialNumber));
        }
        if (data.apiVersion != null) {
            updateState(CHANNEL_API_VERSION, new StringType(data.apiVersion));
        }
        updateState(CHANNEL_BATTERY, new DecimalType(data.battery));
        updateState(CHANNEL_BRIGHTNESS, new DecimalType(data.brightness));
        if (data.dateTime != null) {
            updateState(CHANNEL_DATE_TIME, new StringType(data.dateTime));
        }
        if (data.firmwareVersion != null) {
            updateState(CHANNEL_FIRMWARE_VERSION, new StringType(data.firmwareVersion));
        }
        if (data.language != null) {
            updateState(CHANNEL_LANGUAGE, new StringType(data.language));
        }
        if (data.machineId != null) {
            updateState(CHANNEL_MACHINE_ID, new StringType(data.machineId));
        }
        if (data.machineIp != null) {
            updateState(CHANNEL_MACHINE_IP, new StringType(data.machineIp));
        }
        if (data.machineName != null) {
            updateState(CHANNEL_MACHINE_NAME, new StringType(data.machineName));
        }
        if (data.model != null) {
            updateState(CHANNEL_MODEL, new StringType(data.model));
        }
        updateState(CHANNEL_NOZZLES_NUM, new DecimalType(data.nozzlesNum));
        updateState(CHANNEL_STORAGE_AVAILABLE, new QuantityType<>(data.storageAvailable, Units.BYTE));
        if (data.update != null) {
            updateState(CHANNEL_UPDATE, new StringType(data.update));
        }
        if (data.version != null) {
            updateState(CHANNEL_VERSION, new StringType(data.version));
        }

        // Running status
        if (data.runningStatus != null) {
            updateState(CHANNEL_RUNNING_STATUS, new StringType(data.runningStatus));
        }
        updateState(CHANNEL_FAN_CUR_SPEED, new DecimalType(data.fanCurSpeed));
        updateState(CHANNEL_FAN_TAR_SPEED, new DecimalType(data.fanTarSpeed));
        updateState(CHANNEL_FEED_CUR_RATE, new DecimalType(data.feedCurRate));
        updateState(CHANNEL_FEED_TAR_RATE, new DecimalType(data.feedTarRate));
        updateState(CHANNEL_HEATBED_CUR_TEMP, new QuantityType<>(data.heatbedCurTemp, SIUnits.CELSIUS));
        updateState(CHANNEL_HEATBED_TAR_TEMP, new QuantityType<>(data.heatbedTarTemp, SIUnits.CELSIUS));
    }
}
