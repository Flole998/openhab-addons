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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link Raise3dApiClient} class handles communication with the Raise3D printer API.
 *
 * @author copilot-swe-agent[bot] - Initial contribution
 */
@NonNullByDefault
public class Raise3dApiClient {
    private final Logger logger = LoggerFactory.getLogger(Raise3dApiClient.class);
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String password;

    private @Nullable String authToken;
    private static final int TIMEOUT_MS = 2000;

    public Raise3dApiClient(HttpClient httpClient, String ipAddress, String password) {
        this.httpClient = httpClient;
        this.baseUrl = "http://" + ipAddress + ":10800";
        this.password = password;
    }

    /**
     * Authenticates with the printer and obtains an auth token.
     *
     * @return true if authentication was successful
     */
    public boolean authenticate() {
        try {
            long timestamp = System.currentTimeMillis();
            String reqString = "password=" + password + "&timestamp=" + timestamp;

            // Calculate SHA1 then MD5 hash as per the Python script
            String sha1Hash = sha1(reqString);
            String md5Hash = md5(sha1Hash);

            String loginUrl = baseUrl + "/v1/login?sign=" + md5Hash + "&timestamp=" + timestamp;

            ContentResponse response = httpClient.newRequest(loginUrl).method(HttpMethod.GET)
                    .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                TypeToken<Raise3dApiResponse<Raise3dLoginData>> typeToken = new TypeToken<>() {
                };
                Raise3dApiResponse<Raise3dLoginData> loginResponse = gson.fromJson(response.getContentAsString(),
                        typeToken.getType());
                if (loginResponse != null && loginResponse.data != null && loginResponse.data.token != null) {
                    authToken = loginResponse.data.token;
                    logger.debug("Authentication successful");
                    return true;
                }
            }
            logger.debug("Authentication failed: HTTP {}", response.getStatus());
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            logger.debug("Authentication error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Validates the current token by checking printer system status.
     *
     * @return true if token is valid
     */
    public boolean validateToken() {
        String token = authToken;
        if (token == null) {
            return false;
        }

        try {
            String url = baseUrl + "/v1/printer/system?token=" + token;
            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                Raise3dApiResponse<?> apiResponse = gson.fromJson(response.getContentAsString(),
                        Raise3dApiResponse.class);
                return apiResponse != null && apiResponse.status == 1;
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            logger.debug("Token validation error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Fetches complete printer data from all API endpoints.
     * Silently ignores individual endpoint failures and only returns null if all endpoints fail.
     *
     * @return Printer data or null if all fetches failed
     */
    public @Nullable Raise3dPrinterData fetchPrinterData() {
        String token = authToken;
        if (token == null) {
            logger.debug("No auth token available");
            return null;
        }

        try {
            Raise3dPrinterData data = new Raise3dPrinterData();
            int successCount = 0;

            // Fetch job status - silently ignore failure
            if (fetchJobStatus(token, data)) {
                successCount++;
            }

            // Fetch system information - silently ignore failure
            if (fetchSystemInfo(token, data)) {
                successCount++;
            }

            // Fetch running status - silently ignore failure
            if (fetchRunningStatus(token, data)) {
                successCount++;
            }

            // Fetch basic info - silently ignore failure
            if (fetchBasicInfo(token, data)) {
                successCount++;
            }

            // Only return null if all endpoints failed
            if (successCount == 0) {
                logger.debug("All API endpoints failed");
                return null;
            }

            return data;
        } catch (Exception e) {
            logger.debug("Error fetching printer data: {}", e.getMessage());
            return null;
        }
    }

    private boolean fetchJobStatus(String token, Raise3dPrinterData data) {
        try {
            String url = baseUrl + "/v1/job/currentjob?token=" + token;
            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                TypeToken<Raise3dApiResponse<Raise3dPrinterData>> typeToken = new TypeToken<>() {
                };
                Raise3dApiResponse<Raise3dPrinterData> apiResponse = gson.fromJson(response.getContentAsString(),
                        typeToken.getType());
                if (apiResponse != null && apiResponse.data != null) {
                    Raise3dPrinterData jobData = apiResponse.data;
                    data.fileName = jobData.fileName;
                    data.jobId = jobData.jobId;
                    data.jobStatus = jobData.jobStatus;
                    data.printProgress = jobData.printProgress;
                    data.printedLayer = jobData.printedLayer;
                    data.printedTime = jobData.printedTime;
                    data.totalLayer = jobData.totalLayer;
                    data.totalTime = jobData.totalTime;
                    return true;
                }
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            logger.debug("Error fetching job status: {}", e.getMessage());
        }
        return false;
    }

    private boolean fetchSystemInfo(String token, Raise3dPrinterData data) {
        try {
            String url = baseUrl + "/v1/printer/system?token=" + token;
            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                TypeToken<Raise3dApiResponse<Raise3dPrinterData>> typeToken = new TypeToken<>() {
                };
                Raise3dApiResponse<Raise3dPrinterData> apiResponse = gson.fromJson(response.getContentAsString(),
                        typeToken.getType());
                if (apiResponse != null && apiResponse.data != null) {
                    Raise3dPrinterData sysData = apiResponse.data;
                    data.serialNumber = sysData.serialNumber;
                    data.apiVersion = sysData.apiVersion;
                    data.battery = sysData.battery;
                    data.brightness = sysData.brightness;
                    data.dateTime = sysData.dateTime;
                    data.firmwareVersion = sysData.firmwareVersion;
                    data.language = sysData.language;
                    data.machineId = sysData.machineId;
                    data.machineIp = sysData.machineIp;
                    data.machineName = sysData.machineName;
                    data.model = sysData.model;
                    data.nozzlesNum = sysData.nozzlesNum;
                    data.storageAvailable = sysData.storageAvailable;
                    data.update = sysData.update;
                    data.version = sysData.version;
                    return true;
                }
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            logger.debug("Error fetching system info: {}", e.getMessage());
        }
        return false;
    }

    private boolean fetchRunningStatus(String token, Raise3dPrinterData data) {
        try {
            String url = baseUrl + "/v1/printer/runningstatus?token=" + token;
            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                TypeToken<Raise3dApiResponse<Raise3dPrinterData>> typeToken = new TypeToken<>() {
                };
                Raise3dApiResponse<Raise3dPrinterData> apiResponse = gson.fromJson(response.getContentAsString(),
                        typeToken.getType());
                if (apiResponse != null && apiResponse.data != null) {
                    Raise3dPrinterData statusData = apiResponse.data;
                    data.runningStatus = statusData.runningStatus;
                    data.fanCurSpeed = statusData.fanCurSpeed;
                    data.fanTarSpeed = statusData.fanTarSpeed;
                    data.feedCurRate = statusData.feedCurRate;
                    data.feedTarRate = statusData.feedTarRate;
                    data.heatbedCurTemp = statusData.heatbedCurTemp;
                    data.heatbedTarTemp = statusData.heatbedTarTemp;
                    return true;
                }
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            logger.debug("Error fetching running status: {}", e.getMessage());
        }
        return false;
    }

    private boolean fetchBasicInfo(String token, Raise3dPrinterData data) {
        try {
            String url = baseUrl + "/v1/printer/basic?token=" + token;
            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                // Basic info might overlap with system info, so we just validate the response
                return true;
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            logger.debug("Error fetching basic info: {}", e.getMessage());
        }
        return false;
    }

    private String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.US_ASCII));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-1 algorithm not available", e);
            return "";
        }
    }

    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.US_ASCII));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not available", e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
