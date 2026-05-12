package com.group7.backend.service.ranking.graph;

import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

/**
 * Shared Neo4j 5 + GDS Community image used by every integration test that
 * needs the {@code gds.*} procedure surface. The image is built from
 * {@code backend/docker/neo4j-gds/Dockerfile} the first time any test class
 * touches {@link #IMAGE} in a JVM, and Testcontainers' content-hash cache
 * (combined with the fixed tag below + {@code deleteOnExit=false}) ensures
 * later test classes in the same Maven run resolve to the same image
 * without rebuilding — important because the apk/curl fetch inside the
 * builder stage is the one network step we can't guarantee on first try.
 *
 * <p>Path is relative to Maven's working directory ({@code backend/}).
 */
final class Neo4jGdsTestImage {

    private static final ImageFromDockerfile IMAGE_FROM_DOCKERFILE =
            new ImageFromDockerfile("group7-neo4j-gds-test", false)
                    .withDockerfile(Path.of("docker/neo4j-gds/Dockerfile"));

    /** Resolved image name (triggers the build on first reference). */
    static final DockerImageName IMAGE = DockerImageName
            .parse(IMAGE_FROM_DOCKERFILE.get())
            .asCompatibleSubstituteFor("neo4j");

    private Neo4jGdsTestImage() {
    }
}
