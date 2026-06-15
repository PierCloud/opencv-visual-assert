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
        Optional<Path> htmlReport,
        VisualCompareStatus status,
        String message
) {

    public VisualCompareResult {
        if (status == null) {
            status = passed ? VisualCompareStatus.PASSED : VisualCompareStatus.FAILED;
        }
    }

    public VisualCompareResult(
            boolean passed,
            long diffPixels,
            long comparedPixels,
            double diffPercent,
            Path expectedImage,
            Optional<Path> actualImage,
            Optional<Path> diffImage,
            Optional<Path> htmlReport,
            String message
    ) {
        this(
                passed,
                diffPixels,
                comparedPixels,
                diffPercent,
                expectedImage,
                actualImage,
                diffImage,
                htmlReport,
                passed ? VisualCompareStatus.PASSED : VisualCompareStatus.FAILED,
                message
        );
    }

    public VisualCompareResult(
            boolean passed,
            long diffPixels,
            long comparedPixels,
            double diffPercent,
            Path expectedImage,
            Optional<Path> actualImage,
            Optional<Path> diffImage,
            String message
    ) {
        this(passed, diffPixels, comparedPixels, diffPercent, expectedImage, actualImage, diffImage, Optional.empty(), message);
    }

    public String failureMessage() {
        if (passed) {
            return "Visual comparison " + status + ": " + message;
        }

        return "Visual comparison failed: " + message
                + ", diffPixels=" + diffPixels
                + ", comparedPixels=" + comparedPixels
                + ", diffPercent=" + String.format("%.6f", diffPercent)
                + ", expected=" + expectedImage
                + actualImage.map(path -> ", actual=" + path).orElse("")
                + diffImage.map(path -> ", diff=" + path).orElse("")
                + htmlReport.map(path -> ", report=" + path).orElse("");
    }
}
