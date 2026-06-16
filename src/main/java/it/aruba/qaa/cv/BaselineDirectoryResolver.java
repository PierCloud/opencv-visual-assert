package it.aruba.qaa.cv;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class BaselineDirectoryResolver {

    private static final Path MAVEN_TEST_RESOURCES = Path.of("src", "test", "resources");
    private static final Path BASELINE_RESOURCE_DIRECTORY = Path.of("test", "cv_img");

    private BaselineDirectoryResolver() {
    }

    static Path resolve() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null) {
            Path directTestResources = cursor.resolve(MAVEN_TEST_RESOURCES);
            if (Files.isDirectory(directTestResources)) {
                return directTestResources.resolve(BASELINE_RESOURCE_DIRECTORY);
            }

            Path childTestResources = findChildTestResources(cursor);
            if (childTestResources != null) {
                return childTestResources.resolve(BASELINE_RESOURCE_DIRECTORY);
            }
            cursor = cursor.getParent();
        }

        return current.resolve(MAVEN_TEST_RESOURCES).resolve(BASELINE_RESOURCE_DIRECTORY);
    }

    private static Path findChildTestResources(Path directory) {
        if (!Files.isDirectory(directory)) {
            return null;
        }

        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (!Files.isDirectory(child)) {
                    continue;
                }

                Path testResources = child.resolve(MAVEN_TEST_RESOURCES);
                if (Files.isDirectory(testResources)) {
                    return testResources;
                }
            }
        } catch (IOException e) {
            return null;
        }

        return null;
    }
}
