/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.persistence.chronicle.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.OpenHAB;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistenceItemInfo;
import org.openhab.core.persistence.PersistenceService;
import org.openhab.core.persistence.QueryablePersistenceService;
import org.openhab.core.persistence.strategy.PersistenceStrategy;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.openhft.chronicle.map.*;

/**
 * This is the implementation of the Chronicle {@link PersistenceService}. To learn more about Chronicle please visit
 * their
 * <a href="http://www.mapdb.org/">website</a>.
 *
 * @author Flole Systems - Initial contribution
 */
@NonNullByDefault
@Component(service = { PersistenceService.class, QueryablePersistenceService.class }, property = Constants.SERVICE_PID
        + "=org.openhab.chronicle")
public class ChroniclePersistenceService implements QueryablePersistenceService {

    private static final String SERVICE_ID = "chronicle";
    private static final String SERVICE_LABEL = "Chronicle";
    private static final Path DB_DIR = new File(OpenHAB.getUserDataFolder(), "persistence").toPath()
            .resolve("chronicle");
    private static final Path BACKUP_DIR = DB_DIR.resolve("backup");
    private static final String DB_FILE_NAME = "storage.chronicle";

    private final Logger logger = LoggerFactory.getLogger(ChroniclePersistenceService.class);

    private final ExecutorService threadPool = ThreadPoolManager.getPool(getClass().getSimpleName());

    /**
     * holds the local instance of the Chronicle database
     */

    private @NonNullByDefault({}) ChronicleMap<String, String> db;

    private transient Gson mapper = new GsonBuilder().setDateFormat(DateTimeType.DATE_PATTERN_JSON_COMPAT)
            .registerTypeHierarchyAdapter(State.class, new StateTypeAdapter()).create();

    @Activate
    public void activate() {
        logger.debug("MChronicle persistence service is being activated");

        try {
            Files.createDirectories(DB_DIR);
        } catch (IOException e) {
            logger.warn("Failed to create one or more directories in the path '{}'", DB_DIR);
            logger.warn("Chronicle persistence service activation has failed.");
            return;
        }

        File dbFile = DB_DIR.resolve(DB_FILE_NAME).toFile();
        try {
            db = ChronicleMap.of(String.class, String.class).name("itemStore").averageKeySize(15).entries(10_000)
                    .recoverPersistedTo(dbFile, false);
        } catch (java.io.IOException re) {
            logger.warn("Failed to create or open the Chronicle DB: {}", re.getMessage());
            logger.warn("Chronicle persistence service activation has failed.");
            return;
        }
        logger.debug("Chronicle persistence service is now activated");
    }

    @Deactivate
    public void deactivate() {
        logger.debug("Chronicle persistence service deactivated");
        if (db != null) {
            db.close();
        }
    }

    @Override
    public String getId() {
        return SERVICE_ID;
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return SERVICE_LABEL;
    }

    @Override
    public Set<PersistenceItemInfo> getItemInfo() {
        return db.values().stream().map(this::deserialize).flatMap(ChroniclePersistenceService::streamOptional)
                .collect(Collectors.<PersistenceItemInfo> toUnmodifiableSet());
    }

    @Override
    public void store(Item item) {
        store(item, item.getName());
    }

    @Override
    public void store(Item item, @Nullable String alias) {
        if (item.getState() instanceof UnDefType) {
            return;
        }

        // PersistenceManager passes SimpleItemConfiguration.alias which can be null
        String localAlias = alias == null ? item.getName() : alias;
        logger.debug("store called for {}", localAlias);

        State state = item.getState();
        ChronicleItem mItem = new ChronicleItem();
        mItem.setName(localAlias);
        mItem.setState(state);
        mItem.setTimestamp(new Date());
        threadPool.submit(() -> {
            String json = serialize(mItem);
            db.put(localAlias, json);
            logger.debug("Stored '{}' with state '{}' as '{}' in Chronicle database", localAlias, state, json);
        });
    }

    @Override
    public Iterable<HistoricItem> query(FilterCriteria filter) {
        String json = db.get(filter.getItemName());
        if (json == null) {
            return List.of();
        }
        Optional<ChronicleItem> item = deserialize(json);
        return item.isPresent() ? List.of(item.get()) : List.of();
    }

    private String serialize(ChronicleItem item) {
        return mapper.toJson(item);
    }

    @SuppressWarnings("null")
    private Optional<ChronicleItem> deserialize(String json) {
        ChronicleItem item = mapper.fromJson(json, ChronicleItem.class);
        if (item == null || !item.isValid()) {
            logger.warn("Deserialized invalid item: {}", item);
            return Optional.empty();
        } else if (logger.isDebugEnabled()) {
            logger.debug("Deserialized '{}' with state '{}' from '{}'", item.getName(), item.getState(), json);
        }

        return Optional.of(item);
    }

    private static <T> Stream<T> streamOptional(Optional<T> opt) {
        return opt.isPresent() ? Stream.of(opt.get()) : Stream.empty();
    }

    @Override
    public List<PersistenceStrategy> getDefaultStrategies() {
        return List.of(PersistenceStrategy.Globals.RESTORE, PersistenceStrategy.Globals.CHANGE);
    }
}
