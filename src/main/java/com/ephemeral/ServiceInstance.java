package com.ephemeral;

import java.util.Map;

/**
 * Represents a service that is currently running.
 * This is an immutable runtime information object.
 *
 * @param containerId The ID of the running Docker container.
 * @param definition The original definition used to start this service.
 * @param mappedPorts A map of [Container Port] -> [Host Port]
 */
public record ServiceInstance(
        String containerId,
        ServiceDefinition definition,
        Map<Integer, Integer> mappedPorts
) {

    /**
     * Gets the dynamically mapped host port for a given container port.
     * @param containerPort The internal port of the container (e.g., 5432 for Postgres).
     * @return The corresponding port on the host machine (e.g., 49153).
     * @throws IllegalArgumentException if the container port was not exposed.
     */
    public int getMappedPort(int containerPort) {
        if (!mappedPorts.containsKey(containerPort)) {
            throw new IllegalArgumentException(
                    "Port " + containerPort + " is not mapped for container " + containerId
            );
        }
        return mappedPorts.get(containerPort);
    }
}
