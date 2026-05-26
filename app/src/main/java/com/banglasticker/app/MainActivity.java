package com.banglasticker.app;

import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private StickerPack stickerPack;
    private Button addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addButton = findViewById(R.id.add_to_whatsapp_button);
        executor.execute(this::loadPack);
    }

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

        addButton.setOnClickListener(v -> addStickerPackToWhatsApp(pack));
        refreshAddButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private void addStickerPackToWhatsApp(@NonNull StickerPack pack) {
        final Intent intent = new Intent();
        intent.setAction(ENABLE_STICKER_PACK_ACTION);
        intent.putExtra(EXTRA_STICKER_PACK_ID, pack.identifier);
        intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        intent.putExtra(EXTRA_STICKER_PACK_NAME, pack.name);
        try {
            startActivityForResult(intent, ADD_PACK_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.add_pack_fail, Toast.LENGTH_LONG).show();
        }
    }

    private void shareSticker(@NonNull Sticker sticker) {
        executor.execute(() -> {
            final Uri uri = copyStickerToCache(sticker);
            if (uri == null) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.share_fail, Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> launchShare(uri));
        });
    }

    private Uri copyStickerToCache(@NonNull Sticker sticker) {
        // Share as PNG: many apps (e.g. Facebook Messenger) reject image/webp.
        try {
            final File outDir = new File(getCacheDir(), "shared");
            if (!outDir.exists() && !outDir.mkdirs()) {
                return null;
            }
            final Bitmap bitmap;
            try (InputStream in = getAssets().open(stickerPack.identifier + "/" + sticker.imageFileName)) {
                bitmap = BitmapFactory.decodeStream(in);
            }
            if (bitmap == null) {
                return null;
            }
            final String pngName = sticker.imageFileName.replaceFirst("\\.webp$", "") + ".png";
            final File outFile = new File(outDir, pngName);
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
        // Grant read permission to every app the chooser might surface.
        for (ResolveInfo info : getPackageManager().queryIntentActivities(share, 0)) {
            grantUriPermission(info.activityInfo.packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_sticker)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
