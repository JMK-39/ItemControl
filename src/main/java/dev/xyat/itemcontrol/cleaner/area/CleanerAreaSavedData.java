package dev.xyat.itemcontrol.cleaner.area;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CleanerAreaSavedData extends SavedData {
    public static final String DATA_NAME = "itemcontrol_cleaner_areas";
    private static final long MAX_INDEXED_CHUNKS_PER_AREA = 1024L;

    private final List<CleanerArea> areas = new ArrayList<>();
    private final List<CleanerArea> readOnlyAreas = Collections.unmodifiableList(areas);
    private final Map<String, Map<Long, List<CleanerArea>>> areasByDimensionAndChunk = new HashMap<>();
    private final Map<String, List<CleanerArea>> oversizedAreasByDimension = new HashMap<>();

    public static CleanerAreaSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(CleanerAreaSavedData::load, CleanerAreaSavedData::new, DATA_NAME);
    }

    public List<CleanerArea> getAreas() {
        return readOnlyAreas;
    }

    public CleanerArea findContaining(String dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return null;
        }

        List<CleanerArea> oversizedAreas = oversizedAreasByDimension.get(dimension);
        if (oversizedAreas != null) {
            for (CleanerArea area : oversizedAreas) {
                if (area.contains(dimension, pos)) {
                    return area;
                }
            }
        }

        Map<Long, List<CleanerArea>> chunkIndex = areasByDimensionAndChunk.get(dimension);
        if (chunkIndex == null) {
            return null;
        }

        List<CleanerArea> candidates = chunkIndex.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (candidates == null) {
            return null;
        }

        for (CleanerArea area : candidates) {
            if (area.contains(dimension, pos)) {
                return area;
            }
        }
        return null;
    }

    public boolean isProtected(String dimension, BlockPos pos) {
        return findContaining(dimension, pos) != null;
    }

    public void addArea(CleanerArea area) {
        areas.add(area);
        rebuildIndex();
        setDirty();
    }

    public void removeArea(CleanerArea area) {
        if (areas.remove(area)) {
            rebuildIndex();
            setDirty();
        }
    }

    public int removeByOwner(java.util.UUID ownerId) {
        int before = areas.size();
        areas.removeIf(area -> area.ownerId().equals(ownerId));
        int removed = before - areas.size();
        if (removed > 0) {
            rebuildIndex();
            setDirty();
        }
        return removed;
    }

    public int clearAllAreas() {
        int removed = areas.size();
        areas.clear();
        areasByDimensionAndChunk.clear();
        oversizedAreasByDimension.clear();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    private void rebuildIndex() {
        areasByDimensionAndChunk.clear();
        oversizedAreasByDimension.clear();

        for (CleanerArea area : areas) {
            int minChunkX = area.minX() >> 4;
            int maxChunkX = area.maxX() >> 4;
            int minChunkZ = area.minZ() >> 4;
            int maxChunkZ = area.maxZ() >> 4;
            long chunkCountX = (long) maxChunkX - minChunkX + 1L;
            long chunkCountZ = (long) maxChunkZ - minChunkZ + 1L;
            long chunkCount = chunkCountX * chunkCountZ;

            if (chunkCount <= 0L || chunkCount > MAX_INDEXED_CHUNKS_PER_AREA) {
                oversizedAreasByDimension
                        .computeIfAbsent(area.dimension(), ignored -> new ArrayList<>())
                        .add(area);
                continue;
            }

            Map<Long, List<CleanerArea>> chunkIndex = areasByDimensionAndChunk
                    .computeIfAbsent(area.dimension(), ignored -> new HashMap<>());

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunkIndex.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), ignored -> new ArrayList<>())
                            .add(area);
                }
            }
        }
    }

    public static CleanerAreaSavedData load(CompoundTag tag) {
        CleanerAreaSavedData data = new CleanerAreaSavedData();
        if (tag.contains("Areas", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Areas", Tag.TAG_COMPOUND);
            for (Tag value : list) {
                if (value instanceof CompoundTag areaTag) {
                    try {
                        data.areas.add(CleanerArea.load(areaTag));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        data.rebuildIndex();
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (CleanerArea area : areas) {
            list.add(area.save());
        }
        tag.put("Areas", list);
        return tag;
    }
}
