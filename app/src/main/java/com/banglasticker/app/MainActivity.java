package com.banglasticker.app;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String ENABLE_STICKER_PACK_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK";
    private static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    private static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    private static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";
    private static final int ADD_PACK_REQUEST_CODE = 200;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    importSticker(uri);
                }
            });

    private StickerPack stickerPack;
    private Button addButton;
    private Button importButton;
    private Button addCustomButton;
    private TextView customCount;
    private TextView customEmpty;
    private RecyclerView customGrid;
    private CustomStickerAdapter customAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addButton = findViewById(R.id.add_to_whatsapp_button);
        importButton = findViewById(R.id.import_button);
        addCustomButton = findViewById(R.id.add_custom_to_whatsapp_button);
        customCount = findViewById(R.id.custom_count);
        customEmpty = findViewById(R.id.custom_empty);
        customGrid = findViewById(R.id.custom_grid);

        customGrid.setLayoutManager(new GridLayoutManager(this, 3));
        customAdapter = new CustomStickerAdapter(new ArrayList<>(), new CustomStickerAdapter.Listener() {
            @Override
            public void onClick(@NonNull File file) {
                shareCustom(file);
            }

            @Override
            public void onLongClick(@NonNull File file) {
                confirmDelete(file);
            }
        });
        customGrid.setAdapter(customAdapter);

        importButton.setOnClickListener(v -> {
            if (CustomStickerStore.count(this) >= CustomStickerStore.MAX_STICKERS) {
                Toast.makeText(this, getString(R.string.custom_full, CustomStickerStore.MAX_STICKERS),
                        Toast.LENGTH_LONG).show();
                return;
            }
            imagePicker.launch("image/*");
        });
        addCustomButton.setOnClickListener(v ->
                addStickerPackToWhatsApp(CustomStickerStore.CUSTOM_PACK_ID, CustomStickerStore.CUSTOM_PACK_NAME));

        executor.execute(this::loadPack);
        refreshCustom();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAddButton();
        refreshCustom();
    }

    // ----- built-in pack -----

    private void loadPack() {
        final List<StickerPack> packs = StickerPackLoader.fetchStickerPacks(this);
        runOnUiThread(() -> {
            if (packs.isEmpty()) {
                Toast.makeText(this, R.string.no_stickers_found, Toast.LENGTH_LONG).show();
                return;
            }
            stickerPack = packs.get(0);
            bindPack(stickerPack);
        });
    }

    private void bindPack(@NonNull StickerPack pack) {
        ((TextView) findViewById(R.id.pack_name)).setText(pack.name);
        ((TextView) findViewById(R.id.pack_publisher))
                .setText(getString(R.string.by_publisher, pack.publisher));
        ((TextView) findViewById(R.id.pack_count))
                .setText(getResources().getQuantityString(R.plurals.sticker_count,
                        pack.getStickers().size(), pack.getStickers().size()));

        final ImageView tray = findViewById(R.id.pack_tray);
        try (InputStream in = getAssets().open(pack.identifier + "/" + pack.trayImageFile)) {
            tray.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (IOException ignored) {
        }

        final RecyclerView recyclerView = findViewById(R.id.sticker_grid);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(new StickerGridAdapter(pack.identifier, pack.getStickers(),
                getAssets(), this::shareSticker));

        addButton.setOnClickListener(v -> addStickerPackToWhatsApp(pack.identifier, pack.name));
        refreshAddButton();
    }

    private void refreshAddButton() {
        if (stickerPack == null) {
            return;
        }
        executor.execute(() -> {
            final boolean whatsappInstalled = WhitelistCheck.isWhatsAppInstalled(this);
            final boolean added = WhitelistCheck.isWhitelisted(this, stickerPack.identifier);
            runOnUiThread(() -> {
                if (!whatsappInstalled) {
                    addButton.setText(R.string.whatsapp_not_installed);
                    addButton.setEnabled(false);
                } else if (added) {
                    addButton.setText(R.string.added_to_whatsapp);
                    addButton.setEnabled(false);
                } else {
                    addButton.setText(R.string.add_to_whatsapp);
                    addButton.setEnabled(true);
                }
            });
        });
    }

    // ----- custom stickers -----

    private void refreshCustom() {
        executor.execute(() -> {
            final List<File> files = CustomStickerStore.list(this);
            final boolean whatsappInstalled = WhitelistCheck.isWhatsAppInstalled(this);
            runOnUiThread(() -> {
                customAdapter.submit(files);
                final boolean empty = files.isEmpty();
                customEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                customGrid.setVisibility(empty ? View.GONE : View.VISIBLE);
                customCount.setText(getResources().getQuantityString(
                        R.plurals.custom_sticker_count, files.size(), files.size()));
                if (files.size() < CustomStickerStore.MIN_FOR_WHATSAPP) {
                    addCustomButton.setText(
                            getString(R.string.custom_need_more, CustomStickerStore.MIN_FOR_WHATSAPP));
                    addCustomButton.setEnabled(false);
                } else if (!whatsappInstalled) {
                    addCustomButton.setText(R.string.whatsapp_not_installed);
                    addCustomButton.setEnabled(false);
                } else {
                    addCustomButton.setText(R.string.add_custom_to_whatsapp);
                    addCustomButton.setEnabled(true);
                }
            });
        });
    }

    private void importSticker(@NonNull Uri uri) {
        executor.execute(() -> {
            final File file = CustomStickerStore.importImage(this, uri);
            runOnUiThread(() -> {
                Toast.makeText(this, file != null ? R.string.import_success : R.string.import_fail,
                        Toast.LENGTH_SHORT).show();
                if (file != null) {
                    refreshCustom();
                }
            });
        });
    }

    private void confirmDelete(@NonNull File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_sticker_title)
                .setPositiveButton(R.string.delete, (dialog, which) -> executor.execute(() -> {
                    CustomStickerStore.delete(this, file);
                    runOnUiThread(this::refreshCustom);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ----- WhatsApp + sharing (shared by both sources) -----

    private void addStickerPackToWhatsApp(@NonNull String identifier, @NonNull String name) {
        final Intent intent = new Intent();
        intent.setAction(ENABLE_STICKER_PACK_ACTION);
        intent.putExtra(EXTRA_STICKER_PACK_ID, identifier);
        intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        intent.putExtra(EXTRA_STICKER_PACK_NAME, name);
        try {
            startActivityForResult(intent, ADD_PACK_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.add_pack_fail, Toast.LENGTH_LONG).show();
        }
    }

    private void shareSticker(@NonNull Sticker sticker) {
        executor.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream in = getAssets().open(stickerPack.identifier + "/" + sticker.imageFileName)) {
                bitmap = BitmapFactory.decodeStream(in);
            } catch (IOException ignored) {
            }
            finishShare(bitmap, sticker.imageFileName);
        });
    }

    private void shareCustom(@NonNull File file) {
        executor.execute(() -> finishShare(
                BitmapFactory.decodeFile(file.getAbsolutePath()), file.getName()));
    }

    private void finishShare(Bitmap bitmap, @NonNull String baseName) {
        final Uri uri = bitmap == null ? null : exportBitmap(bitmap, baseName);
        runOnUiThread(() -> {
            if (uri == null) {
                Toast.makeText(this, R.string.share_fail, Toast.LENGTH_SHORT).show();
            } else {
                launchShare(uri);
            }
        });
    }

    // Stickers are WebP (for WhatsApp); other apps such as Facebook Messenger
    // only reliably accept a PNG that lives in the public MediaStore, so we
    // export there on Android 10+ and fall back to a private FileProvider URI.
    private Uri exportBitmap(@NonNull Bitmap bitmap, @NonNull String baseName) {
        final String pngName = baseName.replaceFirst("\\.webp$", "") + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final Uri mediaUri = saveToMediaStore(bitmap, pngName);
            if (mediaUri != null) {
                return mediaUri;
            }
        }
        return saveToCache(bitmap, pngName);
    }

    private Uri saveToMediaStore(@NonNull Bitmap bitmap, @NonNull String fileName) {
        final ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Bangla Stickers");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        final Uri collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        final Uri item = getContentResolver().insert(collection, values);
        if (item == null) {
            return null;
        }
        try (OutputStream out = getContentResolver().openOutputStream(item)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                getContentResolver().delete(item, null, null);
                return null;
            }
        } catch (IOException e) {
            getContentResolver().delete(item, null, null);
            return null;
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(item, values, null, null);
        return item;
    }

    private Uri saveToCache(@NonNull Bitmap bitmap, @NonNull String fileName) {
        try {
            final File outDir = new File(getCacheDir(), "shared");
            if (!outDir.exists() && !outDir.mkdirs()) {
                return null;
            }
            final File outFile = new File(outDir, fileName);
            try (OutputStream out = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", outFile);
        } catch (IOException e) {
            return null;
        }
    }

    private void launchShare(@NonNull Uri uri) {
        final Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/png");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        // ClipData carries the grant so receiving apps reliably get read access.
        share.setClipData(ClipData.newUri(getContentResolver(), "sticker", uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Manual per-app grants are only valid for our own FileProvider URIs;
        // MediaStore URIs are already readable and would throw here.
        if ((getPackageName() + ".fileprovider").equals(uri.getAuthority())) {
            for (ResolveInfo info : getPackageManager().queryIntentActivities(share, 0)) {
                grantUriPermission(info.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_sticker)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
