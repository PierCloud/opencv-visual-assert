package io.senti.testing.visual;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import java.nio.file.Path;
import java.util.Optional;

import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;
import static org.bytedeco.opencv.global.opencv_imgproc.TM_CCOEFF_NORMED;
import static org.bytedeco.opencv.global.opencv_imgproc.matchTemplate;

public final class OpenCvVision {

    private OpenCvVision() {
    }

    public static TemplateMatchResult findTemplateInScreenshotBytes(
            byte[] screenshotBytes,
            String templateKeyOrPath,
            TemplateMatchOptions options
    ) {
        BaselineImageLoader.LoadedImage template = BaselineImageLoader.loadFromClasspathOrPath(templateKeyOrPath);
        Mat screenshot = OpenCvImageComparator.normalizeToBgr(
                OpenCvImageComparator.decodeImage(screenshotBytes, "screenshot")
        );
        Mat templateImage = OpenCvImageComparator.normalizeToBgr(
                OpenCvImageComparator.decodeImage(template.bytes(), "template image")
        );

        if (templateImage.cols() > screenshot.cols() || templateImage.rows() > screenshot.rows()) {
            return new TemplateMatchResult(
                    false,
                    0.0,
                    VisualRegion.of(0, 0, 0, 0),
                    template.displayPath(),
                    Optional.empty(),
                    "Template is larger than screenshot"
            );
        }

        Mat result = new Mat();
        matchTemplate(screenshot, templateImage, result, TM_CCOEFF_NORMED);

        DoublePointer minValue = new DoublePointer(1);
        DoublePointer maxValue = new DoublePointer(1);
        Point minLocation = new Point();
        Point maxLocation = new Point();
        minMaxLoc(result, minValue, maxValue, minLocation, maxLocation, null);

        double score = maxValue.get();
        VisualRegion matchRegion = VisualRegion.of(
                maxLocation.x(),
                maxLocation.y(),
                templateImage.cols(),
                templateImage.rows()
        );

        Path searchPath = options.outputDirectory().resolve(options.artifactName() + "-search.png");
        if (options.writeSearchImage()) {
            writeSearchImage(screenshot, searchPath);
        }

        return new TemplateMatchResult(
                score >= options.minScore(),
                score,
                matchRegion,
                template.displayPath(),
                options.writeSearchImage() ? Optional.of(searchPath) : Optional.empty(),
                "Required minScore=" + options.minScore()
        );
    }

    public static VisualCompareResult compareScreenshotBytes(
            byte[] actualScreenshotBytes,
            String baselineKeyOrPath,
            VisualCompareOptions options
    ) {
        return VisualAssert.compareScreenshotBytes(actualScreenshotBytes, baselineKeyOrPath, options);
    }

    private static void writeSearchImage(Mat screenshot, Path searchPath) {
        OpenCvImageComparator.writePngForVisionApi(screenshot, searchPath);
    }
}
