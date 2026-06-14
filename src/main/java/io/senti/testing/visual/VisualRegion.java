package io.senti.testing.visual;

public record VisualRegion(int x, int y, int width, int height) {

    public static VisualRegion of(int x, int y, int width, int height) {
        return new VisualRegion(x, y, width, height);
    }

    int clippedArea(int imageWidth, int imageHeight) {
        int clippedX = Math.max(0, x);
        int clippedY = Math.max(0, y);
        int clippedRight = Math.min(imageWidth, x + width);
        int clippedBottom = Math.min(imageHeight, y + height);

        if (clippedRight <= clippedX || clippedBottom <= clippedY) {
            return 0;
        }

        return (clippedRight - clippedX) * (clippedBottom - clippedY);
    }
}
