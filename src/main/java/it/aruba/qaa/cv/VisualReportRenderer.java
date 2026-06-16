package it.aruba.qaa.cv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

final class VisualReportRenderer {

    private static final String TEMPLATE_PATH = "/it/aruba/qaa/cv/visual-report-template.html";

    private VisualReportRenderer() {
    }

    static void write(VisualReport report) {
        String html = render(report);
        try {
            Files.createDirectories(report.reportPath().getParent());
            Files.writeString(report.reportPath(), html);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to write visual report: " + report.reportPath(), e);
        }
    }

    private static String render(VisualReport report) {
        String html = loadTemplate();
        Map<String, String> values = Map.of(
                "statusColor", statusColor(report.status()),
                "statusLabel", statusLabel(report.status(), report.diffPixels()),
                "artifactName", escape(report.options().artifactName()),
                "diffPixels", Long.toString(report.diffPixels()),
                "comparedPixels", Long.toString(report.comparedPixels()),
                "diffPercent", String.format("%.6f", report.diffPercent()),
                "maxDiffPercent", String.format("%.6f", report.options().maxDiffPercent()),
                "pixelTolerance", Integer.toString(report.effectivePixelTolerance()),
                "note", escape(report.note()),
                "figures", figures(report)
        );

        for (Map.Entry<String, String> entry : values.entrySet()) {
            html = html.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return html;
    }

    private static String loadTemplate() {
        try (InputStream input = VisualReportRenderer.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (input == null) {
                throw new VisualAssertException("Visual report template not found: " + TEMPLATE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to read visual report template", e);
        }
    }

    private static String figures(VisualReport report) {
        return imageFigure("Baseline", report.expectedPath())
                + imageFigure("Actual", report.actualPath())
                + imageFigure(report.status() == VisualCompareStatus.FAILED ? "Diff" : "Accepted diff", report.diffPath());
    }

    private static String imageFigure(String title, Optional<Path> path) {
        return path.map(value -> """
                <figure>
                    <figcaption>%s</figcaption>
                    <img src="%s" alt="%s">
                </figure>
                """.formatted(escape(title), escape(value.getFileName().toString()), escape(title))).orElse("");
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
}
