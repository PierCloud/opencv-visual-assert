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
        return compare(
                actualImageBytes,
                baselineKeyOrPath,
                optionsFor(baselineKeyOrPath, pixelTolerance, maxDiffPercent, null)
        );
    }

    public static VisualCompareResult compare(
            byte[] actualImageBytes,
            String baselineKeyOrPath,
            int pixelTolerance,
            double maxDiffPercent,
            VisualRegion compareOnlyRegion
    ) {
        return compare(
                actualImageBytes,
                baselineKeyOrPath,
                optionsFor(baselineKeyOrPath, pixelTolerance, maxDiffPercent, compareOnlyRegion)
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

    private static VisualCompareOptions optionsFor(
            String baselineKeyOrPath,
            int pixelTolerance,
            double maxDiffPercent,
            VisualRegion compareOnlyRegion
    ) {
        VisualCompareOptions.Builder builder = VisualCompareOptions.builder()
                .artifactName(ImageKey.artifactName(baselineKeyOrPath))
                .pixelTolerance(pixelTolerance)
                .maxDiffPercent(maxDiffPercent);

        if (compareOnlyRegion != null) {
            builder.compareOnlyRegion(compareOnlyRegion);
        }

        return builder.build();
    }
}
