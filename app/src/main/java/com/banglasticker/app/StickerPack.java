package com.banglasticker.app;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class StickerPack implements Parcelable {
    final String identifier;
    final String name;
    final String publisher;
    final String trayImageFile;
    final String publisherEmail;
    final String publisherWebsite;
    final String privacyPolicyWebsite;
    final String licenseAgreementWebsite;
    final String imageDataVersion;
    final boolean avoidCache;
    final boolean animatedStickerPack;

    String iosAppStoreLink;
    String androidPlayStoreLink;
    private List<Sticker> stickers;
    long totalSize;
    private boolean isWhitelisted;

    StickerPack(String identifier, String name, String publisher, String trayImageFile,
                String publisherEmail, String publisherWebsite, String privacyPolicyWebsite,
                String licenseAgreementWebsite, String imageDataVersion, boolean avoidCache,
                boolean animatedStickerPack) {
        this.identifier = identifier;
        this.name = name;
        this.publisher = publisher;
        this.trayImageFile = trayImageFile;
        this.publisherEmail = publisherEmail;
        this.publisherWebsite = publisherWebsite;
        this.privacyPolicyWebsite = privacyPolicyWebsite;
        this.licenseAgreementWebsite = licenseAgreementWebsite;
        this.imageDataVersion = imageDataVersion;
        this.avoidCache = avoidCache;
        this.animatedStickerPack = animatedStickerPack;
    }

    void setStickers(List<Sticker> stickers) {
        this.stickers = stickers;
        long total = 0;
        for (Sticker sticker : stickers) {
            total += sticker.size;
        }
        this.totalSize = total;
    }

    List<Sticker> getStickers() {
        return stickers;
    }

    void setIsWhitelisted(boolean whitelisted) {
        isWhitelisted = whitelisted;
    }

    boolean getIsWhitelisted() {
        return isWhitelisted;
    }

    void setAndroidPlayStoreLink(String androidPlayStoreLink) {
        this.androidPlayStoreLink = androidPlayStoreLink;
    }

    void setIosAppStoreLink(String iosAppStoreLink) {
        this.iosAppStoreLink = iosAppStoreLink;
    }

    private StickerPack(Parcel in) {
        identifier = in.readString();
        name = in.readString();
        publisher = in.readString();
        trayImageFile = in.readString();
        publisherEmail = in.readString();
        publisherWebsite = in.readString();
        privacyPolicyWebsite = in.readString();
        licenseAgreementWebsite = in.readString();
        iosAppStoreLink = in.readString();
        androidPlayStoreLink = in.readString();
        imageDataVersion = in.readString();
        avoidCache = in.readByte() != 0;
        animatedStickerPack = in.readByte() != 0;
        stickers = in.createTypedArrayList(Sticker.CREATOR);
        totalSize = in.readLong();
        isWhitelisted = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(identifier);
        dest.writeString(name);
        dest.writeString(publisher);
        dest.writeString(trayImageFile);
        dest.writeString(publisherEmail);
        dest.writeString(publisherWebsite);
        dest.writeString(privacyPolicyWebsite);
        dest.writeString(licenseAgreementWebsite);
        dest.writeString(iosAppStoreLink);
        dest.writeString(androidPlayStoreLink);
        dest.writeString(imageDataVersion);
        dest.writeByte((byte) (avoidCache ? 1 : 0));
        dest.writeByte((byte) (animatedStickerPack ? 1 : 0));
        dest.writeTypedList(stickers);
        dest.writeLong(totalSize);
        dest.writeByte((byte) (isWhitelisted ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<StickerPack> CREATOR = new Creator<StickerPack>() {
        @Override
        public StickerPack createFromParcel(Parcel in) {
            return new StickerPack(in);
        }

        @Override
        public StickerPack[] newArray(int size) {
            return new StickerPack[size];
        }
    };
}
