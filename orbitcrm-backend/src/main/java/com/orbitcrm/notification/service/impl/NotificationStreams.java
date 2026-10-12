package com.orbitcrm.notification.service.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registry of the open server-sent event (SSE) connections per user.
 *
 * <p>Purpose: deliver notifications to connected browsers in real time. {@link #open} registers a new emitter
 * (30-minute timeout) and sends a {@code ready} event; {@link #push} sends a {@code notification} event to all of a
 * user's connections and drops broken ones. The state is in memory, so with several instances a user only receives
 * pushes from the instance they are connected to; clients should also poll the unread count, and a Redis pub/sub
 * fan-out would replace this when scaling out.
 *
 * @author Abhay
 */
@Component
public class NotificationStreams {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter open(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> emitters.getOrDefault(userId, List.of()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            remove.run();
        }
        return emitter;
    }

    public void push(UUID userId, Object payload) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                emitters.getOrDefault(userId, List.of()).remove(emitter);
            }
        }
    }
}
