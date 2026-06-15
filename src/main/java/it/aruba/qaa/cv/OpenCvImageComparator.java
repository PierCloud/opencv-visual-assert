package it.aruba.qaa.cv;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class OpenCvImageComparator {

    private OpenCvImageComparator() {
    }

    static VisualCompareResult compare(
            byte[] actualImageBytes,
            byte[] expectedImageBytes,
            Path expectedDisplayPath,
            VisualCompareOptions options
    ) {
        BufferedImage actual = normalize(decodeImage(actualImageBytes, "actual screenshot"));
        BufferedImage expected = normalize(decodeImage(expectedImageBytes, "baseline image"));

        Path expectedPath = options.outputDirectory().resolve(options.artifactName() + "-expected.png");
        Path actualPath = options.outputDirectory().resolve(options.artifactName() + "-actual.png");
        Path diffPath = options.outputDirectory().resolve(options.artifactName() + "-diff.png");
        Path reportPath = options.outputDirectory().resolve(options.artifactName() + "-report.html");

        ensureOutputDirectory(options.outputDirectory());

        String note = "";
        if (actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight()) {
            note = "Image size mismatch. Expected " + expected.getWidth() + "x" + expected.getHeight()
                    + ", actual " + actual.getWidth() + "x" + actual.getHeight()
                    + ". Actual image normalized to baseline size.";
            actual = resizeTo(actual, expected.getWidth(), expected.getHeight());
        }

        Optional<VisualRegion> compareOnlyRegion = options.compareOnlyRegion();
        if (compareOnlyRegion.isPresent()) {
            VisualRegion clippedRegion = clipRegion(compareOnlyRegion.get(), expected.getWidth(), expected.getHeight());
            expected = crop(expected, clippedRegion);
            actual = crop(actual, clippedRegion);
            note += (note.isBlank() ? "" : " ")
                    + "Compared only region x=" + clippedRegion.x()
                    + ", y=" + clippedRegion.y()
                    + ", width=" + clippedRegion.width()
                    + ", height=" + clippedRegion.height() + ".";
        }

        if (options.writeExpectedImage()) {
            writePng(expected, expectedPath);
        }

        if (options.writeActualImage()) {
            writePng(actual, actualPath);
        }

        int effectivePixelTolerance = Math.max(options.pixelTolerance(), 32);
        if (effectivePixelTolerance != options.pixelTolerance()) {
            note += (note.isBlank() ? "" : " ")
                    + "Effective pixel tolerance raised to " + effectivePixelTolerance
                    + " for perceptual comparison.";
        }

        boolean[][] mask = buildPerceptualMask(expected, actual, effectivePixelTolerance);
        long ignoredPixels = applyIgnoredRegions(mask, options);
        int minimumComponentArea = minimumSignificantComponentArea(mask);
        long renderingNoisePixels = removeSmallComponents(mask, minimumComponentArea);
        if (renderingNoisePixels > 0) {
            note += (note.isBlank() ? "" : " ")
                    + "Ignored " + renderingNoisePixels
                    + " isolated rendering-noise pixels below component area " + minimumComponentArea + ".";
        }

        long comparedPixels = Math.max(0, (long) expected.getWidth() * expected.getHeight() - ignoredPixels);
        long diffPixels = countMaskPixels(mask);
        double diffPercent = comparedPixels == 0 ? 0.0 : diffPixels * 100.0 / comparedPixels;
        VisualCompareStatus status = evaluateStatus(diffPixels, diffPercent, options);
        boolean passed = status != VisualCompareStatus.FAILED;

        if (status == VisualCompareStatus.PASSED && diffPixels > 0) {
            note += (note.isBlank() ? "" : " ")
                    + "Detected differences are safely within configured threshold.";
        } else if (status == VisualCompareStatus.WARNING) {
            note += (note.isBlank() ? "" : " ")
                    + "Detected differences are close to configured threshold.";
        }

        if (options.writeDiffImage()) {
            writeDiffImage(actual, mask, diffPath, status);
        }

        if (options.writeHtmlReport()) {
            writeHtmlReport(
                    reportPath,
                    optionalPath(options.writeExpectedImage(), expectedPath),
                    optionalPath(options.writeActualImage(), actualPath),
                    optionalPath(options.writeDiffImage(), diffPath),
                    diffPixels,
                    comparedPixels,
                    diffPercent,
                    status,
                    options,
                    effectivePixelTolerance,
                    note
            );
        }

        return new VisualCompareResult(
                passed,
                diffPixels,
                comparedPixels,
                diffPercent,
                options.writeExpectedImage() ? expectedPath : expectedDisplayPath,
                optionalPath(options.writeActualImage(), actualPath),
                optionalPath(options.writeDiffImage(), diffPath),
                optionalPath(options.writeHtmlReport(), reportPath),
                status,
                (note.isBlank() ? "" : note + " ")
                        + "Allowed maxDiffPercent=" + options.maxDiffPercent()
                        + ", maxDiffPixels=" + options.maxDiffPixels()
                        + ", warningThresholdRatio=" + options.warningThresholdRatio()
                        + ", failureThresholdMultiplier=" + options.failureThresholdMultiplier()
                        + ", pixelTolerance=" + effectivePixelTolerance
        );
    }

    private static VisualCompareStatus evaluateStatus(
            long diffPixels,
            double diffPercent,
            VisualCompareOptions options
    ) {
        if (diffPixels == 0) {
            return VisualCompareStatus.PASSED;
        }

        VisualCompareStatus status = VisualCompareStatus.FAILED;
        boolean hasThreshold = false;

        if (options.maxDiffPercent() > 0.0) {
            status = leastSevere(status, evaluateMetric(diffPercent, options.maxDiffPercent(), options));
            hasThreshold = true;
        }

        if (options.maxDiffPixels() > 0) {
            status = leastSevere(status, evaluateMetric(diffPixels, options.maxDiffPixels(), options));
            hasThreshold = true;
        }

        return hasThreshold ? status : VisualCompareStatus.FAILED;
    }

    private static VisualCompareStatus evaluateMetric(
            double value,
            double threshold,
            VisualCompareOptions options
    ) {
        if (value <= threshold * options.warningThresholdRatio()) {
            return VisualCompareStatus.PASSED;
        }

        if (value <= threshold * options.failureThresholdMultiplier()) {
            return VisualCompareStatus.WARNING;
        }

        return VisualCompareStatus.FAILED;
    }

    private static VisualCompareStatus leastSevere(VisualCompareStatus current, VisualCompareStatus candidate) {
        return severity(candidate) < severity(current) ? candidate : current;
    }

    private static int severity(VisualCompareStatus status) {
        return switch (status) {
            case PASSED -> 0;
            case WARNING -> 1;
            case FAILED -> 2;
        };
    }

    private static VisualRegion clipRegion(VisualRegion region, int imageWidth, int imageHeight) {
        int x = Math.max(0, region.x());
        int y = Math.max(0, region.y());
        int right = Math.min(imageWidth, region.x() + region.width());
        int bottom = Math.min(imageHeight, region.y() + region.height());

        if (right <= x || bottom <= y) {
            throw new VisualAssertException(
                    "compareOnlyRegion is outside image bounds: x=" + region.x()
                            + ", y=" + region.y()
                            + ", width=" + region.width()
                            + ", height=" + region.height()
                            + ", imageWidth=" + imageWidth
                            + ", imageHeight=" + imageHeight
            );
        }

        return VisualRegion.of(x, y, right - x, bottom - y);
    }

    private static BufferedImage crop(BufferedImage image, VisualRegion region) {
        BufferedImage cropped = new BufferedImage(region.width(), region.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(
                    image,
                    0,
                    0,
                    region.width(),
                    region.height(),
                    region.x(),
                    region.y(),
                    region.x() + region.width(),
                    region.y() + region.height(),
                    null
            );
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    static BufferedImage decodeImage(byte[] imageBytes, String imageName) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new VisualAssertException("Could not decode " + imageName);
            }
            return image;
        } catch (IOException e) {
            throw new VisualAssertException("Unable to decode " + imageName, e);
        }
    }

    static BufferedImage normalize(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        BufferedImage normalized = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return normalized;
    }

    private static boolean[][] buildPerceptualMask(
            BufferedImage expected,
            BufferedImage actual,
            int effectivePixelTolerance
    ) {
        int[][] expectedGray = gaussianBlur(toGray(expected));
        int[][] actualGray = gaussianBlur(toGray(actual));
        boolean[][] rawMask = thresholdAbsDiff(expectedGray, actualGray, effectivePixelTolerance);
        boolean[][] closedMask = dilate(erode(dilate(rawMask)));
        return erode(closedMask);
    }

    private static int[][] toGray(BufferedImage image) {
        int[][] gray = new int[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                gray[y][x] = luminance(image.getRGB(x, y));
            }
        }
        return gray;
    }

    private static int[][] gaussianBlur(int[][] input) {
        int rows = input.length;
        int cols = rows == 0 ? 0 : input[0].length;
        int[][] horizontal = new int[rows][cols];
        int[][] output = new int[rows][cols];
        int[] kernel = {1, 4, 6, 4, 1};

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int total = 0;
                for (int k = 0; k < kernel.length; k++) {
                    int sourceX = clamp(x + k - 2, 0, cols - 1);
                    total += input[y][sourceX] * kernel[k];
                }
                horizontal[y][x] = total / 16;
            }
        }

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int total = 0;
                for (int k = 0; k < kernel.length; k++) {
                    int sourceY = clamp(y + k - 2, 0, rows - 1);
                    total += horizontal[sourceY][x] * kernel[k];
                }
                output[y][x] = total / 16;
            }
        }

        return output;
    }

    private static boolean[][] thresholdAbsDiff(int[][] expectedGray, int[][] actualGray, int threshold) {
        int rows = expectedGray.length;
        int cols = rows == 0 ? 0 : expectedGray[0].length;
        boolean[][] mask = new boolean[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                mask[y][x] = Math.abs(expectedGray[y][x] - actualGray[y][x]) >= threshold;
            }
        }

        return mask;
    }

    private static boolean[][] dilate(boolean[][] input) {
        int rows = input.length;
        int cols = rows == 0 ? 0 : input[0].length;
        boolean[][] output = new boolean[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                boolean value = false;
                for (int dy = -1; dy <= 1 && !value; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int sourceY = y + dy;
                        int sourceX = x + dx;
                        if (sourceY >= 0 && sourceY < rows && sourceX >= 0 && sourceX < cols && input[sourceY][sourceX]) {
                            value = true;
                            break;
                        }
                    }
                }
                output[y][x] = value;
            }
        }

        return output;
    }

    private static boolean[][] erode(boolean[][] input) {
        int rows = input.length;
        int cols = rows == 0 ? 0 : input[0].length;
        boolean[][] output = new boolean[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                boolean value = true;
                for (int dy = -1; dy <= 1 && value; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int sourceY = y + dy;
                        int sourceX = x + dx;
                        if (sourceY < 0 || sourceY >= rows || sourceX < 0 || sourceX >= cols || !input[sourceY][sourceX]) {
                            value = false;
                            break;
                        }
                    }
                }
                output[y][x] = value;
            }
        }

        return output;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long applyIgnoredRegions(boolean[][] mask, VisualCompareOptions options) {
        long ignoredPixels = 0;
        int rows = mask.length;
        int cols = rows == 0 ? 0 : mask[0].length;
        for (VisualRegion region : options.ignoredRegions()) {
            int x = Math.max(0, region.x());
            int y = Math.max(0, region.y());
            int right = Math.min(cols, region.x() + region.width());
            int bottom = Math.min(rows, region.y() + region.height());

            if (right <= x || bottom <= y) {
                continue;
            }

            for (int row = y; row < bottom; row++) {
                for (int col = x; col < right; col++) {
                    mask[row][col] = false;
                }
            }
            ignoredPixels += region.clippedArea(cols, rows);
        }
        return ignoredPixels;
    }

    private static int minimumSignificantComponentArea(boolean[][] mask) {
        long pixels = (long) mask.length * (mask.length == 0 ? 0 : mask[0].length);
        return (int) Math.max(16, pixels / 120_000);
    }

    private static long removeSmallComponents(boolean[][] mask, int minimumArea) {
        int rows = mask.length;
        int cols = rows == 0 ? 0 : mask[0].length;
        boolean[][] visited = new boolean[rows][cols];
        int[] queue = new int[rows * cols];
        long removedPixels = 0;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (visited[y][x] || !mask[y][x]) {
                    continue;
                }

                int size = collectComponent(mask, visited, queue, x, y);
                if (size < minimumArea) {
                    for (int i = 0; i < size; i++) {
                        int pixel = queue[i];
                        mask[pixel / cols][pixel % cols] = false;
                    }
                    removedPixels += size;
                }
            }
        }

        return removedPixels;
    }

    private static int collectComponent(boolean[][] mask, boolean[][] visited, int[] queue, int startX, int startY) {
        int cols = mask[0].length;
        int head = 0;
        int tail = 0;
        visited[startY][startX] = true;
        queue[tail++] = startY * cols + startX;

        while (head < tail) {
            int pixel = queue[head++];
            int x = pixel % cols;
            int y = pixel / cols;

            tail = addNeighbor(mask, visited, queue, x - 1, y, tail);
            tail = addNeighbor(mask, visited, queue, x + 1, y, tail);
            tail = addNeighbor(mask, visited, queue, x, y - 1, tail);
            tail = addNeighbor(mask, visited, queue, x, y + 1, tail);
        }

        return tail;
    }

    private static int addNeighbor(boolean[][] mask, boolean[][] visited, int[] queue, int x, int y, int tail) {
        if (y < 0 || y >= mask.length || x < 0 || x >= mask[0].length) {
            return tail;
        }

        if (visited[y][x] || !mask[y][x]) {
            return tail;
        }

        visited[y][x] = true;
        queue[tail++] = y * mask[0].length + x;
        return tail;
    }

    private static long countMaskPixels(boolean[][] mask) {
        long count = 0;
        for (boolean[] row : mask) {
            for (boolean value : row) {
                if (value) {
                    count++;
                }
            }
        }
        return count;
    }

    private static BufferedImage resizeTo(BufferedImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static int luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return (int) Math.round(0.2126 * red + 0.7152 * green + 0.0722 * blue);
    }

    private static void writeDiffImage(BufferedImage actual, boolean[][] mask, Path diffPath, VisualCompareStatus status) {
        BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = diff.createGraphics();
        try {
            graphics.drawImage(actual, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        for (int y = 0; y < diff.getHeight(); y++) {
            for (int x = 0; x < diff.getWidth(); x++) {
                if (mask[y][x]) {
                    int rgb = diff.getRGB(x, y);
                    int red = status == VisualCompareStatus.FAILED
                            ? Math.max(190, (rgb >> 16) & 0xff)
                            : Math.max(180, (rgb >> 16) & 0xff);
                    int green = status == VisualCompareStatus.FAILED
                            ? ((rgb >> 8) & 0xff) / 4
                            : Math.max(140, ((rgb >> 8) & 0xff) / 2);
                    int blue = status == VisualCompareStatus.FAILED ? (rgb & 0xff) / 4 : (rgb & 0xff) / 5;
                    diff.setRGB(x, y, new Color(red, green, blue).getRGB());
                }
            }
        }

        writePng(diff, diffPath);
    }

    private static void writeHtmlReport(
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
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>Visual comparison report</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 24px; color: #1f2937; }
                        h1 { margin-bottom: 8px; }
                        .status { font-weight: 700; color: %s; }
                        .summary { display: grid; grid-template-columns: 180px 1fr; gap: 8px 16px; margin: 16px 0 24px; }
                        .summary dt { font-weight: 700; }
                        .summary dd { margin: 0; font-family: Consolas, monospace; }
                        .grid { display: grid; grid-template-columns: repeat(3, minmax(260px, 1fr)); gap: 16px; align-items: start; }
                        figure { margin: 0; border: 1px solid #d1d5db; padding: 12px; }
                        figcaption { font-weight: 700; margin-bottom: 8px; }
                        img { max-width: 100%%; display: block; border: 1px solid #e5e7eb; }
                    </style>
                </head>
                <body>
                    <h1>Visual comparison report</h1>
                    <div class="status">%s</div>
                    <dl class="summary">
                        <dt>Artifact</dt><dd>%s</dd>
                        <dt>Diff pixels</dt><dd>%d</dd>
                        <dt>Compared pixels</dt><dd>%d</dd>
                        <dt>Diff percent</dt><dd>%.6f</dd>
                        <dt>Max diff percent</dt><dd>%.6f</dd>
                        <dt>Pixel tolerance</dt><dd>%d</dd>
                        <dt>Note</dt><dd>%s</dd>
                    </dl>
                    <section class="grid">
                        %s
                        %s
                        %s
                    </section>
                </body>
                </html>
                """.formatted(
                statusColor(status),
                statusLabel(status, diffPixels),
                escape(options.artifactName()),
                diffPixels,
                comparedPixels,
                diffPercent,
                options.maxDiffPercent(),
                effectivePixelTolerance,
                escape(note),
                imageFigure("Baseline", expectedPath),
                imageFigure("Actual", actualPath),
                imageFigure(status == VisualCompareStatus.FAILED ? "Diff" : "Accepted diff", diffPath)
        );

        try {
            ensureOutputDirectory(reportPath.getParent());
            Files.writeString(reportPath, html);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to write visual report: " + reportPath, e);
        }
    }

    private static String statusColor(VisualCompareStatus status) {
        return switch (status) {
            case PASSED -> "#047857";
            case WARNING -> "#b45309";
            case FAILED -> "#b91c1c";
        };
    }

    private static String statusLabel(VisualCompareStatus status, long diffPixels) {
        return switch (status) {
            case PASSED -> diffPixels > 0 ? "PASSED - MINIMAL DIFFERENCES" : "PASSED";
            case WARNING -> "WARNING - NEAR THRESHOLD";
            case FAILED -> "FAILED";
        };
    }

    private static String imageFigure(String title, Optional<Path> path) {
        return path.map(value -> """
                <figure>
                    <figcaption>%s</figcaption>
                    <img src="%s" alt="%s">
                </figure>
                """.formatted(escape(title), escape(value.getFileName().toString()), escape(title))).orElse("");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void writePng(BufferedImage image, Path outputPath) {
        ensureOutputDirectory(outputPath.getParent());
        try {
            ImageIO.write(image, "png", outputPath.toFile());
        } catch (IOException e) {
            throw new VisualAssertException("Unable to write image: " + outputPath, e);
        }
    }

    static void writePngForVisionApi(BufferedImage image, Path outputPath) {
        writePng(image, outputPath);
    }

    static byte[] encodePngForVisionApi(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new VisualAssertException("Unable to encode image", e);
        }
    }

    private static void ensureOutputDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to create output directory: " + directory, e);
        }
    }

    private static Optional<Path> optionalPath(boolean enabled, Path path) {
        return enabled ? Optional.of(path) : Optional.empty();
    }
}
