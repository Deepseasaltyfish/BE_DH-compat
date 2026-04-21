package com.deepseasaltyfish.BeLodCompat.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public abstract class GenericBlockDataCache<V> {
    public final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, V>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<ChunkPos, ScheduledFuture<?>> pendingRemovals = new ConcurrentHashMap<>();
    private final long removalDelayMs;

    public GenericBlockDataCache(long removalDelayMs) {
        this.removalDelayMs = removalDelayMs;
    }

    // 取消区块的延迟卸载
    public void cancelRemoval(ChunkPos chunkPos) {
        ScheduledFuture<?> future = pendingRemovals.remove(chunkPos);
        if (future != null) future.cancel(false);
    }

    // 安排区块延迟卸载
    protected void scheduleRemoval(ChunkPos chunkPos) {
        cancelRemoval(chunkPos);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            ConcurrentHashMap<BlockPos, V> removed = cache.remove(chunkPos);
            if (removed != null && !removed.isEmpty()) {
                onChunkUnloaded(chunkPos, removed);
            }
            pendingRemovals.remove(chunkPos);
        }, removalDelayMs, TimeUnit.MILLISECONDS);
        pendingRemovals.put(chunkPos, future);
    }

    // 子类可覆写，用于记录卸载日志等
    protected void onChunkUnloaded(ChunkPos chunkPos, ConcurrentHashMap<BlockPos, V> removedData) {}

    // 获取某个位置的缓存值（只读）
    public V get(BlockPos pos) {
        if (pos == null) return null;
        ChunkPos cp = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(cp);
        return inner != null ? inner.get(pos) : null;
    }

    // 更新缓存的核心方法：子类调用此方法来完成实际存储
    // 返回值表示是否真正发生了变更（用于外部决定是否写数据库等）
    public boolean updateCache(BlockPos pos, V newData, java.util.function.BiPredicate<V, V> sameDataChecker) {
        ChunkPos chunkPos = new ChunkPos(pos);
        cancelRemoval(chunkPos); // 有新数据，取消延迟卸载

        // 获取或创建区块内层 Map
        ConcurrentHashMap<BlockPos, V> inner = cache.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>());

        V oldData = inner.get(pos);
        if (oldData != null && sameDataChecker.test(oldData, newData)) {
            // 数据完全相同，无需更新
            return false;
        }
        // 更新数据
        inner.put(pos.immutable(), newData);
        return true;
    }

    // 删除单个位置
    public void removeAt(BlockPos pos) {
        if (pos == null) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(chunkPos);
        if (inner != null) {
            inner.remove(pos);
            if (inner.isEmpty()) {
                scheduleRemoval(chunkPos);
            }
        }
    }

    // 立即删除区块
    public void removeChunkInMemory(ChunkPos chunkPos) {
        if (chunkPos == null) return;
        cancelRemoval(chunkPos);
        cache.remove(chunkPos);
    }

    // 清空所有
    public void clearAll() {
        pendingRemovals.values().forEach(future -> future.cancel(false));
        pendingRemovals.clear();
        cache.clear();
    }

    public boolean contains(BlockPos pos) {
        if (pos == null) return false;
        ChunkPos cp = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(cp);
        return inner != null && inner.containsKey(pos);
    }

    public int getChunkCount() {
        return cache.size();
    }

    public String dumpAllEntries(Function<V, String> formatter) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(getClass().getSimpleName()).append(" Dump ===\n");
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
        for (Map.Entry<ChunkPos, ConcurrentHashMap<BlockPos, V>> entry : cache.entrySet()) {
            ChunkPos cp = entry.getKey();
            ConcurrentHashMap<BlockPos, V> inner = entry.getValue();
            if (inner.isEmpty()) continue;
            sb.append("Chunk ").append(cp.x).append(", ").append(cp.z).append(":\n");
            for (Map.Entry<BlockPos, V> e : inner.entrySet()) {
                BlockPos pos = e.getKey();
                V data = e.getValue();
                sb.append("  ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ())
                        .append(" -> ").append(formatter.apply(data)).append("\n");
            }
        }
        sb.append("Total entries: ").append(total).append("\n");
        return sb.toString();
    }

    // 可选：关闭线程池
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
}