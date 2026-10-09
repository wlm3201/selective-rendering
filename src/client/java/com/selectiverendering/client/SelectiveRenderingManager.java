package com.selectiverendering.client;

import com.selectiverendering.client.compat.Platform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SelectiveRenderingManager {
    public static final int TRANSPARENCY_STEP = 1;
    public static final int MAX_TRANSPARENCY = 100;

    public static final Mode DEFAULT_MODE = Mode.OFF;
    public static final int DEFAULT_TRANSPARENCY = 50;
    public static final String DEFAULT_WAND = "minecraft:breeze_rod";
    public static final boolean DEFAULT_FULL_BRIGHT = true;

    public enum Mode {
        OFF("off"),
        REGION_INSIDE_LISTED("region_inside_listed"),
        REGION_INSIDE_UNLISTED("region_inside_unlisted"),
        REGION_INSIDE("region_inside"),
        REGION_OUTSIDE_LISTED("region_outside_listed"),
        REGION_OUTSIDE_UNLISTED("region_outside_unlisted"),
        REGION_OUTSIDE("region_outside"),
        BLACKLIST("blacklist"),
        WHITELIST("whitelist");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public Component displayName() {
            return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
        }

        public Mode cycle(int direction) {
            Mode[] values = values();
            return values[Math.floorMod(ordinal() + direction, values.length)];
        }

        public boolean usesRegion() {
            return this != OFF && this != BLACKLIST && this != WHITELIST;
        }

        public boolean usesList() {
            return this != OFF && this != REGION_INSIDE && this != REGION_OUTSIDE;
        }

        public boolean hidesListed() {
            return this == BLACKLIST
                    || this == REGION_INSIDE_LISTED
                    || this == REGION_OUTSIDE_LISTED;
        }
    }

    private static final int LIGHT_RANGE = 15;

    private static final int MAX_SUBTRACT_FRAGMENTS = 4096;

    private static final int MAX_CACHED_ENTRIES = 4096;

    private static final AtomicLong RELIGHT_GENERATION = new AtomicLong();

    private static final List<Region> RETIRED_REGIONS = new ArrayList<>();

    private static final Object LOCK = new Object();
    private static final List<BlockMatchRule> RULES = new ArrayList<>();

    private static final List<BlockMatchRule> RECORDED = new ArrayList<>();

    private static final List<Region> REGIONS = new ArrayList<>();

    private static final List<Preset> PRESETS = new ArrayList<>();

    private static volatile List<String> ruleSourceInput = List.of();
    private static volatile List<String> regionSourceInput = List.of();
    private static volatile List<String> recordSourceInput = List.of();

    public enum RecordMode {
        LISTED("record_listed"),
        UNLISTED("record_unlisted");

        private final String key;

        RecordMode(String key) {
            this.key = key;
        }

        public Component displayName() {
            return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
        }
    }

    public static final RecordMode DEFAULT_RECORD_MODE = RecordMode.LISTED;

    public enum RecordApplyMode {
        ADD("record_add"),

        SUBTRACT("record_subtract");

        private final String key;

        RecordApplyMode(String key) {
            this.key = key;
        }

        public Component displayName() {
            return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
        }
    }

    public static final RecordApplyMode DEFAULT_RECORD_APPLY_MODE = RecordApplyMode.ADD;

    public static final boolean DEFAULT_INVERT = false;

    public static final boolean DEFAULT_PASS_THROUGH = true;

    private static Mode mode = DEFAULT_MODE;
    private static int transparency = DEFAULT_TRANSPARENCY;
    private static RecordMode recordMode = DEFAULT_RECORD_MODE;
    private static RecordApplyMode recordApplyMode = DEFAULT_RECORD_APPLY_MODE;
    private static boolean invert = DEFAULT_INVERT;
    private static String wand = DEFAULT_WAND;
    private static boolean fullBright = DEFAULT_FULL_BRIGHT;
    private static boolean passThrough = DEFAULT_PASS_THROUGH;
    private static BlockPos corner1;
    private static BlockPos corner2;
    private static AABB region;

    private static volatile RenderConfig config = RenderConfig.initial();

    private static volatile int selectedCorner = -1;

    private static final ThreadLocal<StateCache> STATE_CACHE =
            ThreadLocal.withInitial(StateCache::new);

    private record RenderConfig(
            Mode mode,
            int transparency,
            boolean invert,
            List<BlockMatchRule> rules,
            List<BlockMatchRule> recorded,
            List<Region> regions,
            List<Preset> presets,
            RecordMode recordMode,
            RecordApplyMode recordApplyMode,
            String wand,
            boolean fullBright,
            boolean passThrough,
            BlockPos corner1,
            BlockPos corner2,
            AABB region,
            int alpha,
            Region union) {
        private static RenderConfig initial() {
            return of(
                    DEFAULT_MODE,
                    DEFAULT_TRANSPARENCY,
                    DEFAULT_INVERT,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    DEFAULT_RECORD_MODE,
                    DEFAULT_RECORD_APPLY_MODE,
                    DEFAULT_WAND,
                    DEFAULT_FULL_BRIGHT,
                    DEFAULT_PASS_THROUGH,
                    null,
                    null,
                    null);
        }

        private static RenderConfig of(
                Mode mode,
                int transparency,
                boolean invert,
                List<BlockMatchRule> rules,
                List<BlockMatchRule> recorded,
                List<Region> regions,
                List<Preset> presets,
                RecordMode recordMode,
                RecordApplyMode recordApplyMode,
                String wand,
                boolean fullBright,
                boolean passThrough,
                @Nullable BlockPos corner1,
                @Nullable BlockPos corner2,
                @Nullable AABB region) {
            return new RenderConfig(
                    mode,
                    transparency,
                    invert,
                    rules,
                    recorded,
                    regions,
                    presets,
                    recordMode,
                    recordApplyMode,
                    wand,
                    fullBright,
                    passThrough,
                    corner1,
                    corner2,
                    region,
                    Math.round(255F * (MAX_TRANSPARENCY - transparency) / MAX_TRANSPARENCY),
                    unionOf(regions));
        }
    }

    private static final class StateCache {
        private RenderConfig owner;

        private final Map<BlockState, Integer> alpha = new HashMap<>();
        private final Map<BlockState, Integer> listed = new HashMap<>();
        private final Map<Long, Integer> sections = new HashMap<>();

        private void reset(RenderConfig owner) {
            this.owner = owner;
            this.alpha.clear();
            this.listed.clear();
            this.sections.clear();
        }
    }

    private SelectiveRenderingManager() {}

    public static void load() {
        ModConfig config = ModConfig.get();

        synchronized (LOCK) {
            RULES.clear();
            RECORDED.clear();
            REGIONS.clear();

            mode = config.mode == null ? Mode.OFF : config.mode;
            transparency = Math.clamp(config.transparency, 0, MAX_TRANSPARENCY);
            recordMode = config.recordMode == null ? DEFAULT_RECORD_MODE : config.recordMode;
            recordApplyMode =
                    config.recordApplyMode == null
                            ? DEFAULT_RECORD_APPLY_MODE
                            : config.recordApplyMode;
            invert = config.invert;
            wand = config.wand == null || config.wand.isBlank() ? DEFAULT_WAND : config.wand.trim();
            fullBright = config.fullBright;
            passThrough = config.passThrough;

            if (config.rules != null) {
                for (String source : config.rules) {
                    BlockMatchRule rule = BlockMatchRule.parse(source);
                    if (rule != null) {
                        RULES.add(rule);
                    }
                }
            }

            corner1 = toBlockPos(config.regionPos1);
            corner2 = toBlockPos(config.regionPos2);
            selectedCorner = corner2 != null ? 1 : corner1 != null ? 0 : -1;
            updateRegion();

            if (config.recorded != null) {
                for (String source : config.recorded) {
                    BlockMatchRule rule = BlockMatchRule.parse(source);
                    if (rule != null) {
                        RECORDED.add(rule);
                    }
                }
            }

            if (config.regions != null) {
                for (int[] values : config.regions) {
                    Region added = toRegion(values);
                    if (added != null) {
                        REGIONS.add(added);
                    }
                }
            }

            if (config.presets != null) {
                for (Preset preset : config.presets) {
                    if (preset != null) {
                        PRESETS.add(preset);
                    }
                }
            }

            publish();
            rememberSources();
        }

        Log.say(
                "[state] loaded: mode {}, transparency {}%, {} rule(s), {} region(s), {} preset(s)",
                mode, transparency, RULES.size(), REGIONS.size(), PRESETS.size());
    }

    public static Mode getMode() {
        return config.mode();
    }

    public static void setMode(Mode newMode) {
        synchronized (LOCK) {
            if (mode == newMode) {
                return;
            }

            mode = newMode;
            commit();
        }

        Log.say("[state] mode {}", newMode.name());
        rebuildChunks();
    }

    public static void cycleMode(int direction) {
        setMode(getMode().cycle(direction));
    }

    public static int getTransparency() {
        return config.transparency();
    }

    public static void setTransparency(int value) {
        int clamped = Math.clamp(value, 0, MAX_TRANSPARENCY);

        synchronized (LOCK) {
            if (clamped == transparency) {
                return;
            }

            transparency = clamped;
            commit();
        }

        Log.say("[state] transparency {}%", clamped);
        rebuildHidden();
    }

    public static BlockPos getCorner(int index) {
        return index == 0 ? config.corner1() : config.corner2();
    }

    public static AABB regionBox() {
        return config.region();
    }

    public static void setCorner(int index, BlockPos pos) {
        synchronized (LOCK) {
            if (index == 0) {
                corner1 = pos.immutable();
            } else {
                corner2 = pos.immutable();
            }

            selectedCorner = index;
            updateRegion();
            commit();
        }
    }

    public static boolean isCornerSelected(int index) {
        return selectedCorner == index;
    }

    public static boolean selectCorner(Vec3 start, Vec3 end) {
        int closest;

        synchronized (LOCK) {
            closest = -1;
            double closestDistance = Double.MAX_VALUE;

            for (int index = 0; index < 2; index++) {
                BlockPos pos = getCorner(index);
                if (pos == null) {
                    continue;
                }

                Optional<Vec3> hit = cornerBox(pos).clip(start, end);
                if (hit.isEmpty()) {
                    continue;
                }

                double distance = start.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = index;
                }
            }

            selectedCorner = closest;
        }

        return closest >= 0;
    }

    private static AABB cornerBox(BlockPos pos) {
        return new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1.0,
                pos.getY() + 1.0,
                pos.getZ() + 1.0);
    }

    public static void moveSelectedCorner(Direction direction, int amount) {
        synchronized (LOCK) {
            int index = selectedCorner;
            if (index < 0) {
                return;
            }

            BlockPos pos = index == 0 ? corner1 : corner2;
            if (pos == null) {
                return;
            }

            BlockPos moved = pos.relative(direction, amount).immutable();
            if (index == 0) {
                corner1 = moved;
            } else {
                corner2 = moved;
            }

            updateRegion();
            commit();
        }
    }

    public static boolean addRegionFromSelection() {
        BlockPos first = corner1;
        BlockPos second = corner2;
        if (first == null || second == null) {
            return false;
        }

        return addRegion(Region.of(first, second));
    }

    public static int addRegions(List<Region> boxes) {
        if (boxes.isEmpty()) {
            return 0;
        }

        int added = 0;

        synchronized (LOCK) {
            for (Region box : boxes) {
                if (!REGIONS.contains(box)) {
                    REGIONS.add(box);
                    added++;
                }
            }

            if (added == 0) {
                return 0;
            }

            commitAndRemember();
        }

        Log.say("[state] {} region(s) recorded", added);

        if (getMode().usesRegion()) {
            for (Region box : boxes) {
                HiddenSections.mark(box.box());
            }

            relight();
        }

        return added;
    }

    @Nullable
    public static Region selection() {
        BlockPos first = corner1;
        BlockPos second = corner2;

        return first == null || second == null ? null : Region.of(first, second);
    }

    public static boolean subtractRegions(List<Region> cuts) {
        if (cuts.isEmpty()) {
            return false;
        }

        int before;
        int kept;

        synchronized (LOCK) {
            if (REGIONS.isEmpty()) {
                return false;
            }

            before = REGIONS.size();

            List<Region> current = new ArrayList<>(REGIONS);

            for (Region cut : cuts) {
                List<Region> next = new ArrayList<>(current.size() + 4);

                for (Region region : current) {
                    next.addAll(region.subtract(cut));
                }

                if (next.size() > MAX_SUBTRACT_FRAGMENTS) {
                    Log.say("[state] 裁剪产生的碎片过多（>{}），已放弃：选区形状过于破碎", MAX_SUBTRACT_FRAGMENTS);
                    return false;
                }

                current = next;
            }

            REGIONS.clear();
            for (Region region : current) {
                if (!REGIONS.contains(region)) {
                    REGIONS.add(region);
                }
            }

            kept = REGIONS.size();
            commitAndRemember();
        }

        Log.say("[state] regions clipped by recorder: {} -> {}", before, kept);

        if (getMode().usesRegion()) {
            rebuildChunks();
        }

        return true;
    }

    public static boolean addRegion(Region newRegion) {
        synchronized (LOCK) {
            if (REGIONS.contains(newRegion)) {
                Log.say("[state] region already added: {}", newRegion.toSource());
                return false;
            }

            REGIONS.add(newRegion);
            commitAndRemember();
        }

        Log.say("[state] region added: {}", newRegion.toSource());

        if (getMode().usesRegion()) {
            HiddenSections.mark(newRegion.box());
            relight();
        }

        return true;
    }

    public static boolean removeRegionAt(BlockPos pos) {
        synchronized (LOCK) {
            return removeRegions(REGIONS.stream().filter(added -> added.contains(pos)).toList());
        }
    }

    public static boolean removeRegions(List<Region> targets) {
        List<Region> dropped;

        synchronized (LOCK) {
            dropped = targets.stream().filter(REGIONS::contains).toList();
            if (dropped.isEmpty()) {
                return false;
            }

            REGIONS.removeAll(dropped);
            commitAndRemember();
        }

        Log.say("[state] {} region(s) removed", dropped.size());

        if (getMode().usesRegion()) {
            for (Region region : dropped) {
                HiddenSections.mark(region.box());
            }

            retire(dropped);
            relight();
        }

        return true;
    }

    @Nullable
    public static Region pickRegion(Vec3 start, Vec3 end) {
        synchronized (LOCK) {
            Region closest = null;
            double closestDistance = Double.MAX_VALUE;

            for (Region region : REGIONS) {
                Optional<Vec3> hit = region.box().clip(start, end);
                if (hit.isEmpty()) {
                    continue;
                }

                double distance = start.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = region;
                }
            }

            return closest;
        }
    }

    public static boolean toggleRegionAlongRay(Vec3 start, Vec3 end) {
        Region hit = pickRegion(start, end);
        return hit != null ? removeRegions(List.of(hit)) : addRegionFromSelection();
    }

    public static boolean clipRegions() {
        BlockPos first = corner1;
        BlockPos second = corner2;
        if (first == null || second == null) {
            return false;
        }

        Region cut = Region.of(first, second);
        List<Region> before;
        int kept;

        synchronized (LOCK) {
            if (REGIONS.isEmpty()) {
                return false;
            }

            before = List.copyOf(REGIONS);
            REGIONS.clear();

            for (Region region : before) {
                for (Region part : region.subtract(cut)) {
                    if (!REGIONS.contains(part)) {
                        REGIONS.add(part);
                    }
                }
            }

            kept = REGIONS.size();

            commitAndRemember();
        }

        Log.say("[state] regions clipped by {}: {} -> {}", cut.toSource(), before.size(), kept);

        if (getMode().usesRegion()) {
            for (Region region : before) {
                HiddenSections.mark(region.box());
            }

            retire(before);
            relight();
        }

        return true;
    }

    public static List<Region> regions() {
        return config.regions();
    }

    public static List<String> regionSources() {
        return config.regions().stream().map(Region::toSource).toList();
    }

    public static void setRegionSources(List<String> sources) {
        List<String> wanted = sources == null ? List.of() : List.copyOf(sources);
        if (wanted.equals(regionSourceInput)) {
            return;
        }

        regionSourceInput = wanted;

        synchronized (LOCK) {
            REGIONS.clear();

            if (sources != null) {
                for (String source : sources) {
                    Region parsed = Region.parse(source);
                    if (parsed != null && !REGIONS.contains(parsed)) {
                        REGIONS.add(parsed);
                    }
                }
            }

            commit();
        }

        if (getMode().usesRegion()) {
            rebuildChunks();
        }
    }

    private static void updateRegion() {
        if (corner1 == null || corner2 == null) {
            region = null;
            return;
        }

        region =
                new AABB(
                        Math.min(corner1.getX(), corner2.getX()),
                        Math.min(corner1.getY(), corner2.getY()),
                        Math.min(corner1.getZ(), corner2.getZ()),
                        Math.max(corner1.getX(), corner2.getX()) + 1,
                        Math.max(corner1.getY(), corner2.getY()) + 1,
                        Math.max(corner1.getZ(), corner2.getZ()) + 1);
    }

    public static boolean addRule(String source) {
        BlockMatchRule rule = BlockMatchRule.parse(source);
        if (rule == null) {
            return false;
        }

        synchronized (LOCK) {
            for (BlockMatchRule existing : RULES) {
                if (existing.source().equalsIgnoreCase(rule.source())) {
                    return false;
                }
            }

            RULES.add(rule);
            commitAndRemember();
        }

        Log.say("[state] rule added: {}", rule.source());
        rebuildAfterListChange(true);
        return true;
    }

    public static boolean removeRule(String source) {
        synchronized (LOCK) {
            if (!RULES.removeIf(rule -> rule.source().equalsIgnoreCase(source.trim()))) {
                return false;
            }

            commitAndRemember();
        }

        Log.say("[state] rule removed: {}", source.trim());
        rebuildAfterListChange(false);
        return true;
    }

    public static List<String> ruleSources() {
        return config.rules().stream().map(BlockMatchRule::source).toList();
    }

    public static void setRuleSources(List<String> sources) {
        List<String> wanted = sources == null ? List.of() : List.copyOf(sources);
        if (wanted.equals(ruleSourceInput)) {
            return;
        }

        ruleSourceInput = wanted;

        synchronized (LOCK) {
            RULES.clear();

            if (sources != null) {
                for (String source : sources) {
                    BlockMatchRule rule = BlockMatchRule.parse(source);
                    if (rule != null && !hasRule(rule.source())) {
                        RULES.add(rule);
                    }
                }
            }

            commit();
        }

        if (getMode().usesList()) {
            rebuildChunks();
        }
    }

    public static boolean hasRule(String source) {
        String trimmed = source.trim();
        for (BlockMatchRule rule : RULES) {
            if (rule.source().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }

        return false;
    }

    public static List<String> recordSources() {
        return config.recorded().stream().map(BlockMatchRule::source).toList();
    }

    public static void setRecordSources(List<String> sources) {
        List<String> wanted = sources == null ? List.of() : List.copyOf(sources);

        synchronized (LOCK) {
            if (wanted.equals(recordSourceInput) && wanted.equals(sourcesOf(RECORDED))) {
                return;
            }

            recordSourceInput = wanted;
            RECORDED.clear();

            for (String source : wanted) {
                BlockMatchRule rule = BlockMatchRule.parse(source);
                if (rule != null && !hasRecord(rule.source())) {
                    RECORDED.add(rule);
                }
            }

            commit();
        }
    }

    private static List<String> sourcesOf(List<BlockMatchRule> rules) {
        return rules.stream().map(BlockMatchRule::source).toList();
    }

    public static boolean hasRecord(String source) {
        String trimmed = source.trim();
        for (BlockMatchRule rule : RECORDED) {
            if (rule.source().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }

        return false;
    }

    public static RecordMode getRecordMode() {
        return config.recordMode();
    }

    public static void setRecordMode(RecordMode newMode) {
        synchronized (LOCK) {
            if (newMode == null || newMode == recordMode) {
                return;
            }

            recordMode = newMode;
            commit();
        }

        Log.say("[state] record mode {}", newMode.name());
    }

    public static RecordApplyMode getRecordApplyMode() {
        return config.recordApplyMode();
    }

    public static void setRecordApplyMode(RecordApplyMode newMode) {
        if (newMode == null) {
            return;
        }

        synchronized (LOCK) {
            if (newMode == recordApplyMode) {
                return;
            }

            recordApplyMode = newMode;
            commit();
        }

        Log.say("[state] record apply mode {}", newMode.name());
    }

    public static boolean isInvert() {
        return config.invert();
    }

    public static void setInvert(boolean value) {
        synchronized (LOCK) {
            if (value == invert) {
                return;
            }

            invert = value;
            commit();
        }

        Log.say("[state] invert {}", value);
        rebuildChunks();
    }

    public static boolean isPassThrough() {
        return config.passThrough();
    }

    public static boolean isPassThroughActive() {
        RenderConfig current = config;
        return current.passThrough()
                && current.mode() != Mode.OFF
                && !SelectiveRendering.isWandHeld();
    }

    public static void setPassThrough(boolean value) {
        synchronized (LOCK) {
            if (value == passThrough) {
                return;
            }

            passThrough = value;
            commit();
        }

        Log.say("[state] pass through {}", value);
    }

    public static boolean matchesRecorded(BlockState state, boolean moving) {
        RenderConfig current = config;
        List<BlockMatchRule> rules = current.recorded();
        if (rules.isEmpty()) {
            return true;
        }

        boolean listed = false;

        for (BlockMatchRule rule : rules) {
            if (rule.matches(state, moving)) {
                listed = true;
                break;
            }
        }

        return current.recordMode() == RecordMode.LISTED ? listed : !listed;
    }

    public static int getAlpha(BlockState state, BlockPos pos) {
        return getAlpha(state, pos, false);
    }

    public static int getAlpha(BlockState state, BlockPos pos, boolean moving) {
        int alpha = alphaNow(state, pos, moving);
        if (alpha < 0) {
            return -1;
        }

        HiddenSections.note(pos);
        return alpha;
    }

    public static int alphaNow(BlockState state, BlockPos pos, boolean moving) {
        RenderConfig current = config;
        Mode mode = current.mode();
        if (mode == Mode.OFF) {
            return -1;
        }

        boolean needsRegion = mode.usesRegion();
        boolean needsList = mode.usesList();

        List<Region> regions = needsRegion ? current.regions() : List.of();
        List<BlockMatchRule> rules = needsList ? current.rules() : List.of();

        if ((needsRegion && regions.isEmpty()) || (needsList && rules.isEmpty())) {
            return -1;
        }

        boolean carried = moving || state.getBlock() instanceof MovingPistonBlock;
        if (carried) {
            Minecraft minecraft = Minecraft.getInstance();
            BlockEntity entity =
                    state.getBlock() instanceof MovingPistonBlock && minecraft.level != null
                            ? minecraft.level.getBlockEntity(pos)
                            : null;
            if (entity instanceof PistonMovingBlockEntity piston) {
                BlockState moved = piston.getMovedState();
                if (moved != null) {
                    state = moved;
                    pos = pos.relative(piston.getMovementDirection().getOpposite());
                }
            }
        }

        StateCache cache = STATE_CACHE.get();
        if (cache.owner != current) {
            cache.reset(current);
        }

        boolean cacheable = !needsRegion && !carried;
        if (cacheable) {
            Integer cached = cache.alpha.get(state);
            if (cached != null) {
                return cached;
            }
        }

        boolean listed = false;
        if (needsList) {
            Integer cached = carried ? null : cache.listed.get(state);
            if (cached != null) {
                listed = cached != 0;
            } else {
                listed = matches(state, carried, rules);
                if (!carried) {
                    remember(cache.listed, state, listed ? 1 : 0);
                }
            }
        }

        boolean inside = false;
        if (needsRegion) {
            inside = insideRegion(pos, regions, current.union(), cache);
        }

        boolean matched =
                switch (mode) {
                    case REGION_INSIDE_LISTED -> inside && listed;
                    case REGION_INSIDE_UNLISTED -> inside && !listed;
                    case REGION_INSIDE -> inside;
                    case REGION_OUTSIDE_LISTED -> !inside && listed;
                    case REGION_OUTSIDE_UNLISTED -> !inside && !listed;
                    case REGION_OUTSIDE -> !inside;
                    case BLACKLIST -> listed;
                    case WHITELIST -> !listed;
                    case OFF -> false;
                };

        boolean hidden = current.invert() != matched;

        int result = hidden ? current.alpha() : -1;

        if (cacheable) {
            remember(cache.alpha, state, result);
        }

        return result;
    }

    private static boolean insideRegion(
            BlockPos pos, List<Region> regions, @Nullable Region union, StateCache cache) {
        if (union == null || !union.contains(pos)) {
            return false;
        }

        long section = SectionPos.asLong(pos);
        Integer hit = cache.sections.get(section);
        if (hit == null) {
            hit =
                    sectionIntersects(
                                    SectionPos.x(section) * 16,
                                    SectionPos.y(section) * 16,
                                    SectionPos.z(section) * 16,
                                    regions)
                            ? 1
                            : 0;
            remember(cache.sections, section, hit);
        }

        return hit != 0 && isInside(pos, regions);
    }

    private static boolean sectionIntersects(int sx, int sy, int sz, List<Region> regions) {
        int maxX = sx + 15;
        int maxY = sy + 15;
        int maxZ = sz + 15;

        for (Region region : regions) {
            if (region.max().getX() >= sx
                    && region.min().getX() <= maxX
                    && region.max().getY() >= sy
                    && region.min().getY() <= maxY
                    && region.max().getZ() >= sz
                    && region.min().getZ() <= maxZ) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static Region unionOf(List<Region> regions) {
        if (regions.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Region region : regions) {
            BlockPos min = region.min();
            BlockPos max = region.max();

            minX = Math.min(minX, min.getX());
            minY = Math.min(minY, min.getY());
            minZ = Math.min(minZ, min.getZ());
            maxX = Math.max(maxX, max.getX());
            maxY = Math.max(maxY, max.getY());
            maxZ = Math.max(maxZ, max.getZ());
        }

        return new Region(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static <K> void remember(Map<K, Integer> map, K key, int value) {
        if (map.size() >= MAX_CACHED_ENTRIES) {
            map.clear();
        }

        map.put(key, value);
    }

    public static int getFluidAlpha(BlockState state, BlockPos pos) {
        return getAlpha(state, pos);
    }

    public static boolean shouldRenderFluidsTranslucent() {
        RenderConfig current = config;
        if (current.mode() == Mode.OFF) {
            return false;
        }

        return current.transparency() < MAX_TRANSPARENCY;
    }

    public static boolean isHidden(BlockState state, BlockPos pos) {
        return getAlpha(state, pos) >= 0;
    }

    public static boolean isHidden(BlockGetter level, BlockPos pos) {
        return getAlpha(level.getBlockState(pos), pos) >= 0;
    }

    public static boolean isHiddenIn(@Nullable BlockGetter level, BlockPos pos) {
        if (level == null) {
            return false;
        }

        return alphaNow(level.getBlockState(pos), pos, false) >= 0;
    }

    public static boolean isFaded(BlockState state, BlockPos pos) {
        return alphaNow(state, pos, false) >= 0;
    }

    public static BlockState airIfHidden(BlockState state, BlockPos pos) {
        RenderConfig current = config;

        if (current.mode() == Mode.OFF) {
            return state;
        }

        if (current.fullBright()) {
            return Blocks.AIR.defaultBlockState();
        }

        return alphaNow(state, pos, false) >= 0 ? Blocks.AIR.defaultBlockState() : state;
    }

    public static String getWand() {
        return config.wand();
    }

    public static void setWand(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            trimmed = DEFAULT_WAND;
        }

        synchronized (LOCK) {
            if (trimmed.equals(wand)) {
                return;
            }

            wand = trimmed;
            commit();
        }
    }

    public static boolean isFullBright() {
        return config.fullBright();
    }

    public static boolean isNightVisionActive() {
        RenderConfig current = config;
        return current.mode() != Mode.OFF && current.fullBright();
    }

    public static void setFullBright(boolean value) {
        if (value == isFullBright()) {
            return;
        }

        rebuildChunks();

        synchronized (LOCK) {
            fullBright = value;
            commit();
        }

        rebuildChunks();
    }

    public static boolean isHiddenAt(BlockPos pos) {
        return getAlphaAt(pos) >= 0;
    }

    public static int getAlphaAt(BlockPos pos) {
        if (getMode() == Mode.OFF) {
            return -1;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return -1;
        }

        return getAlpha(minecraft.level.getBlockState(pos), pos);
    }

    public static int alphaAt(BlockPos pos) {
        if (getMode() == Mode.OFF) {
            return -1;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return -1;
        }

        return alphaNow(minecraft.level.getBlockState(pos), pos, false);
    }

    public static boolean isFadedCube(int x, int y, int z) {
        return alphaAt(BlockPosScratch.at(x, y, z)) >= 0;
    }

    public static int getHiddenAlpha() {
        return config.alpha();
    }

    private static boolean matches(BlockState state, boolean moving, List<BlockMatchRule> rules) {
        for (BlockMatchRule rule : rules) {
            if (rule.matches(state, moving)) {
                return true;
            }
        }

        return false;
    }

    private static void rebuildHidden() {
        if (!HiddenSections.mark()) {
            rebuildChunks();
        }
    }

    private static void rebuildAfterListChange(boolean added) {
        Mode mode = getMode();
        if (!mode.usesList()) {
            return;
        }

        if (isInvert()) {
            rebuildChunks();
            return;
        }

        if (mode.hidesListed() == added) {
            rebuildChunks();
            return;
        }

        rebuildHidden();
    }

    public static void rebuildChunks() {
        HiddenSections.clear();
        Platform.rebuildAll();
        relight();
    }

    private static void relight() {
        if (isFullBright()) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        List<Region> targets;
        synchronized (RETIRED_REGIONS) {
            targets = new ArrayList<>(config.regions().size() + RETIRED_REGIONS.size());
            targets.addAll(config.regions());

            for (Region region : RETIRED_REGIONS) {
                if (!targets.contains(region)) {
                    targets.add(region);
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        long generation = RELIGHT_GENERATION.incrementAndGet();
        level.queueLightUpdate(new Relight(targets, generation));
    }

    private static void retire(List<Region> regions) {
        synchronized (RETIRED_REGIONS) {
            for (Region region : regions) {
                if (!RETIRED_REGIONS.contains(region)) {
                    RETIRED_REGIONS.add(region);
                }
            }
        }
    }

    private static final class Relight implements Runnable {
        private static final int LIMIT = 2000;

        private final List<Region> regions;
        private final long generation;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        private int regionIndex;
        private long progress;

        private boolean started;

        private int minX;
        private int minZ;
        private int bottomY;
        private int topY;
        private int width;
        private int depth;
        private int height;

        Relight(List<Region> regions, long generation) {
            this.regions = regions;
            this.generation = generation;
        }

        @Override
        public void run() {
            if (this.generation != RELIGHT_GENERATION.get()) {
                return;
            }

            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            int budget = LIMIT;
            while (budget > 0 && this.regionIndex < this.regions.size()) {
                if (!this.started) {
                    beginRegion(level, this.regions.get(this.regionIndex));
                    this.started = true;
                }

                if (this.width <= 0 || this.depth <= 0 || this.height <= 0) {
                    this.regionIndex++;
                    this.started = false;
                    continue;
                }

                long total = (long) this.width * this.depth * this.height;

                while (this.progress < total && budget > 0) {
                    int y = this.bottomY + (int) (this.progress % this.height);
                    long column = this.progress / this.height;
                    int x = this.minX + (int) (column % this.width);
                    int z = this.minZ + (int) (column / this.width);

                    level.getLightEngine().checkBlock(this.pos.set(x, y, z));
                    this.progress++;
                    budget--;
                }

                if (this.progress >= total) {
                    this.regionIndex++;
                    this.progress = 0;
                    this.started = false;
                }
            }

            if (this.regionIndex < this.regions.size()) {
                level.queueLightUpdate(this);
            }
        }

        private void beginRegion(ClientLevel level, Region region) {
            this.minX = region.min().getX() - LIGHT_RANGE;
            this.minZ = region.min().getZ() - LIGHT_RANGE;
            this.width = region.max().getX() - region.min().getX() + LIGHT_RANGE * 2 + 1;
            this.depth = region.max().getZ() - region.min().getZ() + LIGHT_RANGE * 2 + 1;
            this.bottomY = Math.max(level.getMinY(), region.min().getY() - LIGHT_RANGE);
            this.topY = Math.min(level.getMaxY(), region.max().getY() + LIGHT_RANGE);
            this.height = this.topY - this.bottomY + 1;
        }
    }

    private static BlockPos toBlockPos(int[] values) {
        return values == null || values.length != 3
                ? null
                : new BlockPos(values[0], values[1], values[2]);
    }

    private static int[] toArray(BlockPos pos) {
        return pos == null ? null : new int[] {pos.getX(), pos.getY(), pos.getZ()};
    }

    private static int[] toArray(Region region) {
        return new int[] {
            region.min().getX(),
            region.min().getY(),
            region.min().getZ(),
            region.max().getX(),
            region.max().getY(),
            region.max().getZ()
        };
    }

    private static Region toRegion(int[] values) {
        return values == null || values.length != 6
                ? null
                : new Region(
                        new BlockPos(values[0], values[1], values[2]),
                        new BlockPos(values[3], values[4], values[5]));
    }

    private static boolean isInside(BlockPos pos, List<Region> regions) {
        for (Region added : regions) {
            if (added.contains(pos)) {
                return true;
            }
        }

        return false;
    }

    public static List<Preset> presets() {
        return config.presets();
    }

    public static void applyPreset(Preset preset) {
        synchronized (LOCK) {
            mode = preset.mode == null ? DEFAULT_MODE : preset.mode;
            transparency = Math.clamp(preset.transparency, 0, MAX_TRANSPARENCY);
            invert = preset.invert;

            RULES.clear();
            if (preset.rules != null) {
                for (String source : preset.rules) {
                    BlockMatchRule rule = BlockMatchRule.parse(source);
                    if (rule != null) {
                        RULES.add(rule);
                    }
                }
            }

            REGIONS.clear();
            if (preset.regions != null) {
                for (String source : preset.regions) {
                    Region added = Region.parse(source);
                    if (added != null && !REGIONS.contains(added)) {
                        REGIONS.add(added);
                    }
                }
            }

            commitAndRemember();
        }

        Log.say("[state] preset applied: {}", preset.displayName());
        rebuildChunks();
    }

    public static void savePresets() {
        synchronized (LOCK) {
            persist();
        }
    }

    public static void setPresets(List<Preset> presets) {
        synchronized (LOCK) {
            PRESETS.clear();

            if (presets != null) {
                for (Preset preset : presets) {
                    if (preset != null) {
                        PRESETS.add(preset);
                    }
                }
            }

            commit();
        }
    }

    private static void commit() {
        publish();
        persist();
    }

    private static void commitAndRemember() {
        publish();
        rememberSources();
        persist();
    }

    private static void publish() {
        config =
                RenderConfig.of(
                        mode,
                        transparency,
                        invert,
                        List.copyOf(RULES),
                        List.copyOf(RECORDED),
                        List.copyOf(REGIONS),
                        List.copyOf(PRESETS),
                        recordMode,
                        recordApplyMode,
                        wand,
                        fullBright,
                        passThrough,
                        corner1,
                        corner2,
                        region);
    }

    private static void rememberSources() {
        ruleSourceInput = RULES.stream().map(BlockMatchRule::source).toList();
        regionSourceInput = REGIONS.stream().map(Region::toSource).toList();

        recordSourceInput = RECORDED.stream().map(BlockMatchRule::source).toList();
    }

    private static void persist() {
        ModConfig config = ModConfig.get();
        config.mode = mode;
        config.transparency = transparency;
        config.recordMode = recordMode;
        config.recordApplyMode = recordApplyMode;
        config.invert = invert;
        config.wand = wand;
        config.fullBright = fullBright;
        config.rules = RULES.stream().map(BlockMatchRule::source).toList();
        config.recorded = RECORDED.stream().map(BlockMatchRule::source).toList();
        config.regionPos1 = toArray(corner1);
        config.regionPos2 = toArray(corner2);
        config.regions = REGIONS.stream().map(SelectiveRenderingManager::toArray).toList();
        config.presets = new ArrayList<>(PRESETS);
        ModConfig.save();
    }
}
