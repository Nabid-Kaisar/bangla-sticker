package com.banglasticker.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class StickerGridAdapter extends RecyclerView.Adapter<StickerGridAdapter.StickerViewHolder> {

    interface OnStickerClickListener {
        void onStickerClick(@NonNull Sticker sticker);
    }

    private final String packIdentifier;
    private final List<Sticker> stickers;
    private final OnStickerClickListener listener;
    private final android.content.res.AssetManager assetManager;

    StickerGridAdapter(@NonNull String packIdentifier,
                       @NonNull List<Sticker> stickers,
                       @NonNull android.content.res.AssetManager assetManager,
                       @NonNull OnStickerClickListener listener) {
        this.packIdentifier = packIdentifier;
        this.stickers = new ArrayList<>(stickers);
        this.assetManager = assetManager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StickerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker, parent, false);
        return new StickerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StickerViewHolder holder, int position) {
        final Sticker sticker = stickers.get(position);
        final Bitmap bitmap = decode(packIdentifier + "/" + sticker.imageFileName);
        if (bitmap != null) {
            holder.imageView.setImageBitmap(bitmap);
        }
        holder.imageView.setContentDescription(sticker.accessibilityText);
        holder.itemView.setOnClickListener(v -> listener.onStickerClick(sticker));
    }

    private Bitmap decode(@NonNull String assetPath) {
        try (InputStream in = assetManager.open(assetPath)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return stickers.size();
    }

    static class StickerViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        StickerViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.sticker_image);
        }
    }
}
