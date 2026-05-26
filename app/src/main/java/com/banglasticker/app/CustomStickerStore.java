package com.banglasticker.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stores and manages user-imported stickers in the app's private storage. */
final class CustomStickerStore {

    static final String CUSTOM_PACK_ID = "custom_pack";
    static final String CUSTOM_PACK_NAME = "My Stickers";
    static final String TRAY_FILE_NAME = "custom_tray.webp";
    static final int MIN_FOR_WHATSAPP = 3;
    static final int MAX_STICKERS = 30;

    private static final String FILE_PREFIX = "cs_";
    private static final String DIR_NAME = "custom_stickers";
    private static final int STICKER_SIZE = 512;
    private static final int TRAY_SIZE = 96;
    private static final int MAX_STICKER_BYTES = 100 * 1024;
    private static final int MAX_TRAY_BYTES = 50 * 1024;

    private CustomStickerStore() {
    }

    static File dir(@NonNull Context ctx) {
        final File dir = new File(ctx.getFilesDir(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static File trayFile(@NonNull Context ctx) {
        return new File(ctx.getFilesDir(), TRAY_FILE_NAME);
    }

    @NonNull
    static List<File> list(@NonNull Context ctx) {
        final File[] files = dir(ctx).listFiles(
                (d, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(".webp"));
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        return new ArrayList<>(Arrays.asList(files));
    }

    static int count(@NonNull Context ctx) {
        return list(ctx).size();
    }

    /** Imports an image from a content URI, returning the saved sticker file or null. */
    @Nullable
    static File importImage(@NonNull Context ctx, @NonNull Uri uri) {
        try {
            final Bitmap decoded = decodeUri(ctx, uri);
            if (decoded == null) {
                return null;
            }
            final Bitmap sticker = toStickerBitmap(decoded);
            if (sticker != decoded) {
                decoded.recycle();
            }
            final File out = new File(dir(ctx), FILE_PREFIX + System.currentTimeMillis() + ".webp");
            final boolean ok = compressWebp(sticker, out, MAX_STICKER_BYTES);
            sticker.recycle();
            if (!ok) {
                out.delete();
                return null;
            }
            regenerateTray(ctx);
            bumpVersion(ctx);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    static void delete(@NonNull Context ctx, @NonNull File file) {
        if (file.delete()) {
            regenerateTray(ctx);
            bumpVersion(ctx);
        }
    }

    /** Builds the WhatsApp-facing custom pack, or null when there are no custom stickers. */
    @Nullable
    static StickerPack buildPack(@NonNull Context ctx) {
        final List<File> files = list(ctx);
        if (files.isEmpty()) {
            return null;
        }
        final StickerPack pack = new StickerPack(
                CUSTOM_PACK_ID, CUSTOM_PACK_NAME, "Bangla Sticker App", TRAY_FILE_NAME,
                "", "", "", "", String.valueOf(version(ctx)), true, false);
        final List<Sticker> stickers = new ArrayList<>();
        for (File f : files) {
            stickers.add(new Sticker(f.getName(),
                    Collections.singletonList("⭐"), "Custom sticker"));
        }
        pack.setStickers(stickers);
        pack.setAndroidPlayStoreLink("");
        pack.setIosAppStoreLink("");
        return pack;
    }

    // ----- image processing -----

    @Nullable
    private static Bitmap decodeUri(@NonNull Context ctx, @NonNull Uri uri) throws IOException {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / sample > 1024) {
            sample *= 2;
        }
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) {
            return null;
        }
        return applyExifRotation(ctx, uri, bitmap);
    }

    private static Bitmap applyExifRotation(@NonNull Context ctx, @NonNull Uri uri,
                                            @NonNull Bitmap bitmap) {
        int rotation = 0;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                final ExifInterface exif = new ExifInterface(in);
                switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL)) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        rotation = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        rotation = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        rotation = 270;
                        break;
                    default:
                        rotation = 0;
                }
            }
        } catch (Exception ignored) {
        }
        if (rotation == 0) {
            return bitmap;
        }
        final Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        final Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    private static Bitmap toStickerBitmap(@NonNull Bitmap src) {
        final float scale = Math.min((float) STICKER_SIZE / src.getWidth(),
                (float) STICKER_SIZE / src.getHeight());
        final int nw = Math.max(1, Math.round(src.getWidth() * scale));
        final int nh = Math.max(1, Math.round(src.getHeight() * scale));
        final Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);
        final Bitmap out = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(out);
        canvas.drawBitmap(scaled, (STICKER_SIZE - nw) / 2f, (STICKER_SIZE - nh) / 2f, null);
        if (scaled != src) {
            scaled.recycle();
        }
        return out;
    }

    private static void regenerateTray(@NonNull Context ctx) {
        final List<File> files = list(ctx);
        final File tray = trayFile(ctx);
        if (files.isEmpty()) {
            if (tray.exists()) {
                tray.delete();
            }
            return;
        }
        final Bitmap source = BitmapFactory.decodeFile(files.get(0).getAbsolutePath());
        if (source == null) {
            return;
        }
        final Bitmap trayBitmap = Bitmap.createScaledBitmap(source, TRAY_SIZE, TRAY_SIZE, true);
        compressWebp(trayBitmap, tray, MAX_TRAY_BYTES);
        if (trayBitmap != source) {
            trayBitmap.recycle();
        }
        source.recycle();
    }

    private static boolean compressWebp(@NonNull Bitmap bitmap, @NonNull File out, int maxBytes) {
        final Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        int quality = 90;
        while (quality >= 30) {
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(format, quality, fos);
            } catch (IOException e) {
                return false;
            }
            if (out.length() <= maxBytes) {
                return true;
            }
            quality -= 15;
        }
        return out.exists() && out.length() <= maxBytes;
    }

    // ----- versioning (forces WhatsApp to re-import after changes) -----

    private static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getSharedPreferences("custom_stickers", Context.MODE_PRIVATE);
    }

    static int version(@NonNull Context ctx) {
        return prefs(ctx).getInt("version", 1);
    }

    private static void bumpVersion(@NonNull Context ctx) {
        prefs(ctx).edit().putInt("version", version(ctx) + 1).apply();
    }
}
