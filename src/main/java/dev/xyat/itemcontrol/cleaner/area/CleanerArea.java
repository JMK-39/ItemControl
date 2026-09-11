package dev.xyat.itemcontrol.cleaner.area;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class CleanerArea {
    private final UUID ownerId;
    private final String ownerName;
    private final String dimension;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public CleanerArea(UUID ownerId, String ownerName, String dimension, BlockPos first, BlockPos second) {
        this(ownerId, ownerName, dimension,
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public CleanerArea(UUID ownerId, String ownerName, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.ownerId = ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.dimension = dimension == null ? "" : dimension;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public String dimension() {
        return dimension;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean contains(String dim, BlockPos pos) {
        return this.dimension.equals(dim)
                && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean overlaps(CleanerArea other) {
        if (!this.dimension.equals(other.dimension)) {
            return false;
        }
        return this.minX <= other.maxX && this.maxX >= other.minX
                && this.minY <= other.maxY && this.maxY >= other.minY
                && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    public int gridCost() {
        long x = (long) maxX - minX + 1L;
        long y = (long) maxY - minY + 1L;
        long z = (long) maxZ - minZ + 1L;
        long cost = Math.max(1L, x * y * z);
        return cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("OwnerId", ownerId);
        tag.putString("OwnerName", ownerName);
        tag.putString("Dimension", dimension);
        tag.putInt("MinX", minX);
        tag.putInt("MinY", minY);
        tag.putInt("MinZ", minZ);
        tag.putInt("MaxX", maxX);
        tag.putInt("MaxY", maxY);
        tag.putInt("MaxZ", maxZ);
        return tag;
    }

    public static CleanerArea load(CompoundTag tag) {
        return new CleanerArea(
                tag.getUUID("OwnerId"),
                tag.getString("OwnerName"),
                tag.getString("Dimension"),
                tag.getInt("MinX"),
                tag.getInt("MinY"),
                tag.getInt("MinZ"),
                tag.getInt("MaxX"),
                tag.getInt("MaxY"),
                tag.getInt("MaxZ"));
    }
}
