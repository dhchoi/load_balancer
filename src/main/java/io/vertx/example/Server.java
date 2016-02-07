package io.vertx.example;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.naming.NamingException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LB server
 */
public class Server {

    /**
     * Vert.x configuration
     */
    private static Vertx vertx;
    private static HttpClient httpClient;
    private static HttpServer httpServer;

    /**
     * data structure used for LoadBalancer
     */
    private static final int PORT = 80;
    private static CopyOnWriteArrayList<DataCenterInstance> instances;
    private static ServerSocket serverSocket;

    /**
     * health check configuration
     */
    private static int healthCheckCooldown = 5000;
    private static Thread launchHealthCheck;
    private static HealthCheckRunnable healthCheckRunnable;

    /**
     * Main function
     *
     * @param args
     * @throws NamingException
     */
    public static void main(String[] args) throws NamingException {
        // create dataCenterList
        instances = new CopyOnWriteArrayList<DataCenterInstance>();

        // initial server socket
        initServerSocket();

        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1024));

        // Create http server
        HttpServerOptions serverOptions = new HttpServerOptions();
        httpServer = vertx.createHttpServer(serverOptions);

        // Create Router
        Router router = Router.router(vertx);
        router.route("/add").handler(Server::handleAdd);
        router.route("/remove").handler(Server::handleRemove);
        router.route("/check").handler(Server::handleCheck);
        router.route("/cooldown").handler(Server::handleCooldown);
        router.route("/").handler(routingContext -> {
            routingContext.response().end("OK");
        });

        // Listen for the request on port 8080
        httpServer.requestHandler(router::accept).listen(8080);

        // open a new thread to run the dispatcher to handle traffic from LG
        Thread launchLoadBalancer = new Thread() {
            public void run() {
                LoadBalancer loadBalancer = new LoadBalancer(serverSocket, instances);
                try {
                    loadBalancer.start();
                } catch (IOException e) {

                }
            }
        };
        launchLoadBalancer.start();

        // TODO: handle health check for instances
        launchHealthCheck = new Thread(healthCheckRunnable = new HealthCheckRunnable());
        launchHealthCheck.start();
    }

    private static void handleAdd(RoutingContext routingContext) {
        System.out.println("[Server-ADD] start");
        String ip = routingContext.request().getParam("ip");

        // check if ip is valid, and add the DC if so
        boolean added = false;
        DataCenterInstance dataCenterInstance = new DataCenterInstance(ip, ip);
        if (dataCenterInstance.isHealthy()) {
            instances.add(dataCenterInstance);
            System.out.println("[Server-ADD] health check succeeded");
            added = true;
        }
        else {
            System.out.println("[Server-ADD] health check failed");
        }

        // send response
        String response = "ADD:" + added + ":" + ip;
        System.out.println("[Server-ADD] send response - " + response);
        sendResponse(routingContext, response);
    }

    private static void handleRemove(RoutingContext routingContext) {
        System.out.println("[Server-REMOVE] start");
        String ip = routingContext.request().getParam("ip");

        // remove DC with corresponding ip
        boolean removed = false;
        for (DataCenterInstance instance : instances) {
            if (instance.getUrl().equals(ip)) {
                instances.remove(instance);
                System.out.println("[Server-REMOVE] successfully removed data center instance");
                removed = true;
                break;
            }
        }

        // send response
        String response = "REMOVE:" + removed + ":" + ip;
        System.out.println("[Server-REMOVE] send response - " + response);
        sendResponse(routingContext, response);
    }

    private static void handleCheck(RoutingContext routingContext) {
        System.out.println("[Server-CHECK] start");

        // get list of all DC ips
        String delimiter = "";
        StringBuilder allInstanceIps = new StringBuilder();
        for (DataCenterInstance instance : instances) {
            allInstanceIps.append(delimiter).append(instance);
            delimiter = ",";
        }

        // send response
        String response = allInstanceIps.toString();
        System.out.println("[Server-CHECK] send response - " + response);
        sendResponse(routingContext, response);
    }

    private static void handleCooldown(RoutingContext routingContext) {
        System.out.println("[Server-COOLDOWN] start");
        String cooldown = routingContext.request().getParam("cooldown");

        // set cooldown
        healthCheckCooldown = Integer.valueOf(cooldown) * 1000;
        healthCheckRunnable.terminate();
        launchHealthCheck.interrupt();

        launchHealthCheck = new Thread(healthCheckRunnable = new HealthCheckRunnable());
        launchHealthCheck.start();

        // send response
        String response = "COOLDOWN:" + "true" + ":" + cooldown;
        System.out.println("[Server-COOLDOWN] send response - " + response);
        sendResponse(routingContext, response);
    }

    private static void sendResponse(RoutingContext routingContext, String text) {
        HttpServerResponse response = routingContext.response().putHeader("Content-Type", "text/plain; charset=utf-8");
        // close the connection and send the response body
        response.end(text + "\n");
    }

    /**
     * Initialize the socket on which the Load Balancer will receive requests from the Load Generator
     */
    private static void initServerSocket() {
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port: " + PORT);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    static class HealthCheckRunnable implements Runnable {
        private volatile boolean running = true;

        public void terminate() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    System.out.println("[HealthCheck] Performing scheduled health check with cooldown " + healthCheckCooldown + "ms");
                    for (DataCenterInstance instance : instances) {
                        if (!instance.isHealthy()) {
                            instances.remove(instance);
                            System.out.println("[HealthCheck] Deleted unhealthy instance " + instance);
                        }
                        else {
                            System.out.println("[HealthCheck] Confirmed healthy instance " + instance);
                        }
                    }

                    Thread.sleep(healthCheckCooldown);
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                    System.out.println("[HealthCheck] launchHealthCheck interrupted");
                    running = false;
                }
            }
        }
    }
}

