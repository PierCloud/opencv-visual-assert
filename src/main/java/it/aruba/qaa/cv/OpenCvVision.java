package it.aruba.qaa.cv;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

public final class OpenCvVision {

    private OpenCvVision() {
    }

    public static TemplateMatchResult findTemplateInScreenshotBytes(
            byte[] screenshotBytes,
            String templateKeyOrPath,
            TemplateMatchOptions options
    ) {
        BaselineImageLoader.LoadedImage template = BaselineImageLoader.loadFromClasspathOrPath(templateKeyOrPath);
        BufferedImage screenshot = OpenCvImageComparator.normalize(
                OpenCvImageComparator.decodeImage(screenshotBytes, "screenshot")
        );
        BufferedImage templateImage = OpenCvImageComparator.normalize(
                OpenCvImageComparator.decodeImage(template.bytes(), "template image")
        );

        if (templateImage.getWidth() > screenshot.getWidth() || templateImage.getHeight() > screenshot.getHeight()) {
            return new TemplateMatchResult(
                    false,
                    0.0,
                    VisualRegion.of(0, 0, 0, 0),
                    template.displayPath(),
                    Optional.empty(),
                    "Template is larger than screenshot"
            );
        }

        Match match = findBestMatch(screenshot, templateImage);
        VisualRegion matchRegion = VisualRegion.of(
                match.x(),
                match.y(),
                templateImage.getWidth(),
                templateImage.getHeight()
        );

        Path searchPath = options.outputDirectory().resolve(options.artifactName() + "-search.png");
        if (options.writeSearchImage()) {
            writeSearchImage(screenshot, matchRegion, searchPath);
        }

        return new TemplateMatchResult(
                match.score() >= options.minScore(),
                match.score(),
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

    private static Match findBestMatch(BufferedImage screenshot, BufferedImage template) {
        int step = Math.max(1, Math.min(template.getWidth(), template.getHeight()) / 20);
        Match best = new Match(0, 0, -1.0);

        for (int y = 0; y <= screenshot.getHeight() - template.getHeight(); y += step) {
            for (int x = 0; x <= screenshot.getWidth() - template.getWidth(); x += step) {
                double score = scoreAt(screenshot, template, x, y);
                if (score > best.score()) {
                    best = new Match(x, y, score);
                }
            }
        }

        int searchRadius = Math.max(2, step);
        Match refined = best;
        for (int y = Math.max(0, best.y() - searchRadius);
             y <= Math.min(screenshot.getHeight() - template.getHeight(), best.y() + searchRadius);
             y++) {
            for (int x = Math.max(0, best.x() - searchRadius);
                 x <= Math.min(screenshot.getWidth() - template.getWidth(), best.x() + searchRadius);
                 x++) {
                double score = scoreAt(screenshot, template, x, y);
                if (score > refined.score()) {
                    refined = new Match(x, y, score);
                }
            }
        }

        return refined;
    }

    private static double scoreAt(BufferedImage screenshot, BufferedImage template, int offsetX, int offsetY) {
        long totalDelta = 0;
        int pixels = template.getWidth() * template.getHeight();
        for (int y = 0; y < template.getHeight(); y++) {
            for (int x = 0; x < template.getWidth(); x++) {
                totalDelta += Math.abs(
                        luminance(screenshot.getRGB(offsetX + x, offsetY + y))
                                - luminance(template.getRGB(x, y))
                );
            }
        }

        double averageDelta = totalDelta / (double) pixels;
        return Math.max(0.0, 1.0 - averageDelta / 255.0);
    }

    private static int luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return (int) Math.round(0.2126 * red + 0.7152 * green + 0.0722 * blue);
    }

    private static void writeSearchImage(BufferedImage screenshot, VisualRegion region, Path searchPath) {
        BufferedImage output = new BufferedImage(
                screenshot.getWidth(),
                screenshot.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.drawImage(screenshot, 0, 0, null);
            graphics.setColor(Color.RED);
            graphics.drawRect(region.x(), region.y(), Math.max(0, region.width() - 1), Math.max(0, region.height() - 1));
        } finally {
            graphics.dispose();
        }
        OpenCvImageComparator.writePngForVisionApi(output, searchPath);
    }

    private record Match(int x, int y, double score) {
    }
}
