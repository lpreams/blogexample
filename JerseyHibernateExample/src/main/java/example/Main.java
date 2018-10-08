package example;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * This is a change
 * 
 * This is a change, and so is this
 * This is also a change
 * @author lpreams
 *
 */

public class Main {
	public static void main(String[] args) {
		DB.startDatabaseConnection();
		startWebserver();
	}
	
	/**
	 * Start the Java web server
	 */
	private static void startWebserver() {
		System.out.println("Starting server"); //Alert the user to allow them to know that the code is actually running
		
		enableExceptionLogging();
		
		ResourceConfig resourceConfig = new ResourceConfig(API.class); // register that the API class contains methods for handling HTTP requests
		resourceConfig.register(UTF8Filter.class); // apply UTF8Filter to server so it will only serve UTF-8
		
		// create the server to listen on all IP addresses (0.0.0.0) on port 8080
		HttpServer server = GrizzlyHttpServerFactory.createHttpServer(UriBuilder.fromUri("http://0.0.0.0/").port(8080).build(), resourceConfig);
		
		try {
			server.start();
		} catch (IOException e) {
			throw new RuntimeException(e); // if the server doesn't start, it should probably just exit (probably means you already have a server running on port 8080)
		}
		System.out.println("Server started");
	}
	
	/**
	 * Without the following block of code, exception which occur during API calls will not be logged to stdout
	 */
	private static void enableExceptionLogging() {
		// Enable exception logging
		Logger l = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
		l.setLevel(Level.FINE);
		l.setUseParentHandlers(false);
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.ALL);
		l.addHandler(ch);
	}
	
	// used to force server to only serve UTF-8
	private static class UTF8Filter implements ContainerResponseFilter {
	    @Override
	    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
	        MediaType type = response.getMediaType();
	        if (type != null) {
	            String contentType = type.toString();
	            if (!contentType.contains("charset")) {
	                contentType = contentType + ";charset=utf-8";
	                response.getHeaders().putSingle("Content-Type", contentType);
	            }
	        }
	    }
	}
}
