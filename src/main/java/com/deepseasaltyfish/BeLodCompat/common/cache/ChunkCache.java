package com.deepseasaltyfish.BeLodCompat.common.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 通用区块缓存管理器，封装了：
 * - 分区块的 ConcurrentHashMap 缓存
 * - 延迟卸载任务调度
 * - 基本增删改查操作
 * 行为与旧版 IRBlockDataCache/LTBlockDataCache 完全一致。
 *
 * @param <V> 缓存值类型
 */
public class ChunkCache<V> {
    // 缓存：区块 -> 位置 -> 数据
    protected final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, V>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<ChunkPos, ScheduledFuture<?>> pendingRemovals = new ConcurrentHashMap<>();
    private final long removalDelayMs;

    public ChunkCache(long removalDelayMs) {
        this.removalDelayMs = removalDelayMs;
    }

    /**
     * 取消指定区块的延迟卸载任务（如果存在）。
     * 行为与旧代码中 pendingRemovals.remove + cancel 一致。
     */
    public void cancelRemoval(ChunkPos chunkPos) {
        ScheduledFuture<?> future = pendingRemovals.remove(chunkPos);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 安排延迟卸载区块（用于主动卸载，如 removeChunkInMemory）。
     * @param onRemove 卸载时的回调（用于日志），可为 null
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
     * 更新缓存（与旧代码 put 逻辑完全一致）：
     * - 总是取消该区块的延迟卸载任务
     * - 如果新旧数据相同（由 sameChecker 判定），则返回 false 且不更新内存
     * - 否则更新内存，返回 true
     * 注意：返回值仅表示内存是否变化，外部不应依赖它来决定数据库操作（旧代码中数据库总是执行）
     */
    public boolean put(BlockPos pos, V newData, BiPredicate<V, V> sameChecker) {
        ChunkPos chunkPos = new ChunkPos(pos);
        // 1. 取消延迟卸载（与旧代码 pendingRemovals.remove + cancel 一致）
        cancelRemoval(chunkPos);

        // 2. 获取或创建区块内层 Map
        ConcurrentHashMap<BlockPos, V> inner = cache.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>());

        // 3. 检查旧数据是否相同
        V oldData = inner.get(pos);
        if (oldData != null && sameChecker.test(oldData, newData)) {
            return false; // 数据相同，不更新内存
        }

        // 4. 更新内存
        inner.put(pos.immutable(), newData);
        return true;
    }

    /**
     * 获取缓存值（只读）
     */
    public V get(BlockPos pos) {
        if (pos == null) return null;
        ChunkPos cp = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(cp);
        return inner != null ? inner.get(pos) : null;
    }

    /**
     * 移除单个位置（与旧代码 removeAt 完全一致）：
     * - 从区块内层 Map 中移除该位置
     * - 如果区块变空，则立即从 cache 中移除该区块（不延迟，也不取消 pendingRemovals）
     * 注意：旧代码中没有取消 pendingRemovals，这里也不做取消
     */
    public void removeAt(BlockPos pos) {
        if (pos == null) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(chunkPos);
        if (inner != null) {
            inner.remove(pos);
            if (inner.isEmpty()) {
                cache.remove(chunkPos);
                // 注意：旧代码没有主动取消 pendingRemovals，但如果有残留任务，执行时 cache 已空，无害
            }
        }
    }

    /**
     * 主动卸载整个区块（延迟执行，与旧代码 removeChunkInMemory 完全一致）
     * @param onRemove 卸载时的回调，用于日志
     */
    public void removeChunkInMemory(ChunkPos chunkPos, BiConsumer<ChunkPos, ConcurrentHashMap<BlockPos, V>> onRemove) {
        if (chunkPos == null) return;
        scheduleRemoval(chunkPos, onRemove);
    }

    /**
     * 清空所有缓存，取消所有待执行的卸载任务
     */
    public void clearAll() {
        pendingRemovals.values().forEach(future -> future.cancel(false));
        pendingRemovals.clear();
        cache.clear();
    }

    /**
     * 检查某个位置是否在缓存中
     */
    public boolean contains(BlockPos pos) {
        if (pos == null) return false;
        ChunkPos cp = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, V> inner = cache.get(cp);
        return inner != null && inner.containsKey(pos);
    }

    /**
     * 返回当前缓存的区块数量（注意：不是条目数）
     */
    public int getChunkCount() {
        return cache.size();
    }

    /**
     * 获取内部缓存 Map（仅用于特殊情况，如 LT 的 loadChunkFromDB 需要直接操作）
     * 请谨慎使用
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
     * 关闭调度器（在 Mod 卸载时调用）
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
}