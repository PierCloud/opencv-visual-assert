package it.aruba.qaa.cv;

import java.nio.file.Path;
import java.util.Optional;

public record VisualCompareResult(
        boolean passed,
        long diffPixels,
        long comparedPixels,
        double diffPercent,
        Path expectedImage,
        Optional<Path> actualImage,
        Optional<Path> diffImage,
        String message
) {

    public String failureMessage() {
        if (passed) {
            return "Visual comparison passed: " + message;
        }

        return "Visual comparison failed: " + message
                + ", diffPixels=" + diffPixels
                + ", comparedPixels=" + comparedPixels
                + ", diffPercent=" + String.format("%.6f", diffPercent)
                + ", expected=" + expectedImage
                + actualImage.map(path -> ", actual=" + path).orElse("")
                + diffImage.map(path -> ", diff=" + path).orElse("");
    }
}
