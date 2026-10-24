package com.nfsu2.androidevolved;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Launcher options. GameActivity reads them when the game process starts and hands them to the native
 * runtime before the guest boots; changing one takes effect on the next JUGAR.
 */
final class GameOptions {
    static final class Option {
        /**
         * {@code title} is a string resource and {@code labels} a string-array resource, so they can be
         * passed straight to AlertDialog.Builder.setTitle(int) / setSingleChoiceItems(int, ...).
         */
        final String key, defaultValue;
        final int title, labels;
        final String[] values;
        Option(String key, int title, String defaultValue, String[] values, int labels) {
            this.key = key; this.title = title; this.defaultValue = defaultValue; this.values = values; this.labels = labels;
        }
        String title(Context context) { return context.getString(title); }
        String[] labels(Context context) { return context.getResources().getStringArray(labels); }
        String label(Context context, String value) {
            String[] names = labels(context);
            for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return names[i];
            return value;
        }
    }

    static final String RESOLUTION = "resolution", FPS = "fps", WIDESCREEN = "widescreen", LATENCY = "latency",
            TEXTURES = "textures", MINIMAP = "minimap", LANGUAGE = "language";

    static final Option[] ALL = {
            new Option(RESOLUTION, R.string.option_resolution_title, "75",
                    new String[] {"100", "75", "50", "720"}, R.array.option_resolution_labels),
            new Option(FPS, R.string.option_fps_title, "120",
                    new String[] {"30", "60", "90", "120", "0"}, R.array.option_fps_labels),
            new Option(WIDESCREEN, R.string.option_widescreen_title, "on",
                    new String[] {"on", "off"}, R.array.option_widescreen_labels),
            new Option(MINIMAP, R.string.option_minimap_title, "top",
                    new String[] {"top", "bottom"}, R.array.option_minimap_labels),
            new Option(LATENCY, R.string.option_latency_title, "2",
                    new String[] {"2", "1"}, R.array.option_latency_labels),
            new Option(TEXTURES, R.string.option_textures_title, "auto",
                    new String[] {"auto", "cpu"}, R.array.option_textures_labels),
    };

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
        return prefs(context).getString(LANGUAGE, "Spanish");
    }

    /** Render size for the selected resolution on a display of width × height (landscape). */
    static int[] renderSize(Context context, int width, int height) {
        String value = get(context, RESOLUTION);
        if (value.equals("720")) return new int[] {1280, 720};
        float scale = Integer.parseInt(value) / 100f;
        return new int[] {Math.round(width * scale / 2f) * 2, Math.round(height * scale / 2f) * 2};
    }
}
