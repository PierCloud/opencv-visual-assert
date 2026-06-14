package io.senti.testing.visual;

import java.nio.file.Path;
import java.util.Optional;

public record TemplateMatchResult(
        boolean found,
        double score,
        VisualRegion matchRegion,
        Path templateImage,
        Optional<Path> searchImage,
        String message
) {

    public String failureMessage() {
        if (found) {
            return "Template match passed: " + message;
        }

        return "Template match failed: " + message
                + ", score=" + String.format("%.6f", score)
                + ", template=" + templateImage
                + searchImage.map(path -> ", searchImage=" + path).orElse("");
    }
}
