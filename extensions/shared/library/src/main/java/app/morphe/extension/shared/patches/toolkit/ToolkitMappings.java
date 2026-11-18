package app.morphe.extension.shared.patches.toolkit;

/**
 * Mappings for Google Photos obfuscated internal symbols.
 * Auto-generated and verified by ToolkitAligner for Google Photos 8.2.0.1023899193.
 */
public final class ToolkitMappings {

    public static final String TARGET_VERSION = "8.2.0.1023899193";

    // Service Locator (bzoq in 7.97, cbnw in 7.98)
    public static final String SERVICE_LOCATOR_CLASS = "cdhf";
    public static final String SERVICE_LOCATOR_METHOD = "e";
    public static final String[] SERVICE_LOCATOR_CLASSES = {"cdhf", "ccym", "cbyf", "bzoq", "bxoe"};

    // Task Runner (Sync task execution via static helper)
    public static final String TASK_RUNNER_CLASS = "bcpr";
    public static final String TASK_RUNNER_SYNC_METHOD = "c";
    public static final String[] TASK_RUNNER_CLASSES = {"bcpr", "bclh", "bsyv", "bztw", "cbsq", "cagl", "cant", "bxue"};

    // Add Media To Album (Private Album creation builder)
    public static final String ADD_MEDIA_TO_ALBUM_BUILDER_CLASS = "pdd";
    public static final String[] ADD_MEDIA_TO_ALBUM_BUILDER_CLASSES = {"pdd", "ozb", "orw", "otz", "orn", "ovo", "mtl", "mqw"};

    // Add Media To Envelope (Shared Album addition builder)
    public static final String ADD_MEDIA_TO_ENVELOPE_BUILDER_CLASS = "aphh";
    public static final String[] ADD_MEDIA_TO_ENVELOPE_BUILDER_CLASSES = {"aphh", "apfz", "apjt", "amwa"};

    // Remove Media From Private Album Task
    public static final String PRIVATE_ALBUM_REMOVE_CLASS = "pcw";
    public static final String PRIVATE_ALBUM_REMOVE_METHOD = "i";
    public static final String[] PRIVATE_ALBUM_REMOVE_CLASSES = {"pcw", "oxk", "oqm", "osi", "iyo"};

    // Mars Remove Task Config: {argClass, enumClass, enumField, builderClass, builderMethod}
    public static final String[] MARS_REMOVE_CONFIG_CURRENT = {"okp", "bcpt", "mo", "tbw", "s"};
    public static final String[][] MARS_REMOVE_CONFIGS = {
        {"okp", "bcpt", "mo", "tbw", "s"}, // 8.2.0.1023899193
        {"ogw", "bclj", "mo", "sou", "n"}, // 8.1.0.1020990585
        {"oac", "bbuk", "ms", "ssk", "l"}, // 7.99.0.1007655401
        {"odi", "bdft", "ms", "stk", "s"}, // 7.97.0 arm64
        {"oax", "bbzi", "ms", "sgz", "n"}, // 7.97.0 univ
        {"ofl", "bcdu", "ms", "tfb", "p"}, // 7.97.0 univ
        {"mbj", "azlk", "mu", "rga", "l"}  // 7.96.0 nodpi
    };

    // Mars Collection (MediaCollection for Locked Folder)
    public static final String MARS_COLLECTION_CLASS = "alrk";
    public static final String[] MARS_COLLECTION_CLASSES = {"alrk", "alsd", "alqt", "alww", "ajhx"};

    // Trash Collection (MediaCollection for Trash/Bin)
    public static final String TRASH_COLLECTION_CLASS = "blja";
    public static final String TRASH_FACTORY_CLASS = "bliu";
    public static final String TRASH_FACTORY_METHOD = "b";
    public static final String[] TRASH_COLLECTION_CLASSES = {"blja", "bliu", "bnew", "blvo", "bmcu", "bjjo", "bjjq"};

    // Keys Media Collection (Loads items given remote media keys)
    public static final String KEYS_COLLECTION_CLASS = "pti";
    public static final String KEYS_COLLECTION_METHOD = "f";
    public static final String KEYS_COLLECTION_BUILDER = "pth";
    public static final String[][] KEYS_COLLECTION_FACTORIES = {
        {"pti", "f"}, // 8.2.0.1023899193
        {"pti", "f"}, // 8.1.0.1020990585
        {"pyk", "f"}, // 7.97.0 arm64
        {"pvv", "f"}, // 7.97.0 univ
        {"pzv", "f"}, // 7.97.0 univ
        {"num", "f"}  // 7.96.0
    };
    public static final String[] KEYS_COLLECTION_BUILDERS = {"pth", "pyj", "pvu", "pzu", "nul"};

    // Core Media Loader & Features Request
    public static final String CORE_MEDIA_LOADER_CLASS = "yrq";
    public static final String CORE_MEDIA_LOADER_METHOD = "B";
    public static final String[] CORE_MEDIA_LOADER_CLASSES = {"yrq", "yul", "yxq", "zap", "wrl"};

    public static final String FEATURES_REQUEST_BUILDER_CLASS = "yqd";
    public static final String FEATURES_REQUEST_CLASS = "yqf";
    public static final String[][] FEATURES_REQUEST_PAIRS = {
        {"yqd", "yqf"}, // 8.2.0.1023899193
        {"yqd", "yqf"}, // 8.1.0.1020990585
        {"wqa", "wqb"}, // 7.97.0
        {"yta", "ytc"}, // 7.97.0
        {"yyq", "yyr"}  // 7.97.0 univ
    };

    // DedupKey Feature & RemoteMediaKey Feature
    public static final String DEDUP_KEY_FEATURE_CLASS = "abmv";
    public static final String[] DEDUP_KEY_FEATURE_CLASSES = {"abmv", "zon", "zoi", "zog", "zkr"};

    public static final String REMOTE_MEDIA_KEY_FEATURE_CLASS = "nfl";
    public static final String[] REMOTE_MEDIA_KEY_FEATURE_CLASSES = {"nfl", "bams", "bamo", "bamk", "azxs"};

    // Action Queue Flusher (flushes local action mutations)
    public static final String ACTION_FLUSHER_CLASS = "arfq";
    public static final String[] ACTION_FLUSHER_CLASSES = {"arfq", "arbz", "aqmp", "aqgz", "aqgr", "aokq", "amwa"};

    // Archive Service & Actions
    public static final String ARCHIVE_SERVICE_CLASS = "shd";
    public static final String ARCHIVE_SERVICE_METHOD = "a";
    public static final String[] ARCHIVE_SERVICE_CLASSES = {"shd", "scm", "rvd", "rsr", "psu"};

    public static final String ARCHIVE_BUILDER_CLASS = "rsv";
    public static final String ARCHIVE_ACTION_CLASS = "rsw";
    public static final String[] ARCHIVE_BUILDER_CLASSES = {"rsv", "psy"};

    // Move to Trash Action
    public static final String MOVE_TO_TRASH_ACTION_CLASS = "bgqg";
    public static final String[] MOVE_TO_TRASH_ACTION_CLASSES = {"bgqg", "bmpy", "bglr", "bfsn", "bfjt", "bjir"};

    // Glide Singleton (mdm in 7.98)
    // Glide Singleton
    public static final String GLIDE_SINGLETON_CLASS = "mpa";
    public static final String[] GLIDE_SINGLETON_CLASSES = {"mpa", "mlt", "mez", "mdm", "mil", "khc", "mgg", "mkp", "kjo"};

    // Remote Media Model & Options (amrp/amrq in 7.98)
    // Remote Media Model & Options
    public static final String REMOTE_MEDIA_MODEL_CLASS = "anof";
    public static final String REMOTE_MEDIA_OPTIONS_CLASS = "anog";
    public static final String[][] REMOTE_MEDIA_MODEL_PAIRS = {
        {"anof", "anog"}, // 8.2.0.1023899193
        {"ankr", "anks"}, // 8.1.0.1020990585
        {"amwm", "amwn"}, // 7.99.0.1007655401
        {"amsr", "amss"}, // 7.97.0 arm64
        {"amri", "amrj"}, // 7.97.0 univ
        {"amxw", "amxx"}, // 7.97.0 univ
        {"akin", "akio"}, // 7.96.0 nodpi
        {"aklr", "akls"}  // 7.96.0 Samsung
    };

    private ToolkitMappings() {}
}
