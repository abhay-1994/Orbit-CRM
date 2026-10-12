package com.orbitcrm.chat.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import com.orbitcrm.chat.dto.MessageView;
import com.orbitcrm.chat.dto.PresenceView;
import com.orbitcrm.chat.repository.ChatRepository;
import com.orbitcrm.chat.service.PresenceService;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;

/**
 * The live chat streams (server-sent events) held by this server instance.
 *
 * <p>Purpose: deliver chat events to browsers in real time and keep presence accurate.
 * <ul>
 *   <li>{@link #open} registers a stream: it records a connection row (the person is now online), then sends a
 *   {@code ready} event with the connection id and the person's presence.</li>
 *   <li>{@link #deliver} forwards an event from {@link ChatEventListener} to the matching local streams of the same
 *   tenant. Message events are expanded into the full message, loaded once per event.</li>
 *   <li>Each stream has its own ordered outbox drained on a virtual thread, so a slow or stalled browser never
 *   delays others. An outbox that grows past {@link #MAX_QUEUED_EVENTS} closes the stream; the browser reconnects
 *   and catches up by refetching.</li>
 *   <li>Every {@link #KEEP_ALIVE} a comment line keeps proxies from closing idle streams and reveals dead ones, and
 *   the heartbeat of all local connection rows is refreshed in one statement. A stream whose row has vanished
 *   (swept during a database outage) is closed so the browser registers again.</li>
 *   <li>When a stream closes, its row is deleted and the person's presence is broadcast a few seconds later, so a
 *   quick reconnect (streams are recycled every {@link #STREAM_TIMEOUT}) never flickers to Offline.</li>
 * </ul>
 * On shutdown all streams are completed first, so graceful shutdown is not held up and browsers move to another
 * instance; the rows left behind expire and are swept by the presence job. The number of open streams is published
 * as the {@code orbitcrm.chat.connections} gauge.
 *
 * @author Abhay
 */
@Component
public class ChatHub implements SmartLifecycle {

    static final Duration KEEP_ALIVE = Duration.ofSeconds(25);
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(10);
    static final int MAX_QUEUED_EVENTS = 256;

    private static final Logger log = LoggerFactory.getLogger(ChatHub.class);
    private static final Duration PRESENCE_SETTLE = Duration.ofSeconds(5);

    private final String instanceId = UuidV7.generate().toString();
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("chat-keep-alive").daemon().factory());
    private final PresenceService presence;
    private final ChatRepository chats;
    private volatile boolean running;

    ChatHub(PresenceService presence, ChatRepository chats, ObjectProvider<MeterRegistry> meters) {
        this.presence = presence;
        this.chats = chats;
        meters.ifAvailable(registry -> Gauge.builder("orbitcrm.chat.connections", connections, Map::size)
                .description("Open chat streams on this instance").register(registry));
    }

    /** Opens a live stream for the signed-in user; called on the request thread (tenant bound). */
    public SseEmitter open(CurrentUser user) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        Connection connection = new Connection(UuidV7.generate(), user.tenantId(), user.userId(), emitter);
        PresenceView me = presence.connected(connection.id, user.userId(), instanceId);
        emitter.onCompletion(() -> closed(connection));
        emitter.onTimeout(() -> closed(connection));
        emitter.onError(ex -> closed(connection));
        connection.enqueue(() -> event("ready", Map.of("connectionId", connection.id, "presence", me)));
        connections.put(connection.id, connection);
        return emitter;
    }

    /**
     * Closes one of the user's streams because the tab is going away, so colleagues see the change within seconds
     * instead of at the next keep-alive. A stream held by another instance is closed there at its next keep-alive,
     * when it finds its row gone.
     */
    public void disconnect(CurrentUser user, UUID connectionId) {
        Connection local = connections.get(connectionId);
        if (local != null && local.userId.equals(user.userId())) {
            close(local);
        } else if (presence.disconnectOwn(connectionId, user.userId())) {
            settleLater(user.tenantId(), user.userId());
        }
    }

    /** Forwards an event to the local streams of its recipients. Called by the listener thread. */
    void deliver(ChatEvent event) {
        List<Connection> targets = connections.values().stream()
                .filter(c -> event.isFor(c.tenantId, c.userId)).toList();
        if (targets.isEmpty()) {
            return;
        }
        Object data = event.data();
        if (event.isMessage()) {
            UUID messageId = UUID.fromString(event.data().get("messageId").asString());
            Optional<MessageView> message = TenantContext.callAs(event.tenantId(), () -> chats.findMessage(messageId));
            if (message.isEmpty()) {
                return;
            }
            data = message.get();
        }
        Object payload = data;
        targets.forEach(c -> c.enqueue(() -> event(event.type(), payload)));
    }

    /** Tells every local stream that events may have been missed, so browsers refetch. */
    void resync() {
        connections.values().forEach(c -> c.enqueue(() -> event("resync", Map.of())));
    }

    @Override
    public void start() {
        long seconds = KEEP_ALIVE.toSeconds();
        timer.scheduleWithFixedDelay(this::keepAlive, seconds, seconds, TimeUnit.SECONDS);
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        timer.shutdownNow();
        connections.values().forEach(c -> complete(c.emitter));
        connections.clear();
        senders.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void keepAlive() {
        try {
            connections.values().forEach(c -> c.enqueue(() -> SseEmitter.event().comment("keep-alive")));
            if (connections.isEmpty()) {
                return;
            }
            Set<UUID> registered = TenantContext.callAsSystem(() -> presence.heartbeat(connections.keySet()));
            connections.values().stream().filter(c -> !registered.contains(c.id)).forEach(this::close);
        } catch (RuntimeException ex) {
            log.warn("Chat keep-alive failed", ex);
        }
    }

    private void close(Connection connection) {
        complete(connection.emitter);
        closed(connection);
    }

    private void closed(Connection connection) {
        if (!connections.remove(connection.id, connection) || !running) {
            return;
        }
        try {
            TenantContext.runAs(connection.tenantId, () -> presence.disconnected(connection.id));
            settleLater(connection.tenantId, connection.userId);
        } catch (RuntimeException ex) {
            log.warn("Could not record closed chat stream {}; the presence job will expire it", connection.id, ex);
        }
    }

    /** Broadcasts the user's presence after a short delay, so a page reload never flickers to Offline. */
    private void settleLater(UUID tenantId, UUID userId) {
        timer.schedule(() -> senders.execute(() -> {
            try {
                TenantContext.runAs(tenantId, () -> presence.publish(List.of(userId)));
            } catch (RuntimeException ex) {
                log.warn("Could not publish presence of user {}", userId, ex);
            }
        }), PRESENCE_SETTLE.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static SseEventBuilder event(String name, Object data) {
        return SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
    }

    private static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // already completed or the response is gone
        }
    }

    /**
     * One open stream with its ordered outbox.
     *
     * <p>Purpose: serialize writes to one browser without blocking anyone else. Events are queued and a single
     * drainer at a time (a virtual thread) writes them in order; the {@code draining} flag hands the role over
     * without locks. Event builders are created at send time because Spring's builders are single-use.
     */
    private final class Connection {

        private final UUID id;
        private final UUID tenantId;
        private final UUID userId;
        private final SseEmitter emitter;
        private final Queue<Supplier<SseEventBuilder>> outbox = new ConcurrentLinkedQueue<>();
        private final AtomicInteger queued = new AtomicInteger();
        private final AtomicBoolean draining = new AtomicBoolean();

        private Connection(UUID id, UUID tenantId, UUID userId, SseEmitter emitter) {
            this.id = id;
            this.tenantId = tenantId;
            this.userId = userId;
            this.emitter = emitter;
        }

        private void enqueue(Supplier<SseEventBuilder> event) {
            if (queued.incrementAndGet() > MAX_QUEUED_EVENTS) {
                log.info("Closing slow chat stream {} of user {}", id, userId);
                close(this);
                return;
            }
            outbox.add(event);
            if (draining.compareAndSet(false, true)) {
                try {
                    senders.execute(this::drain);
                } catch (RejectedExecutionException ex) {
                    draining.set(false);
                }
            }
        }

        private void drain() {
            while (true) {
                Supplier<SseEventBuilder> next = outbox.poll();
                if (next == null) {
                    draining.set(false);
                    // An event may have been queued after poll() but before the flag was cleared.
                    if (outbox.isEmpty() || !draining.compareAndSet(false, true)) {
                        return;
                    }
                    continue;
                }
                queued.decrementAndGet();
                try {
                    emitter.send(next.get());
                } catch (IOException | IllegalStateException ex) {
                    // The browser is gone; the container completes the response and calls the error callback.
                    closed(this);
                    return;
                }
            }
        }
    }
}
