package it.aruba.qaa.cv;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

final class BaselineImageLoader {

    private BaselineImageLoader() {
    }

    static LoadedImage loadFromClasspathOrPath(String baselineKeyOrPath) {
        for (String candidate : baselineCandidates(baselineKeyOrPath)) {
            Path directPath = Path.of(candidate);
            if (Files.isRegularFile(directPath)) {
                return readFile(directPath);
            }

            LoadedImage resource = readResource(candidate);
            if (resource != null) {
                return resource;
            }
        }

        throw new VisualAssertException(
                "Baseline image not found for key or path: " + baselineKeyOrPath
                        + ". Baseline folder is test/cv_img."
        );
    }

    private static LoadedImage readFile(Path directPath) {
        try {
            return new LoadedImage(Files.readAllBytes(directPath), directPath);
        } catch (IOException e) {
            throw new VisualAssertException("Unable to read baseline image: " + directPath, e);
        }
    }

    private static LoadedImage readResource(String resourcePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }

            return new LoadedImage(stream.readAllBytes(), Path.of(resourcePath));
        } catch (IOException e) {
            throw new VisualAssertException("Unable to read baseline resource: " + resourcePath, e);
        }
    }

    private static List<String> baselineCandidates(String baselineKeyOrPath) {
        String normalized = baselineKeyOrPath.replace('\\', '/');
        List<String> names = fileNameCandidates(normalized);
        List<String> candidates = new ArrayList<>();

        for (String name : names) {
            candidates.add("test/cv_img/" + name);
        }

        return candidates;
    }

    private static List<String> fileNameCandidates(String key) {
        if (hasImageExtension(key)) {
            return List.of(key);
        }

        return List.of(key + ".png", key + ".jpg", key + ".jpeg");
    }

    private static boolean hasImageExtension(String key) {
        String value = key.toLowerCase();
        return value.endsWith(".png") || value.endsWith(".jpg") || value.endsWith(".jpeg");
    }

    record LoadedImage(byte[] bytes, Path displayPath) {
    }
}
