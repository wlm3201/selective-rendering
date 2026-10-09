package com.selectiverendering.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.selectiverendering.client.SelectiveRenderingManager.Mode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
    private static final String FILE_NAME = SelectiveRendering.MOD_ID + ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int VERSION = 4;

    private static final long SAVE_DELAY_MS = 300L;

    private static final Object SAVE_LOCK = new Object();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread =
                                new Thread(runnable, SelectiveRendering.MOD_ID + "-config-save");
                        thread.setDaemon(true);
                        return thread;
                    });

    private static String pendingJson;

    private static String writtenJson;

    private static ModConfig instance = new ModConfig();

    static {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                ModConfig::writePending,
                                SelectiveRendering.MOD_ID + "-config-shutdown"));
    }

    public int version = VERSION;

    public Mode mode = Mode.OFF;

    public int transparency = 50;

    public List<String> rules = new ArrayList<>();

    public boolean invert = SelectiveRenderingManager.DEFAULT_INVERT;

    public List<String> recorded = new ArrayList<>();

    public SelectiveRenderingManager.RecordMode recordMode =
            SelectiveRenderingManager.DEFAULT_RECORD_MODE;

    public SelectiveRenderingManager.RecordApplyMode recordApplyMode =
            SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE;

    public int[] regionPos1 = null;

    public int[] regionPos2 = null;

    public List<int[]> regions = new ArrayList<>();

    public List<Preset> presets = new ArrayList<>();

    public JsonObject hotkeys = null;

    public transient List<String> blocks = null;

    public String wand = "minecraft:breeze_rod";

    public boolean fullBright = SelectiveRenderingManager.DEFAULT_FULL_BRIGHT;

    public boolean passThrough = SelectiveRenderingManager.DEFAULT_PASS_THROUGH;

    private ModConfig() {}

    public static ModConfig get() {
        return instance;
    }

    public static void load() {
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded != null) {
                migrate(loaded);
                instance = loaded;
            }
        } catch (Exception e) {
            LOGGER.error("Could not read {}, keeping defaults", FILE_NAME, e);
        }
    }

    private static void migrate(ModConfig config) {
        if (config.version < VERSION && config.rules == null) {
            config.rules = config.blocks == null ? new ArrayList<>() : config.blocks;
        }

        if (config.presets == null) {
            config.presets = new ArrayList<>();
        }

        if (config.recorded == null) {
            config.recorded = new ArrayList<>();
        }

        if (config.rules == null) {
            config.rules = new ArrayList<>();
        }

        if (config.regions == null) {
            config.regions = new ArrayList<>();
        }

        if (config.hotkeys != null && !config.hotkeys.isJsonObject()) {
            config.hotkeys = null;
        }

        config.blocks = null;
        config.version = VERSION;
    }

    public static void save() {
        String json;

        synchronized (SAVE_LOCK) {
            json = GSON.toJson(instance);

            if (json.equals(pendingJson) || json.equals(writtenJson)) {
                return;
            }

            pendingJson = json;
        }

        SCHEDULER.schedule(ModConfig::writePending, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static void flush() {
        writePending();
    }

    private static void writePending() {
        String json;

        synchronized (SAVE_LOCK) {
            json = pendingJson;
            pendingJson = null;

            if (json == null || json.equals(writtenJson)) {
                return;
            }
        }

        try {
            Path path = path();
            Files.createDirectories(path.getParent());

            Path temp = Files.createTempFile(path.getParent(), FILE_NAME, ".tmp");

            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }

            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }

            synchronized (SAVE_LOCK) {
                writtenJson = json;
            }
        } catch (IOException e) {
            LOGGER.error("Could not write {}", FILE_NAME, e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
