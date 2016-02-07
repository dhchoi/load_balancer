package io.vertx.example;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.StringEscapeUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.naming.NamingException;
import java.text.SimpleDateFormat;
import java.util.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * LB server
 *
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
    private static List<DataCenterInstance> instances;
    private static ServerSocket serverSocket;

    /**
     * Main function
     *
     * @param args
     * @throws NamingException
     */
    public static void main(String[] args) throws NamingException {
        // create dataCenterList
        instances = new ArrayList<DataCenterInstance>();

        // initial server socket
        initServerSocket();

        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1024));

        // Create http server
        HttpServerOptions serverOptions = new HttpServerOptions();
        httpServer = vertx.createHttpServer(serverOptions);

        // Create Router
        // TODO: implement your LB
        // TODO: add more handlers
        Router router = Router.router(vertx);
        router.route("/add").handler(Server::handleAdd);
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
        
    }

    private static void handleAdd(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response().putHeader("Content-Type", "text/plain; charset=utf-8");
        // to get argument from http request
        String dnsName = routingContext.request().getParam("<your ip goes here>");
        // TODO: implement the add handler


        // close the connection and send the response body
        response.end("<resond message>\n");
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

}

