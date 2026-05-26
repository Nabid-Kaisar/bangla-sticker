package com.banglasticker.app;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class CustomStickerAdapter extends RecyclerView.Adapter<CustomStickerAdapter.ViewHolder> {

    interface Listener {
        void onClick(@NonNull File file);

        void onLongClick(@NonNull File file);
    }

    private final List<File> files = new ArrayList<>();
    private final Listener listener;

    CustomStickerAdapter(@NonNull List<File> files, @NonNull Listener listener) {
        this.files.addAll(files);
        this.listener = listener;
    }

    void submit(@NonNull List<File> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final File file = files.get(position);
        holder.imageView.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
        holder.imageView.setContentDescription(holder.itemView.getContext()
                .getString(R.string.custom_sticker));
        holder.itemView.setOnClickListener(v -> listener.onClick(file));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(file);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.sticker_image);
        }
    }
}
