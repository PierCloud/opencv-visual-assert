package it.aruba.qaa.cv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class VisualAssertUtil {

    private static final Path BASELINE_DIRECTORY = Path.of("resources", "test", "cv_img");

    private VisualAssertUtil() {
    }

    public static Path saveBaselineScreenshot(byte[] screenshotBytes, String imageKey) {
        Objects.requireNonNull(screenshotBytes, "screenshotBytes");
        String normalizedKey = normalizeImageKey(imageKey);
        Path outputPath = BASELINE_DIRECTORY.resolve(normalizedKey + ".png");

        try {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, screenshotBytes);
            return outputPath;
        } catch (IOException e) {
            throw new VisualAssertException("Unable to save baseline screenshot for key: " + normalizedKey, e);
        }
    }

    public static VisualCompareResult compareScreenshot(byte[] screenshotBytes, String imageKey) {
        String normalizedKey = normalizeImageKey(imageKey);
        return VisualAssert.compareScreenshotBytes(
                screenshotBytes,
                normalizedKey,
                VisualCompareOptions.builder()
                        .artifactName(normalizedKey)
                        .maxDiffPercent(0.25)
                        .pixelTolerance(12)
                        .build()
        );
    }

    private static String normalizeImageKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("imageKey must not be blank");
        }

        String normalizedKey = imageKey.trim();
        if (normalizedKey.endsWith(".png")) {
            normalizedKey = normalizedKey.substring(0, normalizedKey.length() - 4);
        }
        return normalizedKey;
    }
}
