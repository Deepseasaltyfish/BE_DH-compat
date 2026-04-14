//TODO: have to iplement Persistent storage if IR cant add bedFill data in Rail_Gag
//package com.deepseasaltyfish.BeDhCompat.common.ImmersiveRairoading;
//
//import com.deepseasaltyfish.BeDhCompat.util.DebugLogger;
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.registries.BuiltInRegistries;
//import net.minecraft.nbt.*;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.block.state.BlockState;
//
//import java.io.*;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.TimeUnit;
//
///**
// * IR 铁轨颜色缓存
// * - 父级（轨道段）永久缓存 + 持久化（数量极少）
// * - 子级（轨道节点）LRU 缓存，限制大小，不持久化（数量多，但淘汰后可自动从父级重建）
// */
//public class IRColorCache {
//    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRColorCache.class);
//    private static final String CACHE_FILE_NAME = "ir_parent_colors.dat";
//
//    // 父级颜色缓存（永久，数量少）
//    private static final ConcurrentHashMap<BlockPos, BlockState> parentCache = new ConcurrentHashMap<>();
//
//    // 子级颜色缓存（LRU，限制大小，不持久化）
//    private static final Cache<BlockPos, BlockState> childCache = CacheBuilder.newBuilder()
//            .maximumSize(50000)                     // 最大条目数，可根据服务器内存调整
//            .expireAfterAccess(30, TimeUnit.MINUTES) // 30分钟未访问则过期
//            .removalListener(notification ->
//                    LOGGER.debug("Evicted child color for {}: {}", notification.getKey(), notification.getCause()))
//            .build();
//
//    // 防止重复解析标志（仅父级）
//    private static final ConcurrentHashMap<BlockPos, Boolean> processingFlag = new ConcurrentHashMap<>();
//
//    private static Path cacheFile = null;
//
//    // ---------- 初始化与持久化 ----------
//    public static void init(Path worldDir) {
//        if (worldDir == null) return;
//        cacheFile = worldDir.resolve(CACHE_FILE_NAME);
//        if (Files.exists(cacheFile)) {
//            loadParentCache();
//        } else {
//            LOGGER.debug("No existing IR parent cache file, starting fresh");
//        }
//    }
//
//    private static void loadParentCache() {
//        try (DataInputStream dis = new DataInputStream(new FileInputStream(cacheFile.toFile()))) {
//            CompoundTag root = NbtIo.read(dis);
//            if (root != null && root.contains("parentCache", Tag.TAG_LIST)) {
//                ListTag list = root.getList("parentCache", Tag.TAG_COMPOUND);
//                for (int i = 0; i < list.size(); i++) {
//                    CompoundTag entry = list.getCompound(i);
//                    BlockPos pos = BlockPos.of(entry.getLong("pos"));
//                    String blockId = entry.getString("block");
//                    BlockState state = blockStateFromId(blockId);
//                    if (state != null && state.getBlock() != Blocks.AIR) {
//                        parentCache.put(pos, state);
//                    }
//                }
//                LOGGER.info("Loaded {} IR parent colors from disk", parentCache.size());
//            }
//        } catch (Exception e) {
//            LOGGER.error("Failed to load IR parent cache, starting fresh", e);
//        }
//    }
//
//    private static void saveParentCache() {
//        if (cacheFile == null) return;
//        try {
//            Files.createDirectories(cacheFile.getParent());
//            CompoundTag root = new CompoundTag();
//            ListTag list = new ListTag();
//            for (var entry : parentCache.entrySet()) {
//                CompoundTag e = new CompoundTag();
//                e.putLong("pos", entry.getKey().asLong());
//                e.putString("block", entry.getValue().getBlock().toString());
//                list.add(e);
//            }
//            root.put("parentCache", list);
//            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(cacheFile.toFile()))) {
//                NbtIo.write(root, dos);
//            }
//            LOGGER.debug("Saved {} IR parent colors to disk", parentCache.size());
//        } catch (Exception e) {
//            LOGGER.error("Failed to save IR parent cache", e);
//        }
//    }
//
//    // ---------- 颜色提取（主线程调用） ----------
//    public static void extractIRColor(BlockPos pos, CompoundTag tag, boolean isParent) {
//        if (pos == null || tag == null) return;
//
//        if (isParent) {
//            // 父级：永久缓存 + 持久化
//            if (parentCache.containsKey(pos)) return;
//            if (processingFlag.putIfAbsent(pos, Boolean.TRUE) != null) return;
//            try {
//                BlockState state = parseColorFromParentNBT(tag);
//                if (state != null && state.getBlock() != Blocks.AIR) {
//                    parentCache.put(pos, state);
//                    saveParentCache(); // 同步保存，父级数量少，频率低
//                    LOGGER.debug("Cached parent color at {}: {}", pos, state.getBlock());
//                }
//            } finally {
//                processingFlag.remove(pos);
//            }
//        } else {
//            // 子级：尝试从父级缓存获取颜色，并存入 LRU 子级缓存
//            BlockPos parentPos = getParentPos(pos, tag);
//            if (parentPos == null) return;
//            BlockState parentState = parentCache.get(parentPos);
//            if (parentState != null) {
//                childCache.put(pos, parentState);
//                LOGGER.debug("Cached child color at {} from parent {}", pos, parentPos);
//            } else {
//                LOGGER.debug("Parent {} not cached yet for child {}", parentPos, pos);
//            }
//        }
//    }
//
//    // ---------- 颜色查询（DH 回调线程调用，只读） ----------
//    public static BlockState getColor(BlockPos pos) {
//        if (pos == null) return null;
//        // 先查父级缓存（如果是父级位置）
//        BlockState state = parentCache.get(pos);
//        if (state != null) return state;
//        // 再查子级 LRU 缓存
//        state = childCache.getIfPresent(pos);
//        if (state != null) return state;
//        return null;
//    }
//
//    // ---------- 辅助方法 ----------
//    private static BlockState parseColorFromParentNBT(CompoundTag tag) {
//        try {
//            CompoundTag info = tag.getCompound("info");
//            CompoundTag settings = info.getCompound("settings");
//            CompoundTag bedItem = settings.getCompound("bedItem");
//            String id = bedItem.getString("id");
//            if (id.isEmpty()) return null;
//            return blockStateFromId(id);
//        } catch (Exception e) {
//            LOGGER.debug("Failed to parse parent color: {}", e.getMessage());
//            return null;
//        }
//    }
//
//    private static BlockPos getParentPos(BlockPos childPos, CompoundTag tag) {
//        if (!tag.contains("parent", Tag.TAG_COMPOUND)) return null;
//        CompoundTag parentTag = tag.getCompound("parent");
//        int dx = parentTag.getInt("X");
//        int dy = parentTag.getInt("Y");
//        int dz = parentTag.getInt("Z");
//        if (dx == 0 && dy == 0 && dz == 0) return null;
//        return childPos.offset(dx, dy, dz);
//    }
//
//    private static BlockState blockStateFromId(String id) {
//        if (id == null || id.isEmpty()) return Blocks.AIR.defaultBlockState();
//        try {
//            ResourceLocation rl = new ResourceLocation(id);
//            Block block = BuiltInRegistries.BLOCK.get(rl);
//            return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
//        } catch (Exception e) {
//            LOGGER.warn("Invalid block id: {}", id);
//            return Blocks.AIR.defaultBlockState();
//        }
//    }
//
//    // ---------- 生命周期管理 ----------
//    public static void clearAll() {
//        parentCache.clear();
//        childCache.invalidateAll();
//        processingFlag.clear();
//        if (cacheFile != null) {
//            try {
//                Files.deleteIfExists(cacheFile);
//            } catch (IOException e) {
//                LOGGER.warn("Failed to delete IR cache file", e);
//            }
//        }
//        LOGGER.debug("Cleared all IR color caches");
//    }
//
//    // 可选：获取缓存大小
//    public static long size() {
//        return parentCache.size() + childCache.size();
//    }
//}