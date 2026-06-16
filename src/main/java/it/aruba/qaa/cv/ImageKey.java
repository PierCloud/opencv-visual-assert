package it.aruba.qaa.cv;

final class ImageKey {

    private ImageKey() {
    }

    static String artifactName(String imageKeyOrPath) {
        String normalized = normalize(imageKeyOrPath).replace('\\', '/');
        int lastSeparator = normalized.lastIndexOf('/');
        if (lastSeparator >= 0) {
            return normalized.substring(lastSeparator + 1);
        }
        return normalized;
    }

    static String normalize(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("imageKey must not be blank");
        }

        String normalizedKey = imageKey.trim();
        if (normalizedKey.endsWith(".png")) {
            return normalizedKey.substring(0, normalizedKey.length() - 4);
        }
        if (normalizedKey.endsWith(".jpg")) {
            return normalizedKey.substring(0, normalizedKey.length() - 4);
        }
        if (normalizedKey.endsWith(".jpeg")) {
            return normalizedKey.substring(0, normalizedKey.length() - 5);
        }
        return normalizedKey;
    }
}
