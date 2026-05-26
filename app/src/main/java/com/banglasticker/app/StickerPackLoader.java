package com.banglasticker.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads sticker packs back out of {@link StickerContentProvider} via the ContentResolver. */
final class StickerPackLoader {

    private StickerPackLoader() {
    }

    @NonNull
    static List<StickerPack> fetchStickerPacks(@NonNull Context context) {
        final Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendPath(StickerContentProvider.METADATA)
                .build();
        final List<StickerPack> packs = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    packs.add(readPack(cursor));
                } while (cursor.moveToNext());
            }
        }
        for (StickerPack pack : packs) {
            pack.setStickers(fetchStickers(context, pack.identifier));
        }
        return packs;
    }

    private static StickerPack readPack(@NonNull Cursor cursor) {
        final String identifier = col(cursor, StickerContentProvider.STICKER_PACK_IDENTIFIER_IN_QUERY);
        final String name = col(cursor, StickerContentProvider.STICKER_PACK_NAME_IN_QUERY);
        final String publisher = col(cursor, StickerContentProvider.STICKER_PACK_PUBLISHER_IN_QUERY);
        final String tray = col(cursor, StickerContentProvider.STICKER_PACK_ICON_IN_QUERY);
        final StickerPack pack = new StickerPack(identifier, name, publisher, tray,
                col(cursor, StickerContentProvider.PUBLISHER_EMAIL),
                col(cursor, StickerContentProvider.PUBLISHER_WEBSITE),
                col(cursor, StickerContentProvider.PRIVACY_POLICY_WEBSITE),
                col(cursor, StickerContentProvider.LICENSE_AGREEMENT_WEBSITE),
                col(cursor, StickerContentProvider.IMAGE_DATA_VERSION),
                intCol(cursor, StickerContentProvider.AVOID_CACHE) == 1,
                intCol(cursor, StickerContentProvider.ANIMATED_STICKER_PACK) == 1);
        pack.setAndroidPlayStoreLink(col(cursor, StickerContentProvider.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY));
        pack.setIosAppStoreLink(col(cursor, StickerContentProvider.IOS_APP_DOWNLOAD_LINK_IN_QUERY));
        return pack;
    }

    @NonNull
    private static List<Sticker> fetchStickers(@NonNull Context context, @NonNull String identifier) {
        final Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendPath(StickerContentProvider.STICKERS)
                .appendPath(identifier)
                .build();
        final List<Sticker> stickers = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    final String fileName = col(cursor, StickerContentProvider.STICKER_FILE_NAME_IN_QUERY);
                    final String emojis = col(cursor, StickerContentProvider.STICKER_FILE_EMOJI_IN_QUERY);
                    final String alt = col(cursor, StickerContentProvider.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY);
                    final List<String> emojiList = TextUtils.isEmpty(emojis)
                            ? new ArrayList<>() : Arrays.asList(emojis.split(","));
                    stickers.add(new Sticker(fileName, emojiList, alt));
                } while (cursor.moveToNext());
            }
        }
        return stickers;
    }

    static Uri getStickerAssetUri(@NonNull String identifier, @NonNull String fileName) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendPath(StickerContentProvider.STICKERS_ASSET)
                .appendPath(identifier)
                .appendPath(fileName)
                .build();
    }

    private static String col(@NonNull Cursor cursor, @NonNull String name) {
        final int index = cursor.getColumnIndex(name);
        return index >= 0 ? cursor.getString(index) : "";
    }

    private static int intCol(@NonNull Cursor cursor, @NonNull String name) {
        final int index = cursor.getColumnIndex(name);
        return index >= 0 ? cursor.getInt(index) : 0;
    }
}
