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

import static org.lmdbjava.DbiFlags.MDB_CREATE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
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
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;
import org.openhab.core.OpenHAB;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistedItem;
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

/**
 * This is the implementation of the LMDB {@link PersistenceService}. To learn more about LMDB please visit their
 * <a href="http://www.lmdb.tech/doc/">website</a>.
 *
 * @author Jens Viebig - Initial contribution (MapDB)
 * @author Martin Kühl - Port to 3.x (MapDB)
 * @author Florian Hotze - LMDB implementation
 */
@NonNullByDefault
@Component(service = { PersistenceService.class, QueryablePersistenceService.class }, property = Constants.SERVICE_PID
        + "=org.openhab.lmdb")
public class LmdbPersistenceService implements QueryablePersistenceService {

    private static final String SERVICE_ID = "lmdb";
    private static final String SERVICE_LABEL = "LMDB";
    private static final Path DB_DIR = new File(OpenHAB.getUserDataFolder(), "persistence").toPath().resolve("lmdb");
    private static final String DB_NAME = "itemStore";
    private static final long DB_SIZE = 10485760L; // 10MB initial size

    private final Logger logger = LoggerFactory.getLogger(LmdbPersistenceService.class);

    private final ExecutorService threadPool = ThreadPoolManager.getPool(getClass().getSimpleName());

    private @NonNullByDefault({}) Env<ByteBuffer> env;
    private @NonNullByDefault({}) Dbi<ByteBuffer> db;

    private transient Gson mapper = new GsonBuilder().setDateFormat(DateTimeType.DATE_PATTERN_JSON_COMPAT)
            .registerTypeHierarchyAdapter(State.class, new StateTypeAdapter()).create();

    @Activate
    public void activate() {
        logger.debug("LMDB persistence service is being activated");

        try {
            Files.createDirectories(DB_DIR);
        } catch (IOException e) {
            logger.warn("Failed to create one or more directories in the path '{}'", DB_DIR);
            logger.warn("LMDB persistence service activation has failed.");
            return;
        }

        File dbDir = DB_DIR.toFile();
        try {
            env = Env.create().setMapSize(DB_SIZE).setMaxDbs(1).open(dbDir, EnvFlags.MDB_NOSUBDIR);
            db = env.openDbi(DB_NAME, MDB_CREATE);
            logger.debug("LMDB persistence service is now activated");
        } catch (Exception e) {
            logger.warn("Failed to create or open the LMDB: {}", e.getMessage());
            logger.warn("LMDB persistence service activation has failed.");
        }
    }

    @Deactivate
    public void deactivate() {
        logger.debug("LMDB persistence service deactivated");
        if (db != null) {
            db.close();
        }
        if (env != null) {
            env.close();
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
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            return db.iterate(txn).stream().map(kv -> {
                ByteBuffer val = kv.val();
                byte[] bytes = new byte[val.remaining()];
                val.get(bytes);
                String json = new String(bytes, StandardCharsets.UTF_8);
                return deserialize(json);
            }).flatMap(LmdbPersistenceService::streamOptional).collect(Collectors.<PersistenceItemInfo> toUnmodifiableSet());
        }
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
        LmdbItem lItem = new LmdbItem();
        lItem.setName(localAlias);
        lItem.setState(state);
        lItem.setLastState(item.getLastState());
        ZonedDateTime lastStateUpdate = item.getLastStateUpdate();
        lItem.setTimestamp(lastStateUpdate != null ? Date.from(lastStateUpdate.toInstant()) : new Date());
        ZonedDateTime lastStateChange = item.getLastStateChange();
        lItem.setLastStateChange(lastStateChange != null ? Date.from(lastStateChange.toInstant()) : null);

        threadPool.submit(() -> {
            String json = serialize(lItem);
            ByteBuffer key = ByteBuffer.allocateDirect(localAlias.getBytes(StandardCharsets.UTF_8).length);
            key.put(localAlias.getBytes(StandardCharsets.UTF_8)).flip();

            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer val = ByteBuffer.allocateDirect(jsonBytes.length);
            val.put(jsonBytes).flip();

            try (Txn<ByteBuffer> txn = env.txnWrite()) {
                db.put(txn, key, val);
                txn.commit();
                logger.debug("Stored '{}' with state '{}' in LMDB database", localAlias, state);
            }
        });
    }

    @Override
    public Iterable<HistoricItem> query(FilterCriteria filter) {
        String itemName = filter.getItemName();
        if (itemName == null) {
            return List.of();
        }

        ByteBuffer key = ByteBuffer.allocateDirect(itemName.getBytes(StandardCharsets.UTF_8).length);
        key.put(itemName.getBytes(StandardCharsets.UTF_8)).flip();

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer val = db.get(txn, key);
            if (val == null) {
                return List.of();
            }
            byte[] bytes = new byte[val.remaining()];
            val.get(bytes);
            String json = new String(bytes, StandardCharsets.UTF_8);
            Optional<LmdbItem> item = deserialize(json);
            return item.isPresent() ? List.of(item.get()) : List.of();
        }
    }

    @Override
    public @Nullable PersistedItem persistedItem(String itemName, @Nullable String alias) {
        String key = alias != null ? alias : itemName;
        ByteBuffer keyBuf = ByteBuffer.allocateDirect(key.getBytes(StandardCharsets.UTF_8).length);
        keyBuf.put(key.getBytes(StandardCharsets.UTF_8)).flip();

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer val = db.get(txn, keyBuf);
            if (val == null) {
                return null;
            }
            byte[] bytes = new byte[val.remaining()];
            val.get(bytes);
            String json = new String(bytes, StandardCharsets.UTF_8);
            Optional<LmdbItem> item = deserialize(json);
            LmdbItem dbItem = item.orElse(null);
            if (dbItem != null) {
                dbItem.setName(itemName);
            }
            return dbItem;
        }
    }

    private String serialize(LmdbItem item) {
        return mapper.toJson(item);
    }

    @SuppressWarnings("null")
    private Optional<LmdbItem> deserialize(String json) {
        LmdbItem item = mapper.fromJson(json, LmdbItem.class);
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
