package com.banglasticker.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

/** Helpers to detect whether WhatsApp is installed and whether a pack is already added. */
final class WhitelistCheck {

    static final String CONSUMER_WHATSAPP_PACKAGE_NAME = "com.whatsapp";
    static final String SMB_WHATSAPP_PACKAGE_NAME = "com.whatsapp.w4b";

    private static final String AUTHORITY_QUERY_PARAM = "authority";
    private static final String IDENTIFIER_QUERY_PARAM = "identifier";
    private static final String CONTENT_PROVIDER = ".provider.sticker_whitelist_check";
    private static final String QUERY_PATH = "is_whitelisted";
    private static final String QUERY_RESULT_COLUMN_NAME = "result";

    private WhitelistCheck() {
    }

    static boolean isWhatsAppInstalled(@NonNull Context context) {
        final PackageManager pm = context.getPackageManager();
        return isPackageInstalled(CONSUMER_WHATSAPP_PACKAGE_NAME, pm)
                || isPackageInstalled(SMB_WHATSAPP_PACKAGE_NAME, pm);
    }

    static boolean isWhitelisted(@NonNull Context context, @NonNull String identifier) {
        try {
            final PackageManager pm = context.getPackageManager();
            boolean consumerInstalled = isPackageInstalled(CONSUMER_WHATSAPP_PACKAGE_NAME, pm);
            boolean smbInstalled = isPackageInstalled(SMB_WHATSAPP_PACKAGE_NAME, pm);
            if (!consumerInstalled && !smbInstalled) {
                return false;
            }
            boolean consumerOk = !consumerInstalled
                    || isStickerPackWhitelisted(context, identifier, CONSUMER_WHATSAPP_PACKAGE_NAME);
            boolean smbOk = !smbInstalled
                    || isStickerPackWhitelisted(context, identifier, SMB_WHATSAPP_PACKAGE_NAME);
            return consumerOk && smbOk;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isStickerPackWhitelisted(@NonNull Context context,
                                                    @NonNull String identifier,
                                                    @NonNull String whatsappPackage) {
        final Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(whatsappPackage + CONTENT_PROVIDER)
                .appendPath(QUERY_PATH)
                .appendQueryParameter(AUTHORITY_QUERY_PARAM, BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendQueryParameter(IDENTIFIER_QUERY_PARAM, identifier)
                .build();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndex(QUERY_RESULT_COLUMN_NAME);
                return index >= 0 && cursor.getInt(index) == 1;
            }
        }
        return false;
    }

    private static boolean isPackageInstalled(@NonNull String packageName, @NonNull PackageManager pm) {
        try {
            pm.getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
