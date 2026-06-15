package com.back.popspot.global.s3;

public enum ImageDomain {
    GOODS("goods"),
    POPUP("popups");

    private final String root;

    ImageDomain(String root) {
        this.root = root;
    }

    public String finalKey(Long ownerId, String typeSegment, String fileName) {
        return String.format("%s/%d/%s/%s", root, ownerId, typeSegment, fileName);
    }

    public String finalKey(Long ownerId, String fileName) {
        return String.format("%s/%d/%s", root, ownerId, fileName);
    }
}
