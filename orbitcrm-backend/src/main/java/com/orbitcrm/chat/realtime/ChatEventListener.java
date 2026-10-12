package com.orbitcrm.chat.realtime;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Receives live chat events from all server instances (PostgreSQL {@code LISTEN}) and hands them to the
 * {@link ChatHub}.
 *
 * <p>Purpose: the receiving half of the cross-instance fan-out started by {@link ChatEventPublisher}. It runs on one
 * dedicated thread with its own database connection, outside the connection pool, so it never takes a pooled
 * connection away from requests.
 * <ul>
 *   <li>It waits for notifications in short slices and checks the connection with a trivial query when idle; TCP
 *   keep-alive and a socket timeout catch half-open connections.</li>
 *   <li>When the connection drops it reconnects with exponential back-off (1 s up to 30 s). Events published in the
 *   meantime are lost, so after reconnecting it asks every local stream to resync (browsers refetch).</li>
 *   <li>A malformed or failing event is logged and skipped; it never stops the loop.</li>
 * </ul>
 *
 * @author Abhay
 */
@Component
class ChatEventListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChatEventListener.class);
    private static final int WAIT_MILLIS = 10_000;
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final ChatHub hub;
    private final JsonMapper json;
    private final String url;
    private final Properties connectionProperties = new Properties();
    private volatile boolean running;
    private volatile Connection connection;
    private Thread thread;

    ChatEventListener(ChatHub hub, JsonMapper json, @Value("${spring.datasource.url}") String url,
                      @Value("${spring.datasource.username}") String username,
                      @Value("${spring.datasource.password}") String password) {
        this.hub = hub;
        this.json = json;
        this.url = url;
        connectionProperties.setProperty("user", username);
        connectionProperties.setProperty("password", password);
        connectionProperties.setProperty("ApplicationName", "orbitcrm-chat-listener");
        connectionProperties.setProperty("tcpKeepAlive", "true");
        connectionProperties.setProperty("socketTimeout", "60");
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("chat-listener").daemon().start(this::run);
    }

    @Override
    public void stop() {
        running = false;
        Connection current = connection;
        if (current != null) {
            try {
                current.close();
            } catch (SQLException ignored) {
                // closing only to unblock the listener thread
            }
        }
        thread.interrupt();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void run() {
        boolean reconnecting = false;
        long backoff = 1000;
        while (running) {
            try (Connection conn = DriverManager.getConnection(url, connectionProperties)) {
                connection = conn;
                try (Statement statement = conn.createStatement()) {
                    statement.execute("LISTEN " + ChatEventPublisher.CHANNEL);
                }
                if (reconnecting) {
                    log.info("Chat event listener reconnected");
                    hub.resync();
                }
                reconnecting = true;
                backoff = 1000;
                listen(conn);
            } catch (SQLException ex) {
                if (running) {
                    log.warn("Chat event listener lost its database connection; retrying in {} ms", backoff, ex);
                    sleep(backoff);
                    backoff = Math.min(backoff * 2, MAX_BACKOFF.toMillis());
                }
            } finally {
                connection = null;
            }
        }
    }

    private void listen(Connection conn) throws SQLException {
        PGConnection postgres = conn.unwrap(PGConnection.class);
        while (running) {
            PGNotification[] notifications = postgres.getNotifications(WAIT_MILLIS);
            if (notifications == null || notifications.length == 0) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute("SELECT 1");
                }
                continue;
            }
            for (PGNotification notification : notifications) {
                dispatch(notification.getParameter());
            }
        }
    }

    private void dispatch(String payload) {
        try {
            hub.deliver(json.readValue(payload, ChatEvent.class));
        } catch (RuntimeException ex) {
            log.warn("Skipping chat event that could not be delivered", ex);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
