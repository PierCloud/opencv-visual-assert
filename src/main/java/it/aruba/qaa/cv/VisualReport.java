package it.aruba.qaa.cv;

import java.nio.file.Path;
import java.util.Optional;

record VisualReport(
        Path reportPath,
        Optional<Path> expectedPath,
        Optional<Path> actualPath,
        Optional<Path> diffPath,
        long diffPixels,
        long comparedPixels,
        double diffPercent,
        VisualCompareStatus status,
        VisualCompareOptions options,
        int effectivePixelTolerance,
        String note
) {
}
