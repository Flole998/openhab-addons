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
import static org.lmdbjava.EnvFlags.MDB_MAPASYNC;
import static org.lmdbjava.EnvFlags.MDB_NOMETASYNC;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.openhab.core.OpenHAB;
import org.openhab.core.items.Item;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistedItem;
import org.openhab.core.persistence.PersistenceItemInfo;
import org.openhab.core.persistence.PersistenceService;
import org.openhab.core.persistence.QueryablePersistenceService;
import org.openhab.core.persistence.strategy.PersistenceStrategy;
import org.openhab.core.types.UnDefType;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String VALUE_DB_NAME = "itemStore";
    private static final String META_DB_NAME = "itemInfoStore";
    private static final long DB_SIZE = 268435456L; // 256MB initial size

    private static final int WRITER_BATCH_SIZE = 256;
    private static final long WRITER_POLL_TIMEOUT_MS = 100;

    private final Logger logger = LoggerFactory.getLogger(LmdbPersistenceService.class);

    private final LinkedBlockingQueue<WriteRequest> writeQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, byte[]> keyBytesCache = new ConcurrentHashMap<>();
    private final ThreadLocal<ByteBuffer> readKeyBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(128));

    private volatile boolean active = false;
    private volatile @Nullable Thread writerThread;

    private @NonNullByDefault({}) Env<ByteBuffer> env;
    private @NonNullByDefault({}) Dbi<ByteBuffer> valueDb;
    private @NonNullByDefault({}) Dbi<ByteBuffer> metaDb;

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
            env = Env.create().setMapSize(DB_SIZE).setMaxDbs(2).open(dbDir, MDB_NOSYNC, MDB_NOMETASYNC, MDB_MAPASYNC);
            valueDb = env.openDbi(VALUE_DB_NAME, MDB_CREATE);
            metaDb = env.openDbi(META_DB_NAME, MDB_CREATE);
            active = true;
            startWriterThread();
            logger.debug("LMDB persistence service is now activated");
        } catch (Exception e) {
            logger.warn("Failed to create or open the LMDB: {}", e.getMessage());
            logger.warn("LMDB persistence service activation has failed.");
        }
    }

    @Deactivate
    public void deactivate() {
        logger.debug("LMDB persistence service deactivating");
        active = false;

        Thread localWriterThread = writerThread;
        if (localWriterThread != null) {
            localWriterThread.interrupt();
            try {
                localWriterThread.join(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writerThread = null;
        }

        if (valueDb != null) {
            valueDb.close();
        }
        if (metaDb != null) {
            metaDb.close();
        }
        if (env != null) {
            env.close();
        }

        writeQueue.clear();
        keyBytesCache.clear();
        readKeyBuffer.remove();

        logger.debug("LMDB persistence service deactivated");
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
        if (!active) {
            return Set.of();
        }

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            Set<PersistenceItemInfo> items = new java.util.HashSet<>();
            for (var kv : metaDb.iterate(txn)) {
                ByteBuffer keyBuffer = kv.key();
                byte[] keyBytes = new byte[keyBuffer.remaining()];
                keyBuffer.get(keyBytes);
                String itemName = new String(keyBytes, StandardCharsets.UTF_8);

                ByteBuffer metaBuffer = kv.val();
                if (LmdbRecordCodec.isValidMetadata(metaBuffer)) {
                    items.add(new LmdbItemInfo(itemName));
                }
            }
            return Set.copyOf(items);
        }
    }

    @Override
    public void store(Item item) {
        store(item, item.getName());
    }

    @Override
    public void store(Item item, @Nullable String alias) {
        if (item.getState() instanceof UnDefType || !active) {
            return;
        }

        String localAlias = alias == null ? item.getName() : alias;
        LmdbItem lItem = new LmdbItem();
        lItem.setName(localAlias);
        lItem.setState(item.getState());
        lItem.setLastState(item.getLastState());

        ZonedDateTime lastStateUpdate = item.getLastStateUpdate();
        lItem.setTimestamp(lastStateUpdate != null ? Date.from(lastStateUpdate.toInstant()) : new Date());

        ZonedDateTime lastStateChange = item.getLastStateChange();
        lItem.setLastStateChange(lastStateChange != null ? Date.from(lastStateChange.toInstant()) : null);

        byte[] keyBytes = getKeyBytes(localAlias);
        writeQueue.offer(new WriteRequest(keyBytes, lItem));
    }

    @Override
    public Iterable<HistoricItem> query(FilterCriteria filter) {
        String itemName = filter.getItemName();
        if (itemName == null || !active) {
            return List.of();
        }

        LmdbItem item = readItem(itemName, itemName);
        return item != null ? List.of(item) : List.of();
    }

    @Override
    public @Nullable PersistedItem persistedItem(String itemName, @Nullable String alias) {
        if (!active) {
            return null;
        }

        String keyName = alias != null ? alias : itemName;
        LmdbItem dbItem = readItem(keyName, itemName);
        if (dbItem != null) {
            dbItem.setName(itemName);
        }
        return dbItem;
    }

    @Override
    public List<PersistenceStrategy> getDefaultStrategies() {
        return List.of(PersistenceStrategy.Globals.RESTORE, PersistenceStrategy.Globals.CHANGE);
    }

    private @Nullable LmdbItem readItem(String keyName, String itemNameForRecord) {
        byte[] keyBytes = getKeyBytes(keyName);
        ByteBuffer keyBuffer = resetBuffer(readKeyBuffer.get(), keyBytes.length);
        keyBuffer.put(keyBytes).flip();

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer value = valueDb.get(txn, keyBuffer);
            if (value == null) {
                return null;
            }
            return LmdbRecordCodec.decodeValue(itemNameForRecord, value);
        }
    }

    private byte[] getKeyBytes(String key) {
        byte[] cachedBytes = keyBytesCache.get(key);
        if (cachedBytes != null) {
            return cachedBytes;
        }

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] existing = keyBytesCache.putIfAbsent(key, keyBytes);
        return existing != null ? existing : keyBytes;
    }

    private void startWriterThread() {
        Thread localWriterThread = new Thread(this::writerLoop, "lmdb-persistence-writer");
        localWriterThread.setDaemon(true);
        localWriterThread.start();
        writerThread = localWriterThread;
    }

    private void writerLoop() {
        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(128);
        ByteBuffer valueBuffer = ByteBuffer.allocateDirect(512);
        ByteBuffer metaBuffer = ByteBuffer.allocateDirect(64);

        List<WriteRequest> batch = new java.util.ArrayList<>(WRITER_BATCH_SIZE);

        while (active || !writeQueue.isEmpty()) {
            try {
                WriteRequest first = writeQueue.poll(WRITER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                batch.clear();
                batch.add(first);
                writeQueue.drainTo(batch, WRITER_BATCH_SIZE - 1);

                try (Txn<ByteBuffer> txn = env.txnWrite()) {
                    for (WriteRequest request : batch) {
                        LmdbItem item = request.item();
                        byte[] keyBytes = request.keyBytes();

                        keyBuffer = resetBuffer(keyBuffer, keyBytes.length);
                        keyBuffer.put(keyBytes).flip();

                        int valueSize = LmdbRecordCodec.valueEncodedSize(item);
                        valueBuffer = resetBuffer(valueBuffer, valueSize);
                        LmdbRecordCodec.encodeValue(valueBuffer, item);
                        valueBuffer.flip();

                        valueDb.put(txn, keyBuffer, valueBuffer);

                        keyBuffer.rewind();

                        int metaSize = LmdbRecordCodec.metadataEncodedSize();
                        metaBuffer = resetBuffer(metaBuffer, metaSize);
                        LmdbRecordCodec.encodeMetadata(metaBuffer, item);
                        metaBuffer.flip();

                        metaDb.put(txn, keyBuffer, metaBuffer);
                    }
                    txn.commit();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                logger.warn("Failed to flush LMDB write batch: {}", e.getMessage());
            }
        }
    }

    private static ByteBuffer resetBuffer(ByteBuffer current, int requiredSize) {
        ByteBuffer buffer = current;
        if (buffer.capacity() < requiredSize) {
            int nextSize = Math.max(requiredSize, buffer.capacity() * 2);
            buffer = ByteBuffer.allocateDirect(nextSize);
        }
        buffer.clear();
        return buffer;
    }

    private record WriteRequest(byte[] keyBytes, LmdbItem item) {
    }
}
