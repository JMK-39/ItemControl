package dev.xyat.itemcontrol.cleaner.client;

import dev.xyat.itemcontrol.cleaner.area.CleanerArea;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CleanerAreaClientState {
    private static final List<CleanerArea> AREAS = new ArrayList<>();
    private static BlockPos start;
    private static BlockPos end;

    public static void setAreas(List<CleanerArea> areas) {
        AREAS.clear();
        AREAS.addAll(areas);
    }

    public static List<CleanerArea> getAreas() {
        return Collections.unmodifiableList(AREAS);
    }

    public static void setStart(BlockPos pos) {
        start = pos;
    }

    public static void setEnd(BlockPos pos) {
        end = pos;
    }

    public static BlockPos getStart() {
        return start;
    }

    public static BlockPos getEnd() {
        return end;
    }

    public static void clearSelection() {
        start = null;
        end = null;
    }

    public static boolean isOwn(CleanerArea area, UUID id) {
        return area.ownerId().equals(id);
    }
}
