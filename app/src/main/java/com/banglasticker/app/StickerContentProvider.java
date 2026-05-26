package com.banglasticker.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Exposes the bundled sticker packs to WhatsApp through the documented
 * third-party sticker ContentProvider contract.
 */
public class StickerContentProvider extends ContentProvider {

    public static final String STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier";
    public static final String STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name";
    public static final String STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher";
    public static final String STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon";
    public static final String ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link";
    public static final String IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link";
    public static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
    public static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
    public static final String PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website";
    public static final String LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website";
    public static final String IMAGE_DATA_VERSION = "image_data_version";
    public static final String AVOID_CACHE = "avoid_cache";
    public static final String ANIMATED_STICKER_PACK = "animated_sticker_pack";

    public static final String STICKER_FILE_NAME_IN_QUERY = "sticker_file_name";
    public static final String STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji";
    public static final String STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text";

    public static final String CONTENT_FILE_NAME = "contents.json";

    static final String METADATA = "metadata";
    static final String STICKERS = "stickers";
    static final String STICKERS_ASSET = "stickers_asset";

    private static final int METADATA_CODE = 1;
    private static final int METADATA_CODE_FOR_SINGLE_PACK = 2;
    private static final int STICKERS_CODE = 3;
    private static final int STICKERS_ASSET_CODE = 4;

    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
    private List<StickerPack> stickerPackList;

    @Override
    public boolean onCreate() {
        final String authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY;
        matcher.addURI(authority, METADATA, METADATA_CODE);
        matcher.addURI(authority, METADATA + "/*", METADATA_CODE_FOR_SINGLE_PACK);
        matcher.addURI(authority, STICKERS + "/*", STICKERS_CODE);
        matcher.addURI(authority, STICKERS_ASSET + "/*/*", STICKERS_ASSET_CODE);
        return true;
    }

    private synchronized void readContentFile() {
        if (stickerPackList != null) {
            return;
        }
        try (InputStream contentsInputStream = getContext().getAssets().open(CONTENT_FILE_NAME)) {
            stickerPackList = parseStickerPacks(contentsInputStream);
        } catch (IOException | JSONException e) {
            throw new RuntimeException("Unable to read " + CONTENT_FILE_NAME, e);
        }
    }

    List<StickerPack> getStickerPackList() {
        readContentFile();
        // Bundled packs are cached; the user's custom pack is rebuilt each call so
        // newly imported or deleted stickers are reflected immediately.
        final List<StickerPack> all = new ArrayList<>(stickerPackList);
        final StickerPack customPack = CustomStickerStore.buildPack(getContext());
        if (customPack != null) {
            all.add(customPack);
        }
        return all;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        readContentFile();
        switch (matcher.match(uri)) {
            case METADATA_CODE:
                return getPackForAllStickerPacks(uri);
            case METADATA_CODE_FOR_SINGLE_PACK:
                return getCursorForSingleStickerPack(uri);
            case STICKERS_CODE:
                return getStickersForAStickerPack(uri);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) {
        if (matcher.match(uri) != STICKERS_ASSET_CODE) {
            return null;
        }
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 3) {
            throw new IllegalArgumentException("Bad asset URI: " + uri);
        }
        final String fileName = pathSegments.get(pathSegments.size() - 1);
        final String identifier = pathSegments.get(pathSegments.size() - 2);
        if (TextUtils.isEmpty(identifier) || TextUtils.isEmpty(fileName)) {
            throw new IllegalArgumentException("Bad asset URI: " + uri);
        }
        if (CustomStickerStore.CUSTOM_PACK_ID.equals(identifier)) {
            return fetchCustomFile(fileName);
        }
        return fetchFile(identifier, fileName);
    }

    private AssetFileDescriptor fetchCustomFile(@NonNull String fileName) {
        final File file = CustomStickerStore.TRAY_FILE_NAME.equals(fileName)
                ? CustomStickerStore.trayFile(getContext())
                : new File(CustomStickerStore.dir(getContext()), fileName);
        try {
            return new AssetFileDescriptor(
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                    0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private AssetFileDescriptor fetchFile(@NonNull String identifier, @NonNull String fileName) {
        final AssetManager am = getContext().getAssets();
        try {
            return am.openFd(identifier + "/" + fileName);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (matcher.match(uri)) {
            case METADATA_CODE:
            case METADATA_CODE_FOR_SINGLE_PACK:
                return "vnd.android.cursor.dir/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + METADATA;
            case STICKERS_CODE:
                return "vnd.android.cursor.dir/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + STICKERS;
            case STICKERS_ASSET_CODE:
                return "image/webp";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    private Cursor getPackForAllStickerPacks(@NonNull Uri uri) {
        return getStickerPackInfo(uri, getStickerPackList());
    }

    private Cursor getCursorForSingleStickerPack(@NonNull Uri uri) {
        final String identifier = uri.getLastPathSegment();
        final List<StickerPack> result = new ArrayList<>();
        for (StickerPack pack : getStickerPackList()) {
            if (identifier != null && identifier.equals(pack.identifier)) {
                result.add(pack);
            }
        }
        return getStickerPackInfo(uri, result);
    }

    @NonNull
    private Cursor getStickerPackInfo(@NonNull Uri uri, @NonNull List<StickerPack> packs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
        });
        for (StickerPack pack : packs) {
            MatrixCursor.RowBuilder builder = cursor.newRow();
            builder.add(pack.identifier);
            builder.add(pack.name);
            builder.add(pack.publisher);
            builder.add(pack.trayImageFile);
            builder.add(pack.androidPlayStoreLink);
            builder.add(pack.iosAppStoreLink);
            builder.add(pack.publisherEmail);
            builder.add(pack.publisherWebsite);
            builder.add(pack.privacyPolicyWebsite);
            builder.add(pack.licenseAgreementWebsite);
            builder.add(pack.imageDataVersion);
            builder.add(pack.avoidCache ? 1 : 0);
            builder.add(pack.animatedStickerPack ? 1 : 0);
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @NonNull
    private Cursor getStickersForAStickerPack(@NonNull Uri uri) {
        final String identifier = uri.getLastPathSegment();
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_FILE_NAME_IN_QUERY,
                STICKER_FILE_EMOJI_IN_QUERY,
                STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY,
        });
        for (StickerPack pack : getStickerPackList()) {
            if (identifier != null && identifier.equals(pack.identifier)) {
                for (Sticker sticker : pack.getStickers()) {
                    cursor.addRow(new Object[]{
                            sticker.imageFileName,
                            TextUtils.join(",", sticker.emojis),
                            sticker.accessibilityText,
                    });
                }
            }
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    // ----- JSON parsing -----

    private List<StickerPack> parseStickerPacks(InputStream in) throws IOException, JSONException {
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        final JSONObject root = new JSONObject(sb.toString());
        final String androidLink = root.optString("android_play_store_link");
        final String iosLink = root.optString("ios_app_store_link");
        final JSONArray packsJson = root.getJSONArray("sticker_packs");
        final List<StickerPack> packs = new ArrayList<>();
        for (int i = 0; i < packsJson.length(); i++) {
            final JSONObject p = packsJson.getJSONObject(i);
            final StickerPack pack = new StickerPack(
                    p.getString("identifier"),
                    p.getString("name"),
                    p.getString("publisher"),
                    p.getString("tray_image_file"),
                    p.optString("publisher_email"),
                    p.optString("publisher_website"),
                    p.optString("privacy_policy_website"),
                    p.optString("license_agreement_website"),
                    p.optString("image_data_version", "1"),
                    p.optBoolean("avoid_cache", false),
                    p.optBoolean("animated_sticker_pack", false));
            pack.setAndroidPlayStoreLink(androidLink);
            pack.setIosAppStoreLink(iosLink);

            final JSONArray stickersJson = p.getJSONArray("stickers");
            final List<Sticker> stickers = new ArrayList<>();
            for (int j = 0; j < stickersJson.length(); j++) {
                final JSONObject s = stickersJson.getJSONObject(j);
                final List<String> emojis = new ArrayList<>();
                final JSONArray emojiArray = s.optJSONArray("emojis");
                if (emojiArray != null) {
                    for (int k = 0; k < emojiArray.length(); k++) {
                        emojis.add(emojiArray.getString(k));
                    }
                }
                stickers.add(new Sticker(
                        s.getString("image_file"),
                        emojis,
                        s.optString("accessibility_text")));
            }
            pack.setStickers(stickers);
            packs.add(pack);
        }
        return packs;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }
}
