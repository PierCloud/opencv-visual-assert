package it.aruba.qaa.cv;

import java.util.Objects;

public final class VisualAssert {

    private VisualAssert() {
    }

    public static VisualCompareResult compare(
            byte[] actualImageBytes,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        Objects.requireNonNull(actualImageBytes, "actualImageBytes");
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
