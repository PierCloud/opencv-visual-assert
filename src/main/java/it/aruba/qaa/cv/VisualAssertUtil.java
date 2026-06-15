package it.aruba.qaa.cv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class VisualAssertUtil {

    private static final Path BASELINE_DIRECTORY = Path.of("resources", "test", "cv_img");
    private static final Path RESOURCE_TEST_DIRECTORY = Path.of("resources", "test");

    private VisualAssertUtil() {
    }

    public static Path saveBaselineScreenshot(byte[] screenshotBytes, String imageKey) {
        Objects.requireNonNull(screenshotBytes, "screenshotBytes");
        String normalizedKey = normalizeImageKey(imageKey);
        return saveBaselineScreenshot(screenshotBytes, normalizedKey, resolveBaselineDirectory());
    }

    public static Path saveBaselineScreenshot(byte[] screenshotBytes, String imageKey, Path baselineDirectory) {
        Objects.requireNonNull(screenshotBytes, "screenshotBytes");
        Objects.requireNonNull(baselineDirectory, "baselineDirectory");
        String normalizedKey = normalizeImageKey(imageKey);
        Path outputPath = baselineDirectory.resolve(normalizedKey + ".png").toAbsolutePath().normalize();

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

    private static Path resolveBaselineDirectory() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null) {
            Path resourceTestDirectory = cursor.resolve(RESOURCE_TEST_DIRECTORY);
            if (Files.isDirectory(resourceTestDirectory)) {
                return resourceTestDirectory.resolve("cv_img");
            }
            cursor = cursor.getParent();
        }

        return current.resolve(BASELINE_DIRECTORY);
    }
}
