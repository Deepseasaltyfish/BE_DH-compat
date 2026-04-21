package com.deepseasaltyfish.BeLodCompat.common.cache;

import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.*;

public class IRBlockDataCache {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRBlockDataCache.class);

    // Cache: chunk -> pos -> IRBlockData (BlockState only, color unused for now)
    private static final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, IRBlockData>> chunkColorMap = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Map<ChunkPos, ScheduledFuture<?>> pendingRemovals = new ConcurrentHashMap<>();
    private static final long REMOVAL_DELAY_MS = 30_000;
    public static class IRBlockData {
        private final BlockState blockState;

        IRBlockData(BlockState blockState) {
            this.blockState = blockState;
        }

        BlockState getBlockState() { return blockState; }
    }

    /**
     * Extract color from IR rail NBT and cache it.
     * @param pos block position
     * @param tag the block entity NBT (instanceData for rail, or full BE tag for parent)
     * @param isParent true for block_rail (parent), false for block_rail_gag (child)
     * @return true if successfully cached (or overridden), false if fallback used (put handled by caller)
     */
    public static boolean extractIRColor(BlockPos pos, CompoundTag tag, boolean isParent) {
        // Check config override first
        if (ModConfigs.overrideIrRailBlock) {
            String overrideId = ModConfigs.getValidatedOverrideId();
            if (overrideId != null && !overrideId.isEmpty()) {
                LOGGER.debug("IR rail override applied at {}, using {}", pos, overrideId);
                return put(pos, overrideId);
            }
        }

        try {
            CompoundTag bedItem;
            if (isParent) {
                CompoundTag info = tag.getCompound("info");
                CompoundTag settings = info.getCompound("settings");
                bedItem = settings.getCompound("bedItem");
            } else {
                if (!tag.contains("railBedCache", Tag.TAG_COMPOUND)) {
                    LOGGER.debug("No railBedCache found at {}, this is old version IR or railBedCache not stored", pos);
                }
                bedItem = tag.getCompound("railBedCache");
            }

            String id = bedItem.getString("id");
            if (id.isEmpty()) {
                LOGGER.debug("Empty bedItem id at {}, using soul sand (handled by put fallback)", pos);
                return put(pos, ModConfigs.getValidatedOverrideId());
            }
            return put(pos, id);
        } catch (Exception e) {
            LOGGER.error("Failed to extract IR color at {}", pos, e);
            return false;
        }
    }

    public static boolean put(BlockPos pos, String blockStr) {
        ChunkPos chunkPos = new ChunkPos(pos);

        // 取消该区块的延迟卸载任务（如果有）
        ScheduledFuture<?> existing = pendingRemovals.remove(chunkPos);
        if (existing != null) {
            existing.cancel(false);
        }

        BlockState convertedState = BlockDataUtil.toDefaultBlockState(blockStr, pos, LOGGER, false);
        if (convertedState == null) {
            LOGGER.error("Fail to convert to BlockState for IR at {}", pos);
            return false;
        }

        // 获取当前内存中的旧数据
        ConcurrentHashMap<BlockPos, IRBlockData> innerMap = chunkColorMap.get(chunkPos);
        IRBlockData oldData = innerMap != null ? innerMap.get(pos) : null;
        if (oldData != null && oldData.getBlockState().equals(convertedState)) {
            // 数据相同，无需更新
            return true;
        }

        // 更新内存缓存
        chunkColorMap.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>())
                .put(pos.immutable(), new IRBlockData(convertedState));
        return true;
    }

    /**
     * Retrieves the cached BlockState for the given position.
     * <p>
     * This method is called concurrently from DH's LOD Builder threads
     * ("DH-LOD Builder Thread[x]"). The implementation must be thread-safe.
     * The underlying cache uses ConcurrentHashMap, which is safe for concurrent reads.
     *
     * @param pos the block position
     * @return the cached BlockState, or null if not found
     */
    public static BlockState getBlockStateAt(BlockPos pos) {
        if (pos == null) {
            LOGGER.error("IRBlockData getBlockStateAt called with null pos");
            return null;
        }
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, IRBlockData> innerMap = chunkColorMap.get(chunkPos);
        if (innerMap == null) {
            LOGGER.debug("No IRBlockData at chunk {} block {} (No chunk data)", chunkPos, pos);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        IRBlockData data = innerMap.get(pos);
        if (data == null) {
            LOGGER.debug("No IRBlockData at chunk {} block {}", chunkPos, pos);
            return Blocks.RED_WOOL.defaultBlockState();
        }
        return data.getBlockState();
    }

    public static void removeChunkInMemory(ChunkPos chunkPos) {
        if (chunkPos == null) return;

        ScheduledFuture<?> existing = pendingRemovals.remove(chunkPos);
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Map<BlockPos, IRBlockData> inner = chunkColorMap.remove(chunkPos);
            if (inner != null) {
                LOGGER.debug("Delayed removal of chunk {} with {} entries", chunkPos, inner.size());
            }
            pendingRemovals.remove(chunkPos);
        }, REMOVAL_DELAY_MS, TimeUnit.MILLISECONDS);
        pendingRemovals.put(chunkPos, future);
    }

    public static void removeAt(BlockPos pos) {
        if (pos == null) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, IRBlockData> inner = chunkColorMap.get(chunkPos);
        if (inner != null) {
            inner.remove(pos);
            if (inner.isEmpty()) {
                chunkColorMap.remove(chunkPos);
            }
        }
    }

    public static void clearAll() {
        pendingRemovals.values().forEach(future -> future.cancel(false));
        pendingRemovals.clear();
        chunkColorMap.clear();
    }

    public static boolean contains(BlockPos pos) {
        if (pos == null) return false;
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, IRBlockData> innerMap = chunkColorMap.get(chunkPos);
        return innerMap != null && innerMap.containsKey(pos);
    }

    public static int getCacheSize() {
        return chunkColorMap.size();
    }

    //debug

    public static String dumpAllEntries() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IRBlockDataCache Dump ===\n");

        // 第一次遍历：统计总数
        int total = 0;
        for (ConcurrentHashMap<BlockPos, IRBlockData> innerMap : chunkColorMap.values()) {
            total += innerMap.size();
        }

        if (total == 0) {
            sb.append("(empty)\n");
            return sb.toString();
        }

        if (total > 100) {
            sb.append("Total entries: ").append(total).append("\n");
            return sb.toString();
        }

        // 第二次遍历：输出详细信息（当总数 ≤ 100 时）
        for (Map.Entry<ChunkPos, ConcurrentHashMap<BlockPos, IRBlockData>> chunkEntry : chunkColorMap.entrySet()) {
            ConcurrentHashMap<BlockPos, IRBlockData> innerMap = chunkEntry.getValue();
            if (innerMap.isEmpty()) continue; // 跳过空区块
            ChunkPos cp = chunkEntry.getKey();
            sb.append("Chunk ").append(cp.x).append(", ").append(cp.z).append(":\n");
            for (Map.Entry<BlockPos, IRBlockData> entry : innerMap.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue().getBlockState();
                ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                sb.append("  ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ())
                        .append(" -> ").append(rl).append("\n");
            }
        }
        sb.append("Total entries: ").append(total).append("\n");
        return sb.toString();
    }
}