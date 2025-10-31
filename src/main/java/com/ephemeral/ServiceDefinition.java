package com.ephemeral;

import java.util.Collections;
import java.util.Map;
import java.util.List;

/**
 * Defines a single service (container) to be run.
 * This is an immutable configuration object.
 *
 * Per Roadmap Phase 1: (image, env, exposed port, readiness check)
 * We will add readiness check in Phase 2.
 *
 * @param imageName The Docker image to pull and run (e.g., "postgres:15").
 * @param exposedPorts The list of container ports to expose to the host.
 * @param environmentVariables A map of environment variables to set inside the container.
 */
public record ServiceDefinition(
        String imageName,
        List<Integer> exposedPorts,
        Map<String, String> environmentVariables
) {

    /**
     * Convenience constructor for a service with a single exposed port and no env vars.
     * @param imageName The Docker image to pull.
     * @param exposedPort The single container port to expose.
     */
    public ServiceDefinition(String imageName, int exposedPort) {
        this(imageName, List.of(exposedPort), Collections.emptyMap());
    }

    /**
     * Convenience constructor for a service with no exposed ports and no env vars.
     * @param imageName The Docker image to pull.
     */
    public ServiceDefinition(String imageName) {
        this(imageName, Collections.emptyList(), Collections.emptyMap());
    }
}
