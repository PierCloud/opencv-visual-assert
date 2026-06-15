package it.aruba.qaa.cv;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class VisualAssert {

    private VisualAssert() {
    }

    public static VisualCompareResult compareScreenshotBytes(
            byte[] actualScreenshotBytes,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        Objects.requireNonNull(actualScreenshotBytes, "actualScreenshotBytes");
        Objects.requireNonNull(options, "options");

        BaselineImageLoader.LoadedImage baseline = BaselineImageLoader.loadFromClasspathOrPath(baselineKeyOrPath);
        return OpenCvImageComparator.compare(
                actualScreenshotBytes,
                baseline.bytes(),
                baseline.displayPath(),
                options
        );
    }

    public static VisualCompareResult compareActualPath(
            Path actualImage,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        Objects.requireNonNull(actualImage, "actualImage");
        try {
            return compareScreenshotBytes(Files.readAllBytes(actualImage), baselineKeyOrPath, options);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to read actual image: " + actualImage, e);
        }
    }

    public static VisualCompareResult compareActualFile(
            File actualImage,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        Objects.requireNonNull(actualImage, "actualImage");
        return compareActualPath(actualImage.toPath(), baselineKeyOrPath, options);
    }

    public static VisualCompareResult compareBufferedImage(
            BufferedImage actualImage,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        Objects.requireNonNull(actualImage, "actualImage");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(actualImage, "png", output);
            return compareScreenshotBytes(output.toByteArray(), baselineKeyOrPath, options);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to encode actual BufferedImage", e);
        }
    }
}
