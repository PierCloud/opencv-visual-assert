package it.aruba.qaa.cv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class VisualAssertUtil {

    private VisualAssertUtil() {
    }

    public static Path saveBaselineScreenshot(byte[] screenshotBytes, String imageKey) {
        Objects.requireNonNull(screenshotBytes, "screenshotBytes");
        String normalizedKey = ImageKey.normalize(imageKey);
        return saveBaselineScreenshot(screenshotBytes, normalizedKey, BaselineDirectoryResolver.resolve());
    }

    public static Path saveBaselineScreenshot(byte[] screenshotBytes, String imageKey, Path baselineDirectory) {
        Objects.requireNonNull(screenshotBytes, "screenshotBytes");
        Objects.requireNonNull(baselineDirectory, "baselineDirectory");
        String normalizedKey = ImageKey.normalize(imageKey);
        Path outputPath = baselineDirectory.resolve(normalizedKey + ".png").toAbsolutePath().normalize();

        try {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, screenshotBytes);
            if (!Files.isRegularFile(outputPath)) {
                throw new VisualAssertException("Baseline screenshot was not written: " + outputPath);
            }
            return outputPath;
        } catch (IOException e) {
            throw new VisualAssertException("Unable to save baseline screenshot for key: " + normalizedKey, e);
        }
    }
}
