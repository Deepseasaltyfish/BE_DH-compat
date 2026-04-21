package com.deepseasaltyfish.BeLodCompat.common.cache;

import com.deepseasaltyfish.BeLodCompat.common.DataBaseCache;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Universal chunk cache manager, encapsulates:
 * - Per-chunk ConcurrentHashMap cache
 * - Delayed unload task scheduling
 * - Basic CRUD operations
 * Behavior is fully consistent with the old IRBlockDataCache/LTBlockDataCache.
 *
 * @param <V> cache value type
 */
public class ChunkCache<V> {
    protected final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, V>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<ChunkPos, ScheduledFuture<?>> pendingRemovals = new ConcurrentHashMap<>();
    private final long removalDelayMs;

    public ChunkCache(long removalDelayMs) {
        this.removalDelayMs = removalDelayMs;
    }

    /**
     * Cancel the delayed unload task for the specified chunk (if exists).
     * Behavior is consistent with pendingRemovals.remove + cancel in old code.
     */
    public void cancelRemoval(ChunkPos chunkPos) {
        ScheduledFuture<?> future = pendingRemovals.remove(chunkPos);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Schedule a delayed unload for a chunk (used for active unload, e.g. removeChunkInMemory).
     * @param onRemove callback on unload (for logging), can be null
     */
    public void scheduleRemoval(ChunkPos chunkPos, BiConsumer<ChunkPos, ConcurrentHashMap<BlockPos, V>> onRemove) {
        cancelRemoval(chunkPos);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            ConcurrentHashMap<BlockPos, V> removed = cache.remove(chunkPos);
            if (removed != null && onRemove != null) {
                onRemove.accept(chunkPos, removed);
            }
            pendingRemovals.remove(chunkPos);
        }, removalDelayMs, TimeUnit.MILLISECONDS);
        pendingRemovals.put(chunkPos, future);
    }

    /**
     * Update cache (fully consistent with old put logic):
     * - Always cancel delayed unload task for the chunk
     * - If old and new data are equal (as determined by sameChecker), return false and do not update memory
     * - Otherwise update memory and return true
     * Note: Return value only indicates whether memory changed; external code should not rely on it for DB operations (old code always executed DB writes)
     */
    public boolean put(BlockPos pos, V newData, BiPredicate<V, V> sameChecker) {
        ChunkPos chunkPos = new ChunkPos(pos);
        cancelRemoval(chunkPos);

        ConcurrentHashMap<BlockPos, V> inner = cache.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>());

        V oldData = inner.get(pos);
        if (oldData != null && sameChecker.test(oldData, newData)) {
            return false; // data unchanged, skip memory update
        }

        inner.put(pos.immutable(), newData);
        return true;
    }

    /**
     * Get cached value (read-only)
     */
    public V get(BlockPos pos) {
        if (pos == null) return null;
        ChunkPos cp = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(cp);
        return inner != null ? inner.get(pos) : null;
    }

    /**
     * Remove a single position (fully consistent with old removeAt):
     * - Remove the position from the chunk's inner map
     * - If the chunk becomes empty, immediately remove it from cache (no delay, no cancellation of pendingRemovals)
     * Note: old code did not cancel pendingRemovals; we do not cancel here either
     */
    public void removeAt(BlockPos pos) {
        if (pos == null) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(chunkPos);
        if (inner != null) {
            inner.remove(pos);
            if (inner.isEmpty()) {
                cache.remove(chunkPos);
                // Note: old code did not actively cancel pendingRemovals; if any task remains, it will find the cache empty, which is harmless
            }
        }
    }

    /**
     * Actively unload the entire chunk (delayed execution, fully consistent with old removeChunkInMemory)
     * @param onRemove callback on unload, for logging
     */
    public void removeChunkInMemory(ChunkPos chunkPos, BiConsumer<ChunkPos, ConcurrentHashMap<BlockPos, V>> onRemove) {
        if (chunkPos == null) return;
        scheduleRemoval(chunkPos, onRemove);
    }

    /**
     * Clear all caches and cancel all pending unload tasks
     */
    public void clearAll() {
        pendingRemovals.values().forEach(future -> future.cancel(false));
        pendingRemovals.clear();
        cache.clear();
    }

    /**
     * Check if a position exists in the cache
     */
    public boolean contains(BlockPos pos) {
        if (pos == null) return false;
        ChunkPos cp = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(cp);
        return inner != null && inner.containsKey(pos);
    }

    /**
     * Return the number of chunks currently cached (not the number of entries)
     */
    public int getChunkCount() {
        return cache.size();
    }

    /**
     * Get the internal raw cache map (only for special cases, e.g., LT's loadChunkFromDB needs direct access)
     * Use with caution
     */
    public ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, V>> getRawCache() {
        return cache;
    }

    public String dumpToString(String title, Function<V, String> formatter) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" Dump ===\n");
        int total = 0;
        for (ConcurrentHashMap<BlockPos, V> inner : cache.values()) {
            total += inner.size();
        }
        if (total == 0) {
            sb.append("(empty)\n");
            return sb.toString();
        }
        if (total > 100) {
            sb.append("Total entries: ").append(total).append("\n");
            return sb.toString();
        }
        for (Map.Entry<ChunkPos, ConcurrentHashMap<BlockPos, V>> chunkEntry : cache.entrySet()) {
            ChunkPos cp = chunkEntry.getKey();
            ConcurrentHashMap<BlockPos, V> inner = chunkEntry.getValue();
            if (inner.isEmpty()) continue;
            sb.append("Chunk ").append(cp.x).append(", ").append(cp.z).append(":\n");
            for (Map.Entry<BlockPos, V> entry : inner.entrySet()) {
                BlockPos pos = entry.getKey();
                V data = entry.getValue();
                sb.append("  ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ())
                        .append(" -> ").append(formatter.apply(data)).append("\n");
            }
        }
        sb.append("Total entries: ").append(total).append("\n");
        return sb.toString();
    }

    /**
     * Shut down the scheduler (call when mod unloads)
     */
    public void shutdown() {
        pendingRemovals.values().forEach(future -> future.cancel(false));
        pendingRemovals.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void loadChunkFromDB(
            ChunkPos chunkPos, String modId,
            java.util.function.Function<DataBaseCache.BlockDataEntry, V> mapper,
            DebugLogger logger
    ) {
        if (!DatabaseManager.isReady()) return;
        getRawCache().computeIfAbsent(chunkPos, cp -> {
            Map<BlockPos, DataBaseCache.BlockDataEntry> tempMap = new HashMap<>();
            DataBaseCache.loadChunk(cp, modId, tempMap);
            ConcurrentHashMap<BlockPos, V> inner = new ConcurrentHashMap<>();
            for (Map.Entry<BlockPos, DataBaseCache.BlockDataEntry> entry : tempMap.entrySet()) {
                BlockPos pos = entry.getKey();
                DataBaseCache.BlockDataEntry dataEntry = entry.getValue();
                V value = mapper.apply(dataEntry);
                if (value != null) {
                    inner.put(pos.immutable(), value);
                }
            }
            return inner;
        });
    }
}