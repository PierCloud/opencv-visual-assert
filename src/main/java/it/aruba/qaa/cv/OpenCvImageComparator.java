package it.aruba.qaa.cv;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_core.absdiff;
import static org.bytedeco.opencv.global.opencv_core.countNonZero;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_UNCHANGED;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.LINE_8;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;
import static org.bytedeco.opencv.global.opencv_imgproc.rectangle;

final class OpenCvImageComparator {

    private OpenCvImageComparator() {
    }

    static VisualCompareResult compare(
            byte[] actualImageBytes,
            byte[] expectedImageBytes,
            Path expectedDisplayPath,
            VisualCompareOptions options
    ) {
        Mat actual = normalizeToBgr(decodeImage(actualImageBytes, "actual screenshot"));
        Mat expected = normalizeToBgr(decodeImage(expectedImageBytes, "baseline image"));

        Path expectedPath = options.outputDirectory().resolve(options.artifactName() + "-expected.png");
        Path actualPath = options.outputDirectory().resolve(options.artifactName() + "-actual.png");
        Path diffPath = options.outputDirectory().resolve(options.artifactName() + "-diff.png");
        Path reportPath = options.outputDirectory().resolve(options.artifactName() + "-report.html");

        ensureOutputDirectory(options.outputDirectory());

        if (options.writeExpectedImage()) {
            writePng(expected, expectedPath);
        }

        String note = "";
        if (actual.cols() != expected.cols() || actual.rows() != expected.rows()) {
            note = "Image size mismatch. Expected " + expected.cols() + "x" + expected.rows()
                    + ", actual " + actual.cols() + "x" + actual.rows()
                    + ". Diff uses the actual image normalized to baseline size.";
            actual = resizeTo(actual, expected.cols(), expected.rows());
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

        Mat expectedGray = toGray(blurForDiff(expected));
        Mat actualGray = toGray(blurForDiff(actual));
        Mat grayDelta = new Mat();
        absdiff(expectedGray, actualGray, grayDelta);

        Mat mask = buildPerceptualMask(expectedGray, actualGray, grayDelta, effectivePixelTolerance);

        long ignoredPixels = applyIgnoredRegions(mask, options);
        int minimumComponentArea = minimumSignificantComponentArea(mask);
        long renderingNoisePixels = removeSmallComponents(mask, minimumComponentArea);
        if (renderingNoisePixels > 0) {
            note += (note.isBlank() ? "" : " ")
                    + "Ignored " + renderingNoisePixels
                    + " isolated rendering-noise pixels below component area " + minimumComponentArea + ".";
        }

        long comparedPixels = Math.max(0, (long) mask.rows() * mask.cols() - ignoredPixels);
        long diffPixels = countNonZero(mask);
        double diffPercent = comparedPixels == 0 ? 0.0 : diffPixels * 100.0 / comparedPixels;

        if (options.writeDiffImage()) {
            writeDiffImage(actual, mask, diffPath);
        }

        boolean passed = diffPixels <= options.maxDiffPixels() || diffPercent <= options.maxDiffPercent();
        if (options.writeHtmlReport()) {
            writeHtmlReport(
                    reportPath,
                    optionalPath(options.writeExpectedImage(), expectedPath),
                    optionalPath(options.writeActualImage(), actualPath),
                    optionalPath(options.writeDiffImage(), diffPath),
                    diffPixels,
                    comparedPixels,
                    diffPercent,
                    passed,
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
                (note.isBlank() ? "" : note + " ")
                        + "Allowed maxDiffPercent=" + options.maxDiffPercent()
                        + ", maxDiffPixels=" + options.maxDiffPixels()
                        + ", pixelTolerance=" + effectivePixelTolerance
        );
    }

    static Mat decodeImage(byte[] imageBytes, String imageName) {
        BytePointer pointer = new BytePointer(imageBytes);
        Mat buffer = new Mat(1, imageBytes.length, CV_8UC1, pointer);
        Mat image = imdecode(buffer, IMREAD_UNCHANGED);
        if (image == null || image.empty()) {
            throw new VisualAssertException("OpenCV could not decode " + imageName);
        }
        return image;
    }

    static Mat normalizeToBgr(Mat image) {
        Mat normalized = new Mat();
        if (image.channels() == 4) {
            cvtColor(image, normalized, COLOR_BGRA2BGR);
            return normalized;
        }
        if (image.channels() == 1) {
            cvtColor(image, normalized, COLOR_GRAY2BGR);
            return normalized;
        }
        return image;
    }

    private static long applyIgnoredRegions(Mat mask, VisualCompareOptions options) {
        long ignoredPixels = 0;
        for (VisualRegion region : options.ignoredRegions()) {
            int x = Math.max(0, region.x());
            int y = Math.max(0, region.y());
            int right = Math.min(mask.cols(), region.x() + region.width());
            int bottom = Math.min(mask.rows(), region.y() + region.height());

            if (right <= x || bottom <= y) {
                continue;
            }

            rectangle(mask, new Rect(x, y, right - x, bottom - y), new Scalar(0.0), -1, LINE_8, 0);
            ignoredPixels += region.clippedArea(mask.cols(), mask.rows());
        }
        return ignoredPixels;
    }

    private static int minimumSignificantComponentArea(Mat mask) {
        long pixels = (long) mask.rows() * mask.cols();
        return (int) Math.max(16, pixels / 120_000);
    }

    private static long removeSmallComponents(Mat mask, int minimumArea) {
        int rows = mask.rows();
        int cols = mask.cols();
        boolean[] visited = new boolean[rows * cols];
        int[] queue = new int[rows * cols];
        long removedPixels = 0;

        UByteIndexer indexer = mask.createIndexer();
        try {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    int start = y * cols + x;
                    if (visited[start] || indexer.get(y, x) == 0) {
                        continue;
                    }

                    int size = collectComponent(indexer, visited, queue, rows, cols, x, y);
                    if (size < minimumArea) {
                        for (int i = 0; i < size; i++) {
                            int pixel = queue[i];
                            indexer.put(pixel / cols, pixel % cols, 0);
                        }
                        removedPixels += size;
                    }
                }
            }
        } finally {
            indexer.release();
        }

        return removedPixels;
    }

    private static int collectComponent(
            UByteIndexer indexer,
            boolean[] visited,
            int[] queue,
            int rows,
            int cols,
            int startX,
            int startY
    ) {
        int head = 0;
        int tail = 0;
        int start = startY * cols + startX;
        visited[start] = true;
        queue[tail++] = start;

        while (head < tail) {
            int pixel = queue[head++];
            int x = pixel % cols;
            int y = pixel / cols;

            tail = addNeighbor(indexer, visited, queue, rows, cols, x - 1, y, tail);
            tail = addNeighbor(indexer, visited, queue, rows, cols, x + 1, y, tail);
            tail = addNeighbor(indexer, visited, queue, rows, cols, x, y - 1, tail);
            tail = addNeighbor(indexer, visited, queue, rows, cols, x, y + 1, tail);
        }

        return tail;
    }

    private static int addNeighbor(
            UByteIndexer indexer,
            boolean[] visited,
            int[] queue,
            int rows,
            int cols,
            int x,
            int y,
            int tail
    ) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) {
            return tail;
        }

        int position = y * cols + x;
        if (visited[position] || indexer.get(y, x) == 0) {
            return tail;
        }

        visited[position] = true;
        queue[tail++] = position;
        return tail;
    }

    private static Mat buildPerceptualMask(Mat expectedGray, Mat actualGray, Mat grayDelta, int effectivePixelTolerance) {
        int rows = grayDelta.rows();
        int cols = grayDelta.cols();
        int blockSize = Math.max(24, Math.min(64, Math.min(rows, cols) / 35));
        Mat mask = new Mat(rows, cols, CV_8UC1, new Scalar(0.0));

        UByteIndexer expectedIndexer = expectedGray.createIndexer();
        UByteIndexer actualIndexer = actualGray.createIndexer();
        UByteIndexer deltaIndexer = grayDelta.createIndexer();
        UByteIndexer maskIndexer = mask.createIndexer();
        try {
            for (int y = 0; y < rows; y += blockSize) {
                for (int x = 0; x < cols; x += blockSize) {
                    int width = Math.min(blockSize, cols - x);
                    int height = Math.min(blockSize, rows - y);
                    if (isSignificantBlock(
                            expectedIndexer,
                            actualIndexer,
                            deltaIndexer,
                            x,
                            y,
                            width,
                            height,
                            effectivePixelTolerance
                    )) {
                        fillBlock(maskIndexer, x, y, width, height);
                    }
                }
            }
        } finally {
            expectedIndexer.release();
            actualIndexer.release();
            deltaIndexer.release();
            maskIndexer.release();
        }

        return mask;
    }

    private static boolean isSignificantBlock(
            UByteIndexer expectedIndexer,
            UByteIndexer actualIndexer,
            UByteIndexer deltaIndexer,
            int startX,
            int startY,
            int width,
            int height,
            int effectivePixelTolerance
    ) {
        double expectedTotal = 0.0;
        double actualTotal = 0.0;
        long totalDelta = 0;
        int strongPixels = 0;
        int pixels = width * height;

        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                expectedTotal += expectedIndexer.get(y, x);
                actualTotal += actualIndexer.get(y, x);
                int value = deltaIndexer.get(y, x);
                totalDelta += value;
                if (value >= effectivePixelTolerance) {
                    strongPixels++;
                }
            }
        }

        double expectedMean = expectedTotal / pixels;
        double actualMean = actualTotal / pixels;
        double expectedVariance = 0.0;
        double actualVariance = 0.0;
        double covariance = 0.0;

        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                double expected = expectedIndexer.get(y, x) - expectedMean;
                double actual = actualIndexer.get(y, x) - actualMean;
                expectedVariance += expected * expected;
                actualVariance += actual * actual;
                covariance += expected * actual;
            }
        }

        expectedVariance /= pixels;
        actualVariance /= pixels;
        covariance /= pixels;

        double c1 = 6.5025;
        double c2 = 58.5225;
        double ssim = ((2.0 * expectedMean * actualMean + c1) * (2.0 * covariance + c2))
                / ((expectedMean * expectedMean + actualMean * actualMean + c1)
                * (expectedVariance + actualVariance + c2));
        double averageDelta = totalDelta / (double) pixels;
        double strongRatio = strongPixels / (double) pixels;
        return ssim <= 0.72 && averageDelta >= 10.0 && strongRatio >= 0.06;
    }

    private static void fillBlock(UByteIndexer maskIndexer, int startX, int startY, int width, int height) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                maskIndexer.put(y, x, 255);
            }
        }
    }

    private static Mat resizeTo(Mat image, int width, int height) {
        Mat resized = new Mat();
        resize(image, resized, new Size(width, height));
        return resized;
    }

    private static Mat blurForDiff(Mat image) {
        Mat blurred = new Mat();
        GaussianBlur(image, blurred, new Size(5, 5), 0.0);
        return blurred;
    }

    private static Mat toGray(Mat image) {
        Mat gray = new Mat();
        cvtColor(image, gray, COLOR_BGR2GRAY);
        return gray;
    }

    private static void writeDiffImage(Mat actual, Mat mask, Path diffPath) {
        Mat diff = actual.clone();
        UByteIndexer maskIndexer = mask.createIndexer();
        UByteIndexer diffIndexer = diff.createIndexer();
        try {
            for (int y = 0; y < mask.rows(); y++) {
                for (int x = 0; x < mask.cols(); x++) {
                    if (maskIndexer.get(y, x) > 0) {
                        diffIndexer.put(y, x, 0, 0);
                        diffIndexer.put(y, x, 1, 0);
                        diffIndexer.put(y, x, 2, 255);
                    }
                }
            }
        } finally {
            maskIndexer.release();
            diffIndexer.release();
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
            boolean passed,
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
                passed ? "#047857" : "#b91c1c",
                passed ? "PASSED" : "FAILED",
                escape(options.artifactName()),
                diffPixels,
                comparedPixels,
                diffPercent,
                options.maxDiffPercent(),
                effectivePixelTolerance,
                escape(note),
                imageFigure("Baseline", expectedPath),
                imageFigure("Actual", actualPath),
                imageFigure("Diff", diffPath)
        );

        try {
            ensureOutputDirectory(reportPath.getParent());
            Files.writeString(reportPath, html);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to write visual report: " + reportPath, e);
        }
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

    private static void writePng(Mat image, Path outputPath) {
        ensureOutputDirectory(outputPath.getParent());
        BytePointer encoded = new BytePointer();
        if (!imencode(".png", image, encoded)) {
            throw new VisualAssertException("OpenCV could not encode image: " + outputPath);
        }

        byte[] bytes = new byte[(int) encoded.limit()];
        encoded.get(bytes);
        try {
            Files.write(outputPath, bytes);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to write image: " + outputPath, e);
        }
    }

    static void writePngForVisionApi(Mat image, Path outputPath) {
        writePng(image, outputPath);
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
