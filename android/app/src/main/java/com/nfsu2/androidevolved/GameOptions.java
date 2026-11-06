package com.nfsu2.androidevolved;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Launcher options. GameActivity reads them when the game process starts and hands them to the native
 * runtime before the guest boots; changing one takes effect on the next PLAY.
 */
final class GameOptions {
    static final class Option {
        final String key, defaultValue;
        final String[] values;
        final int titleRes;
        final int[] labelRes;

        Option(String key, int titleRes, String defaultValue, String[] values, int[] labelRes) {
            if (values.length != labelRes.length) throw new IllegalArgumentException("Option values/labels mismatch: " + key);
            this.key = key;
            this.titleRes = titleRes;
            this.defaultValue = defaultValue;
            this.values = values;
            this.labelRes = labelRes;
        }

        String title(Context context) { return context.getString(titleRes); }

        String label(Context context, String value) {
            for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return context.getString(labelRes[i]);
            return value;
        }

        String[] labels(Context context) {
            String[] labels = new String[labelRes.length];
            for (int i = 0; i < labelRes.length; i++) labels[i] = context.getString(labelRes[i]);
            return labels;
        }
    }

    static final String RESOLUTION = "resolution", FPS = "fps", WIDESCREEN = "widescreen", LATENCY = "latency",
            TEXTURES = "textures", MINIMAP = "minimap", LANGUAGE = "language", REGION = "region", FPS_OVERLAY = "fps_overlay", TACHOMETER = "tachometer", PAD_LAYOUT = "pad_layout", INTRO = "intro";

    static final Option[] ALL = {
            new Option(RESOLUTION, R.string.option_resolution_title, "75",
                    new String[] {"100", "75", "50", "720"},
                    new int[] {R.string.option_resolution_native, R.string.option_resolution_75,
                            R.string.option_resolution_50, R.string.option_resolution_720}),
            new Option(FPS, R.string.option_fps_title, "120",
                    new String[] {"30", "60", "90", "120", "0"},
                    new int[] {R.string.option_fps_30, R.string.option_fps_60, R.string.option_fps_90,
                            R.string.option_fps_120, R.string.option_fps_unlimited}),
            new Option(WIDESCREEN, R.string.option_widescreen_title, "on",
                    new String[] {"on", "off", "off_minimap"},
                    new int[] {R.string.option_widescreen_on, R.string.option_widescreen_off,
                            R.string.option_widescreen_off_minimap}),
            new Option(MINIMAP, R.string.option_minimap_title, "top",
                    new String[] {"top", "bottom"},
                    new int[] {R.string.option_minimap_top, R.string.option_minimap_bottom}),
            new Option(TACHOMETER, R.string.option_tachometer_title, "bottom",
                    new String[] {"bottom", "raised", "middle"},
                    new int[] {R.string.option_tachometer_bottom, R.string.option_tachometer_raised,
                            R.string.option_tachometer_middle}),
            new Option(INTRO, R.string.option_intro_title, "skip",
                    new String[] {"skip", "play"},
                    new int[] {R.string.option_intro_skip, R.string.option_intro_play}),
            new Option(PAD_LAYOUT, R.string.option_pad_layout_title, "port",
                    new String[] {"port", "console"},
                    new int[] {R.string.option_pad_layout_port, R.string.option_pad_layout_console}),
            new Option(LATENCY, R.string.option_latency_title, "2",
                    new String[] {"2", "1"},
                    new int[] {R.string.option_latency_2, R.string.option_latency_1}),
            new Option(TEXTURES, R.string.option_textures_title, "auto",
                    new String[] {"auto", "cpu"},
                    new int[] {R.string.option_textures_auto, R.string.option_textures_cpu}),
            new Option(FPS_OVERLAY, R.string.option_fps_overlay_title, "off",
                    new String[] {"off", "fps", "full"},
                    new int[] {R.string.option_fps_overlay_off, R.string.option_fps_overlay_fps,
                            R.string.option_fps_overlay_full}),
    };

    /** 0 original, 1 raised, 2 mid-screen (native nfs_widescreen_set_tachometer_offset). */
    static int tachometerPosition(Context context) {
        switch (get(context, TACHOMETER)) {
            case "middle": return 2;
            case "raised": return 1;
            default: return 0;
        }
    }

    /** REGION has no registered Option (no UI, no labels): this build doesn't offer it -- the SKU
     * address is only known for the documented SPEED2.EXE, not the one this fork bundles. find()/get()
     * require a registered Option and would throw for this key, so read the raw preference instead;
     * nothing currently writes it, so this is always the "auto" default in practice. */
    static String regionValue(Context context) {
        String value = prefs(context).getString(REGION, "auto");
        return "eu".equals(value) || "na".equals(value) ? value : "auto";
    }

    /** -1 keeps the executable's region, 0 North America, 1 Europe (native setGuestRegion). */
    static int regionCode(Context context) {
        switch (regionValue(context)) {
            case "eu": return 1;
            case "na": return 0;
            default: return -1;
        }
    }

    private static final String PREFS = "nfsu2_game";

    private GameOptions() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Option find(String key) {
        for (Option o : ALL) if (o.key.equals(key)) return o;
        throw new IllegalArgumentException(key);
    }

    static String get(Context context, String key) {
        Option o = find(key);
        String value = prefs(context).getString(key, o.defaultValue);
        for (String allowed : o.values) if (allowed.equals(value)) return value;
        return o.defaultValue;
    }

    static void set(Context context, String key, String value) {
        prefs(context).edit().putString(key, value).apply();
    }

    static final String ONLINE_SERVER = "online_server", DEFAULT_ONLINE_SERVER = "eahub.eu";

    /** EA Nation replacement server: the game's *.ea.com hosts resolve to this name or IPv4 address. */
    static String onlineServer(Context context) {
        String value = prefs(context).getString(ONLINE_SERVER, DEFAULT_ONLINE_SERVER);
        return validServer(value) ? value : DEFAULT_ONLINE_SERVER;
    }

    static boolean validServer(String value) {
        return value != null && value.length() <= 253 && value.matches("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*");
    }

    /** The game's language folder name (LANGUAGES/<file>.bin) chosen in the launcher. */
    static String language(Context context) {
        return prefs(context).getString(LANGUAGE, "English UK");
    }

    /** Render size for the selected resolution on a display of width × height (landscape). */
    static int[] renderSize(Context context, int width, int height) {
        String value = get(context, RESOLUTION);
        if (value.equals("720")) return new int[] {1280, 720};
        float scale = Integer.parseInt(value) / 100f;
        return new int[] {Math.round(width * scale / 2f) * 2, Math.round(height * scale / 2f) * 2};
    }
}
