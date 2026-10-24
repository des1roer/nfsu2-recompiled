package com.nfsu2.androidevolved;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * User-provided adrenotools ZIPs: Turnip, PanVK or other Android ARM64 Vulkan drivers (from the MW port).
 * The app ships no driver; an imported one is loaded by adrenotools in the game and probe processes only.
 */
final class GpuDrivers {
    static final class Driver {
        final String id, name, library;
        final File directory;
        Driver(String id, String name, File directory, String library) {
            this.id = id; this.name = name; this.directory = directory; this.library = library;
        }
    }
    private static File root(Context context) {
        File root = new File(context.getFilesDir(), "gpu_drivers"); root.mkdirs(); return root;
    }
    static List<Driver> list(Context context) {
        List<Driver> result = new ArrayList<>();
        File[] dirs = root(context).listFiles(File::isDirectory);
        if (dirs != null) for (File dir : dirs) {
            try {
                if (dir.getName().startsWith(".")) continue;
                JSONObject meta = new JSONObject(new String(Files.readAllBytes(new File(dir, "driver.json").toPath()), StandardCharsets.UTF_8));
                String library = meta.getString("libraryName");
                if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) continue;
                File loadDir = new File(dir, meta.getString("folder"));
                if (!loadDir.getCanonicalPath().startsWith(dir.getCanonicalPath() + "/")) continue;
                if (new File(loadDir, library).isFile())
                    result.add(new Driver(dir.getName(), meta.getString("name"), loadDir, library));
            } catch (Exception ignored) { }
        }
        result.sort(Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)));
        return result;
    }
    static String selectedId(Context context) {
        return GameOptions.prefs(context).getString("gpu_driver", "");
    }
    static Driver selected(Context context) {
        String id = selectedId(context);
        for (Driver driver : list(context)) if (driver.id.equals(id)) return driver;
        return null;
    }
    static void select(Context context, String id) {
        GameOptions.prefs(context).edit().putString("gpu_driver", id).apply();
    }
    static Driver importZip(Context context, Uri uri) throws Exception {
        try { return install(context, uri); }
        catch (DriverArchive.Failure failure) { throw localized(context, failure); }
    }
    private static Driver install(Context context, Uri uri) throws Exception {
        File stage = new File(root(context), ".import-" + UUID.randomUUID());
        if (!stage.mkdirs()) throw new IOException(context.getString(R.string.driver_error_stage_dir));
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException(context.getString(R.string.driver_error_open_zip));
            DriverArchive.extract(input, stage);
            File packageRoot = findMeta(context, stage);
            if (packageRoot == null) throw new IOException(context.getString(R.string.driver_error_no_meta));
            JSONObject meta = new JSONObject(new String(Files.readAllBytes(new File(packageRoot, "meta.json").toPath()), StandardCharsets.UTF_8));
            String library = meta.getString("libraryName");
            if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) throw new IOException(context.getString(R.string.driver_error_bad_library_name));
            if (meta.optInt("minApi", 26) > Build.VERSION.SDK_INT)
                throw new IOException(context.getString(R.string.driver_error_min_api));
            DriverArchive.validateLibrary(new File(packageRoot, library));
            String name = meta.optString("name", library);
            if (name.trim().isEmpty() || name.length() > 120) throw new IOException(context.getString(R.string.driver_error_bad_name));
            // Keep dependencies alongside the main library, including ZIPs with a wrapper directory.
            String relative = "payload";
            File installed = new File(root(context), UUID.randomUUID().toString());
            if (!installed.mkdirs()) throw new IOException(context.getString(R.string.driver_error_install_dir));
            try {
                if (!packageRoot.renameTo(new File(installed, relative))) throw new IOException(context.getString(R.string.driver_error_install_package));
                JSONObject record = new JSONObject().put("name", name).put("libraryName", library).put("folder", relative);
                Files.write(new File(installed, "driver.json").toPath(), record.toString().getBytes(StandardCharsets.UTF_8));
                readOnly(context, new File(installed, relative));
            } catch (Exception error) { DriverArchive.delete(installed); throw error; }
            return new Driver(installed.getName(), name, new File(installed, relative), library);
        } finally {
            if (stage.exists()) DriverArchive.delete(stage);
        }
    }
    private static File findMeta(Context context, File root) throws IOException {
        if (new File(root, "meta.json").isFile()) return root;
        File found = null;
        File[] children = root.listFiles(File::isDirectory);
        if (children != null) for (File child : children) {
            File candidate = findMeta(context, child);
            if (candidate != null) {
                if (found != null) throw new IOException(context.getString(R.string.driver_error_ambiguous_meta));
                found = candidate;
            }
        }
        return found;
    }
    private static void readOnly(Context context, File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) readOnly(context, child);
        else if (!file.setReadOnly()) throw new IOException(context.getString(R.string.driver_error_read_only));
    }
    /** The player-facing text of a DriverArchive failure, in the app's language. */
    private static IOException localized(Context context, DriverArchive.Failure failure) {
        String message;
        switch (failure.reason) {
            case TOO_MANY_FILES: message = context.getString(R.string.driver_error_too_many_files); break;
            case BAD_ENTRY: message = context.getString(R.string.driver_error_bad_entry); break;
            case MKDIR: message = context.getString(R.string.driver_error_mkdir); break;
            case TOO_LARGE: message = context.getString(R.string.driver_error_too_large); break;
            case NOT_ARM64: message = context.getString(R.string.driver_error_not_arm64); break;
            case SYMLINK: message = context.getString(R.string.driver_error_symlink); break;
            case DELETE: message = context.getString(R.string.driver_error_delete, failure.detail); break;
            default: message = failure.getMessage(); break;
        }
        return new IOException(message, failure);
    }
    static void remove(Context context, Driver driver) throws IOException {
        File target = new File(root(context), driver.id);
        if (!target.getCanonicalFile().getParentFile().equals(root(context).getCanonicalFile()))
            throw new IOException(context.getString(R.string.driver_error_bad_path));
        try { DriverArchive.delete(target); }
        catch (DriverArchive.Failure failure) { throw localized(context, failure); }
        if (selectedId(context).equals(driver.id)) select(context, "");
    }
    /** {hooks dir, temp dir, driver dir, library}; empty driver fields select the system driver. */
    static String[] loaderArguments(Context context) {
        Driver driver = android.os.Process.is64Bit() ? selected(context) : null;
        return new String[] {context.getApplicationInfo().nativeLibraryDir, context.getCacheDir().getAbsolutePath(),
                driver == null ? "" : driver.directory.getAbsolutePath() + "/", driver == null ? "" : driver.library};
    }
}
