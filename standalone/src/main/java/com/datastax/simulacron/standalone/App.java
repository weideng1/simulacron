package com.datastax.simulacron.standalone;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.beust.jcommander.JCommander;
import com.datastax.simulacron.http.server.ClusterManager;
import com.datastax.simulacron.http.server.HttpContainer;
import com.datastax.simulacron.http.server.SwaggerUI;
import com.datastax.simulacron.server.AddressResolver.Inet4Resolver;
import com.datastax.simulacron.server.Server;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;

public class App {

  private static final Logger logger = (Logger) LoggerFactory.getLogger(App.class);

  public static synchronized void main(String args[]) {

    CommandLineArguments cli = new CommandLineArguments();
    JCommander commander = new JCommander(cli, args);
    if (cli.help) {
      commander.usage();
      System.exit(-2);
    }

    // Adjust the root logger level based on configuration.
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    if (cli.verbose) {
      root.setLevel(Level.DEBUG);
    } else {
      root.setLevel(cli.logLevel);
    }

    HttpContainer httpServer = new HttpContainer(cli.httpInterface, cli.httpPort, cli.verbose);

    InetAddress ipAddress;
    try {
      ipAddress = InetAddress.getByName(cli.ipAddress);
      // Attempt to open up a socket on the interface
      new ServerSocket(0, 0, ipAddress);
    } catch (Exception e) {
      ipAddress = InetAddress.getLoopbackAddress();
      logger.error(
          "Failure binding on starting IP address to use for simulated C* nodes, defaulting to {}",
          ipAddress.getHostAddress(),
          e);
    }
    byte[] ipBytes = ipAddress.getAddress();
    Server nativeServer = Server.builder().withAddressResolver(new Inet4Resolver(ipBytes)).build();

    // TODO: There should probably be a module in http-server for setting up the http server instead of doing it here.
    ClusterManager provisioner = new ClusterManager(nativeServer);
    provisioner.registerWithRouter(httpServer.getRouter());

    SwaggerUI swaggerUI = new SwaggerUI();
    swaggerUI.registerWithRouter(httpServer.getRouter());

    httpServer.start();
    logger.info(
        "Started HTTP server interface @ http://{}:{}.  Created nodes will start with ip {}.",
        cli.httpInterface,
        cli.httpPort,
        ipAddress);
    logger.info(
        "New to simulacron?  Visit http://{}:{}/doc for interactive API documentation.",
        cli.httpInterface,
        cli.httpPort);

    try {
      App.class.wait();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}