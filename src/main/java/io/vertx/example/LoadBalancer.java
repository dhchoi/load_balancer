package io.vertx.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadBalancer {
    private static final int THREAD_POOL_SIZE = 4;
    private final ServerSocket socket;
    private final CopyOnWriteArrayList<DataCenterInstance> instances;
    private int requestCount = 0;

    public LoadBalancer(ServerSocket socket, CopyOnWriteArrayList<DataCenterInstance> instances) {
        this.socket = socket;
        this.instances = instances;
    }

    /**
     *  Send the request in round robin manner
     */
    public void start() throws IOException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        while (true) {
            if (instances.size() == 0) {
                continue;
            }

            int instanceIndex = requestCount % instances.size();
            DataCenterInstance instance = instances.get(instanceIndex);
            while (!instance.isHealthy()) {
                System.out.println("[LoadBalancer] Removing unhealthy instance and checking next one");
                instances.remove(instance);
                instance = instances.get(instanceIndex);
            }

            System.out.println("[LoadBalancer] Forwarding request to instance");
            Runnable requestHandler = new RequestHandler(socket.accept(), instance);
            executorService.execute(requestHandler);
            requestCount++;
        }
    }
}
