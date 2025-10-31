package com.ephemeral;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A thin wrapper around the docker-java client to simplify starting
 * and stopping containers as defined by the roadmap.
 */
public class DockerClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(DockerClientWrapper.class);
    private final DockerClient dockerClient;

    public DockerClientWrapper() {
        // Connects to the Docker daemon using default settings
        // (e.g., DOCKER_HOST env var or local socket)
        this.dockerClient = DockerClientBuilder.getInstance().build();
        log.info("DockerClient initialized. Info: {}", dockerClient.infoCmd().exec().getName());
    }

    /**
     * Starts a container based on the provided service definition.
     *
     * @param service The service to start.
     * @return A ServiceInstance record with runtime info (container ID, mapped ports).
     */
    public ServiceInstance startContainer(ServiceDefinition service) {
        log.info("Starting container for service with image: {}", service.imageName());

        // --- 1. Pull Image (optional, but good practice) ---
        // We'll skip the pull command for now to keep it simple,
        // assuming the image is local or docker-java handles it.

        // --- 2. Configure Port Bindings ---
        List<ExposedPort> exposedPorts = service.exposedPorts().stream()
                .map(ExposedPort::tcp)
                .collect(Collectors.toList());

        // Bind each exposed port to a random, available port on the host.
        PortBinding[] portBindings = exposedPorts.stream()
                .map(port -> new PortBinding(Ports.Binding.empty(), port))
                .toArray(PortBinding[]::new);

        HostConfig hostConfig = new HostConfig().withPortBindings(portBindings);

        // --- 3. Create Container ---
        CreateContainerResponse container = dockerClient.createContainerCmd(service.imageName())
                .withExposedPorts(exposedPorts)
                .withHostConfig(hostConfig)
                .withEnv(service.environmentVariables().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.toList()))
                .exec();

        String containerId = container.getId();
        log.info("Container created with ID: {}", containerId);

        // --- 4. Start Container ---
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Container started: {}", containerId);

        // --- 5. Inspect to find mapped ports ---
        Map<Integer, Integer> mappedPorts = getMappedPorts(containerId);
        log.info("Container {} ports mapped: {}", containerId, mappedPorts);

        return new ServiceInstance(containerId, service, mappedPorts);
    }

    /**
     * Helper method to inspect a container and find its port mappings.
     */
    private Map<Integer, Integer> getMappedPorts(String containerId) {
        Map<Integer, Integer> portMap = new HashMap<>();

        // Inspect the container to get its network settings
        Ports ports = dockerClient.inspectContainerCmd(containerId).exec()
                .getNetworkSettings()
                .getPorts();

        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : ports.getBindings().entrySet()) {
            // We only care about TCP ports for now
            if (entry.getKey().getProtocol() == Ports.Protocol.TCP) {
                int containerPort = entry.getKey().getPort();

                // Get the first binding (if it exists)
                if (entry.getValue() != null && entry.getValue().length > 0) {
                    int hostPort = Integer.parseInt(entry.getValue()[0].getHostPortSpec());
                    portMap.put(containerPort, hostPort);
                }
            }
        }
        return portMap;
    }


    /**
     * Stops and removes a container.
     *
     * @param containerId The ID of the container to stop.
     */
    public void stopContainer(String containerId) {
        log.info("Stopping container: {}", containerId);
        try {
            dockerClient.stopContainerCmd(containerId).exec();
            log.info("Removing container: {}", containerId);
            dockerClient.removeContainerCmd(containerId).exec();
            log.info("Container removed: {}", containerId);
        } catch (Exception e) {
            log.warn("Could not stop or remove container {}: {}", containerId, e.getMessage());
            // In a real lib, you might force remove: .withForce(true)
        }
    }
}
