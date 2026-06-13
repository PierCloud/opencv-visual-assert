package io.senti.testing.visual;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;

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
import static org.bytedeco.opencv.global.opencv_imgproc.LINE_8;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.rectangle;
import static org.bytedeco.opencv.global.opencv_imgproc.threshold;

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

        Path actualPath = options.outputDirectory().resolve(options.artifactName() + "-actual.png");
        Path diffPath = options.outputDirectory().resolve(options.artifactName() + "-diff.png");

        ensureOutputDirectory(options.outputDirectory());

        if (options.writeActualImage()) {
            writePng(actual, actualPath);
        }

        if (actual.cols() != expected.cols() || actual.rows() != expected.rows()) {
            return new VisualCompareResult(
                    false,
                    -1,
                    -1,
                    100.0,
                    expectedDisplayPath,
                    optionalPath(options.writeActualImage(), actualPath),
                    Optional.empty(),
                    "Image size mismatch. Expected " + expected.cols() + "x" + expected.rows()
                            + ", actual " + actual.cols() + "x" + actual.rows()
            );
        }

        Mat delta = new Mat();
        absdiff(expected, actual, delta);

        Mat gray = new Mat();
        cvtColor(delta, gray, COLOR_BGR2GRAY);

        Mat mask = new Mat();
        threshold(gray, mask, options.pixelTolerance(), 255, THRESH_BINARY);

        long ignoredPixels = applyIgnoredRegions(mask, options);
        long comparedPixels = Math.max(0, (long) mask.rows() * mask.cols() - ignoredPixels);
        long diffPixels = countNonZero(mask);
        double diffPercent = comparedPixels == 0 ? 0.0 : diffPixels * 100.0 / comparedPixels;

        if (options.writeDiffImage()) {
            writeDiffImage(actual, mask, diffPath);
        }

        boolean passed = diffPixels <= options.maxDiffPixels() || diffPercent <= options.maxDiffPercent();

        return new VisualCompareResult(
                passed,
                diffPixels,
                comparedPixels,
                diffPercent,
                expectedDisplayPath,
                optionalPath(options.writeActualImage(), actualPath),
                optionalPath(options.writeDiffImage(), diffPath),
                "Allowed maxDiffPercent=" + options.maxDiffPercent()
                        + ", maxDiffPixels=" + options.maxDiffPixels()
                        + ", pixelTolerance=" + options.pixelTolerance()
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
