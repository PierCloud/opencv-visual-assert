package it.aruba.qaa.cv;

import java.util.Objects;

public final class VisualAssert {

    private VisualAssert() {
    }

    public static VisualCompareResult compare(
            byte[] actualImageBytes,
            String baselineKeyOrPath,
            int pixelTolerance,
            double maxDiffPercent
    ) {
        String artifactName = ImageKey.artifactName(baselineKeyOrPath);
        return compare(
                actualImageBytes,
                baselineKeyOrPath,
                VisualCompareOptions.builder()
                        .artifactName(artifactName)
                        .pixelTolerance(pixelTolerance)
                        .maxDiffPercent(maxDiffPercent)
                        .build()
        );
    }

    public static VisualCompareResult compare(
            byte[] actualImageBytes,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        Objects.requireNonNull(actualImageBytes, "actualImageBytes");
        Objects.requireNonNull(baselineKeyOrPath, "baselineKeyOrPath");
        Objects.requireNonNull(options, "options");

        BaselineImageLoader.LoadedImage baseline = BaselineImageLoader.loadFromClasspathOrPath(baselineKeyOrPath);
        return VisualImageComparator.compare(
                actualImageBytes,
                baseline.bytes(),
                baseline.displayPath(),
                options
        );
    }
}
