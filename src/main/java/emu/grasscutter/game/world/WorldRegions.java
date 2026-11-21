package emu.grasscutter.game.world;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.proto.LimitedRegionInfo._LimitedRegionInfo;
import java.util.Set;
import java.util.TreeSet;

/** Builds the existing world-area snapshot from resource parent-area IDs. */
public final class WorldRegions {
    private WorldRegions() {}

    private static Set<Integer> areaIds;

    /**
     * Every world area id.
     *
     * <p>The small parent-area numbers from WorldAreaConfigData, the same space scene points use -
     * NOT WorldAreaData.getId(), which is an internal composite of (areaID2 << 16) + areaID1 and
     * runs past 2^32, so it cannot be a wire value.
     */
    public static synchronized Set<Integer> allAreaIds() {
        if (areaIds != null) return areaIds;

        var ids = new TreeSet<Integer>();
        GameData.getWorldAreaDataMap().values().forEach(area -> ids.add(area.getParentArea()));
        ids.remove(0);
        areaIds = ids;
        return ids;
    }

    /** Every area, as the sub-message the scene notifies carry. */
    public static _LimitedRegionInfo unrestricted() {
        return _LimitedRegionInfo.newBuilder().addAllLimitedRegionList(allAreaIds()).build();
    }
}
