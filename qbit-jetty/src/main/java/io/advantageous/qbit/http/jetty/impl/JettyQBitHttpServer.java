package io.advantageous.qbit.http.jetty.impl;

import io.advantageous.qbit.GlobalConstants;
import io.advantageous.qbit.http.HttpRequest;
import io.advantageous.qbit.http.impl.SimpleHttpServer;
import io.advantageous.qbit.system.QBitSystemManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static io.advantageous.qbit.servlet.QBitServletUtil.convertRequest;

/**
 * Created by rhightower on 2/13/15.
 */
public class JettyQBitHttpServer extends SimpleHttpServer {

    private final Logger logger = LoggerFactory.getLogger(SimpleHttpServer.class);
    private final boolean debug = false || GlobalConstants.DEBUG || logger.isDebugEnabled();
    private final Server server;
    //private final QBitWebSocketHandler qBitWebSocketHandler ;
    private final WebSocketPolicy policy = new WebSocketPolicy(WebSocketBehavior.SERVER);


    private final WebSocketServletFactory webSocketServletFactory;

    public JettyQBitHttpServer(final String host,
                               final int port,
                               final int flushInterval,
                               final int httpWorkers,
                               final QBitSystemManager systemManager) {
        super(systemManager, flushInterval);

        this.server = new Server();

        final ThreadPool threadPool = this.server.getThreadPool();

        if (threadPool instanceof QueuedThreadPool) {
            if (httpWorkers > 4) {
                ((QueuedThreadPool) threadPool).setMaxThreads(httpWorkers);
                ((QueuedThreadPool) threadPool).setMinThreads(4);
            }
        }

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        connector.setHost(host);
        server.addConnector(connector);


        webSocketServletFactory = webSocketServletFactory();


        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(final String target,
                               final Request baseRequest,
                               final HttpServletRequest request,
                               final HttpServletResponse response)
                    throws IOException, ServletException {

                if (webSocketServletFactory.isUpgradeRequest(request, response)) {
                    /* We have an upgrade request. */
                    if (webSocketServletFactory.acceptWebSocket(request, response)) {

                        baseRequest.setHandled(true);
                        /* websocket created */
                        return;
                    }
                    if (response.isCommitted()) {
                        return;
                    }
                } else {
                    baseRequest.setAsyncSupported(true);
                    handleRequestInternal(request);
                }
            }
        });

    }

    private WebSocketServletFactory webSocketServletFactory() {

        try {
            WebSocketServletFactory webSocketServletFactory = WebSocketServletFactory.Loader.create(policy);
            webSocketServletFactory.init();
            webSocketServletFactory.setCreator(new WebSocketCreator() {
                @Override
                public Object createWebSocket(ServletUpgradeRequest request, ServletUpgradeResponse response) {
                    return new JettyNativeWebSocketHandler(request, JettyQBitHttpServer.this);
                }
            });
            return webSocketServletFactory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void handleRequestInternal(final HttpServletRequest request) {
        final HttpRequest httpRequest = convertRequest(request.startAsync());
        super.handleRequest(httpRequest);
    }

    @Override
    public void start() {
        super.start();
        try {
            server.start();
        } catch (Exception ex) {
            logger.error("Unable to start up Jetty", ex);
        }
    }


    public void stop() {
        super.stop();
        try {
            server.stop();
        } catch (Exception ex) {
            logger.error("Unable to shut down Jetty", ex);
        }
    }
}
