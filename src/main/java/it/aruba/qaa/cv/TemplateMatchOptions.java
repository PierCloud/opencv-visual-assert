package it.aruba.qaa.cv;

import java.nio.file.Path;
import java.util.Objects;

public final class TemplateMatchOptions {

    private final String artifactName;
    private final Path outputDirectory;
    private final double minScore;
    private final boolean writeSearchImage;

    private TemplateMatchOptions(Builder builder) {
        this.artifactName = builder.artifactName;
        this.outputDirectory = builder.outputDirectory;
        this.minScore = builder.minScore;
        this.writeSearchImage = builder.writeSearchImage;
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

    public double minScore() {
        return minScore;
    }

    public boolean writeSearchImage() {
        return writeSearchImage;
    }

    public static final class Builder {
        private String artifactName = "template-match";
        private Path outputDirectory = Path.of("target", "visual-assert");
        private double minScore = 0.90;
        private boolean writeSearchImage = true;

        private Builder() {
        }

        public Builder artifactName(String artifactName) {
            if (artifactName == null || artifactName.isBlank()) {
                throw new IllegalArgumentException("artifactName must not be blank");
            }
            this.artifactName = artifactName;
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            return this;
        }

        public Builder minScore(double minScore) {
            if (minScore < 0.0 || minScore > 1.0) {
                throw new IllegalArgumentException("minScore must be between 0 and 1");
            }
            this.minScore = minScore;
            return this;
        }

        public Builder writeSearchImage(boolean writeSearchImage) {
            this.writeSearchImage = writeSearchImage;
            return this;
        }

        public TemplateMatchOptions build() {
            return new TemplateMatchOptions(this);
        }
    }
}
