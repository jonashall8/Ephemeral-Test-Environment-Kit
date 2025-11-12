package com.ephemeral;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DockerClientBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the DockerClientWrapper.
 * <p>
 * NOTE: This test requires a running Docker daemon to be present on the host.
 * Tests will be skipped if a connection cannot be established.
 */
class DockerClientWrapperTest {

    private static DockerClientWrapper clientWrapper;
    private static DockerClient rawClient; // For verification

    @BeforeAll
    static void setUp() {
        try {
            // Attempt to connect to Docker
            clientWrapper = new DockerClientWrapper();
            rawClient = DockerClientBuilder.getInstance().build();
            rawClient.pingCmd().exec();
            System.out.println("Docker connection successful. Running tests.");
        } catch (Exception e) {
            // If Docker isn't running, skip all tests in this class
            System.err.println("Docker daemon not found. Skipping tests. Error: " + e.getMessage());
            clientWrapper = null;
            // Use JUnit's Assumptions to skip tests
            Assumptions.abort("Docker daemon is not running. Skipping all tests in this class.");
        }
    }

    @Test
    @DisplayName("Should start, inspect, and stop a container")
    void testStartAndStopContainer() {
        // Using nginx:alpine as it's small and exposes a port (80)
        ServiceDefinition definition = new ServiceDefinition("nginx:alpine", 80);

        ServiceInstance instance = null;
        try {
            // 1. START
            instance = clientWrapper.startContainer(definition);

            // 2. ASSERT (Core Logic)
            assertNotNull(instance);
            assertNotNull(instance.containerId());
            assertFalse(instance.containerId().isBlank());

            // Check port mapping
            assertTrue(instance.mappedPorts().containsKey(80), "Port 80 should be mapped");
            int hostPort = instance.getMappedPort(80);
            assertTrue(hostPort > 1024, "Host port should be a valid, non-privileged port");
            System.out.println("Container " + instance.containerId() + " mapped 80 -> " + hostPort);

            // Verify with raw client that the container is indeed running
            // Create a final variable for use in the lambda
            String containerIdForCheck = instance.containerId();
            assertDoesNotThrow(() -> {
                rawClient.inspectContainerCmd(containerIdForCheck).exec();
            }, "Container should be inspectable");

        } finally {
            // 3. STOP (Cleanup)
            // We use a finally block to ensure cleanup happens EVEN IF assertions fail.
            if (instance != null) {
                // *** THIS IS THE FIX ***
                // Create a new, effectively final variable inside the finally block's scope
                final String finalContainerId = instance.containerId();

                clientWrapper.stopContainer(finalContainerId);

                // 4. VERIFY STOPPED
                // Check that the container was truly removed
                assertThrows(NotFoundException.class, () -> {
                    // Use the new final variable inside the lambda
                    rawClient.inspectContainerCmd(finalContainerId).exec();
                }, "Container should be removed and throw NotFoundException");

                System.out.println("Container " + finalContainerId + " successfully stopped and removed.");
            }
        }
    }
}
