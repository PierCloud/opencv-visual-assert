package it.aruba.qaa.cv;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class VisualCompareOptions {

    private final String artifactName;
    private final Path outputDirectory;
    private final double maxDiffPercent;
    private final long maxDiffPixels;
    private final int pixelTolerance;
    private final boolean writeActualImage;
    private final boolean writeDiffImage;
    private final boolean writeExpectedImage;
    private final boolean writeHtmlReport;
    private final List<VisualRegion> ignoredRegions;

    private VisualCompareOptions(Builder builder) {
        this.artifactName = builder.artifactName;
        this.outputDirectory = builder.outputDirectory;
        this.maxDiffPercent = builder.maxDiffPercent;
        this.maxDiffPixels = builder.maxDiffPixels;
        this.pixelTolerance = builder.pixelTolerance;
        this.writeActualImage = builder.writeActualImage;
        this.writeDiffImage = builder.writeDiffImage;
        this.writeExpectedImage = builder.writeExpectedImage;
        this.writeHtmlReport = builder.writeHtmlReport;
        this.ignoredRegions = Collections.unmodifiableList(new ArrayList<>(builder.ignoredRegions));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String artifactName() {
        return artifactName;
    }

    public Path outputDirectory() {
        return outputDirectory;
    }

    public double maxDiffPercent() {
        return maxDiffPercent;
    }

    public long maxDiffPixels() {
        return maxDiffPixels;
    }

    public int pixelTolerance() {
        return pixelTolerance;
    }

    public boolean writeActualImage() {
        return writeActualImage;
    }

    public boolean writeDiffImage() {
        return writeDiffImage;
    }

    public boolean writeExpectedImage() {
        return writeExpectedImage;
    }

    public boolean writeHtmlReport() {
        return writeHtmlReport;
    }

    public List<VisualRegion> ignoredRegions() {
        return ignoredRegions;
    }

    public static final class Builder {
        private String artifactName = "visual-comparison";
        private Path outputDirectory = Path.of("target", "visual-assert");
        private double maxDiffPercent = 0.0;
        private long maxDiffPixels = 0;
        private int pixelTolerance = 0;
        private boolean writeActualImage = true;
        private boolean writeDiffImage = true;
        private boolean writeExpectedImage = true;
        private boolean writeHtmlReport = true;
        private final List<VisualRegion> ignoredRegions = new ArrayList<>();

        private Builder() {
        }

        public Builder artifactName(String artifactName) {
            this.artifactName = requireText(artifactName, "artifactName");
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            return this;
        }

        public Builder maxDiffPercent(double maxDiffPercent) {
            if (maxDiffPercent < 0.0 || maxDiffPercent > 100.0) {
                throw new IllegalArgumentException("maxDiffPercent must be between 0 and 100");
            }
            this.maxDiffPercent = maxDiffPercent;
            return this;
        }

        public Builder maxDiffPixels(long maxDiffPixels) {
            if (maxDiffPixels < 0) {
                throw new IllegalArgumentException("maxDiffPixels must be >= 0");
            }
            this.maxDiffPixels = maxDiffPixels;
            return this;
        }

        public Builder pixelTolerance(int pixelTolerance) {
            if (pixelTolerance < 0 || pixelTolerance > 255) {
                throw new IllegalArgumentException("pixelTolerance must be between 0 and 255");
            }
            this.pixelTolerance = pixelTolerance;
            return this;
        }

        public Builder writeActualImage(boolean writeActualImage) {
            this.writeActualImage = writeActualImage;
            return this;
        }

        public Builder writeDiffImage(boolean writeDiffImage) {
            this.writeDiffImage = writeDiffImage;
            return this;
        }

        public Builder writeExpectedImage(boolean writeExpectedImage) {
            this.writeExpectedImage = writeExpectedImage;
            return this;
        }

        public Builder writeHtmlReport(boolean writeHtmlReport) {
            this.writeHtmlReport = writeHtmlReport;
            return this;
        }

        public Builder ignoreRegion(VisualRegion ignoredRegion) {
            this.ignoredRegions.add(Objects.requireNonNull(ignoredRegion, "ignoredRegion"));
            return this;
        }

        public Builder ignoreRegions(List<VisualRegion> ignoredRegions) {
            this.ignoredRegions.addAll(Objects.requireNonNull(ignoredRegions, "ignoredRegions"));
            return this;
        }

        public VisualCompareOptions build() {
            return new VisualCompareOptions(this);
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }
}
