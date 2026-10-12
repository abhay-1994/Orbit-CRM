package com.orbitcrm.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.orbitcrm.chat.job.PresenceSweepJob;
import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.PostgresTestContainer;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;
import com.orbitcrm.support.TestUsers;

/**
 * Integration tests of in-app chat and presence through the public API, with live streams.
 *
 * <p>Purpose: prove the chat feature end to end against a real PostgreSQL - including row-level security and the
 * LISTEN/NOTIFY fan-out that carries every live event:
 * <ul>
 *   <li>colleagues start one conversation per pair, send idempotent messages, edit and delete their own, page
 *   through history and see unread counts and read receipts;</li>
 *   <li>the other participant's stream receives new messages, edits, typing indicators and receipts as they
 *   happen;</li>
 *   <li>conversations are invisible to non-participants and to other workspaces;</li>
 *   <li>presence follows open streams, chosen statuses, idle tabs and meetings in progress, and the sweep job
 *   expires connections of a vanished server.</li>
 * </ul>
 *
 * @author Abhay
 */
class ChatIntegrationTest extends IntegrationTest {

    private static final Pattern CONNECTION_ID = Pattern.compile("\"connectionId\":\"([0-9a-f-]{36})\"");

    @Autowired
    private PresenceSweepJob sweepJob;

    @Test
    void colleaguesChatInRealTimeWithReceiptsEditsAndIdempotentSends() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session manager = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales Manager", "maya", null);
        LiveStream adminStream = open(admin);

        // One conversation per pair, whoever starts it.
        String conversation = Json.read(call(manager, post("/api/v1/chat/conversations"),
                Json.obj("userId", admin.userId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.with.name").value("Asha Owner"))
                .andExpect(jsonPath("$.data.with.presence").value("AVAILABLE"))
                .andReturn(), "$.data.id");
        call(admin, post("/api/v1/chat/conversations"), Json.obj("userId", manager.userId().toString()))
                .andExpect(jsonPath("$.data.id").value(conversation))
                .andExpect(jsonPath("$.data.with.presence").value("OFFLINE"));

        // Sending is idempotent per clientId.
        String messages = "/api/v1/chat/conversations/" + conversation + "/messages";
        String messageId = Json.read(call(manager, post(messages), Json.obj("clientId", "c-1", "body", "  Hello Asha!  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Hello Asha!"))
                .andReturn(), "$.data.id");
        call(manager, post(messages), Json.obj("clientId", "c-1", "body", "Hello Asha!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(messageId));
        adminStream.waitFor("event:message.created", "Hello Asha!");

        // The recipient sees it unread, then reads it; the sender gets the receipt.
        call(admin, get("/api/v1/chat/unread-count")).andExpect(jsonPath("$.data.unread").value(1));
        call(admin, get("/api/v1/chat/conversations"))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data[0].lastMessage.body").value("Hello Asha!"));
        LiveStream managerStream = open(manager);
        call(admin, post("/api/v1/chat/conversations/" + conversation + "/read"), Json.obj("messageId", messageId))
                .andExpect(jsonPath("$.data.lastReadAt").isNotEmpty());
        managerStream.waitFor("event:read", admin.userId().toString());
        call(admin, get("/api/v1/chat/unread-count")).andExpect(jsonPath("$.data.unread").value(0));
        call(manager, get("/api/v1/chat/conversations/" + conversation))
                .andExpect(jsonPath("$.data.theirLastReadAt").isNotEmpty())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // Typing indicators reach the other participant.
        call(manager, post("/api/v1/chat/conversations/" + conversation + "/typing")).andExpect(status().isNoContent());
        adminStream.waitFor("event:typing", manager.userId().toString());

        // Only the author may edit or delete; both are pushed live.
        call(admin, patch("/api/v1/chat/messages/" + messageId), Json.obj("body", "hijack"))
                .andExpect(status().isNotFound());
        call(manager, patch("/api/v1/chat/messages/" + messageId), Json.obj("body", "Hello Asha, edited"))
                .andExpect(jsonPath("$.data.editedAt").isNotEmpty());
        adminStream.waitFor("event:message.updated", "Hello Asha, edited");
        call(manager, delete("/api/v1/chat/messages/" + messageId)).andExpect(status().isOk());
        call(admin, get(messages))
                .andExpect(jsonPath("$.data.messages[0].deleted").value(true))
                .andExpect(jsonPath("$.data.messages[0].body").doesNotExist());

        // A clientId cannot be replayed into another conversation.
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "ravi", null);
        String other = Json.read(call(manager, post("/api/v1/chat/conversations"),
                Json.obj("userId", rep.userId().toString())).andReturn(), "$.data.id");
        call(manager, post("/api/v1/chat/conversations/" + other + "/messages"), Json.obj("clientId", "c-1", "body", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CLIENT_ID_REUSED"));
    }

    @Test
    void conversationsAreInvisibleToOutsidersAndInputIsValidated() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session manager = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales Manager", "maya", null);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "ravi", null);
        Session stranger = TestAuth.register(mockMvc);
        String conversation = Json.read(call(admin, post("/api/v1/chat/conversations"),
                Json.obj("userId", manager.userId().toString())).andReturn(), "$.data.id");

        for (Session outsider : List.of(rep, stranger)) {
            call(outsider, get("/api/v1/chat/conversations/" + conversation))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
            call(outsider, post("/api/v1/chat/conversations/" + conversation + "/messages"),
                    Json.obj("clientId", "x-1", "body", "hi")).andExpect(status().isNotFound());
        }
        call(stranger, post("/api/v1/chat/conversations"), Json.obj("userId", admin.userId().toString()))
                .andExpect(status().isNotFound());
        call(admin, post("/api/v1/chat/conversations"), Json.obj("userId", admin.userId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHAT_WITH_SELF"));

        String messages = "/api/v1/chat/conversations/" + conversation + "/messages";
        call(admin, post(messages), Json.obj("clientId", "c-1", "body", "   ")).andExpect(status().isBadRequest());
        call(admin, post(messages), Json.obj("clientId", "c-2", "body", "x".repeat(4001)))
                .andExpect(status().isBadRequest());
        call(admin, post(messages), Json.obj("clientId", "not valid!", "body", "hi")).andExpect(status().isBadRequest());

        // People search lists active colleagues (never yourself) with presence.
        call(admin, get("/api/v1/chat/people").param("q", "maya"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(manager.userId().toString()))
                .andExpect(jsonPath("$.data[0].presence").value("OFFLINE"));
        call(admin, get("/api/v1/chat/people"))
                .andExpect(jsonPath("$.data[?(@.id == '" + admin.userId() + "')]").isEmpty());
    }

    @Test
    void historyIsPagedNewestFirstAndReturnedInChronologicalOrder() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session manager = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales Manager", "maya", null);
        String conversation = Json.read(call(admin, post("/api/v1/chat/conversations"),
                Json.obj("userId", manager.userId().toString())).andReturn(), "$.data.id");
        String messages = "/api/v1/chat/conversations/" + conversation + "/messages";
        for (int i = 1; i <= 7; i++) {
            call(i % 2 == 0 ? manager : admin, post(messages), Json.obj("clientId", "m-" + i, "body", "message " + i))
                    .andExpect(status().isCreated());
        }

        MvcResult newest = call(admin, get(messages).param("limit", "3"))
                .andExpect(jsonPath("$.data.messages[*].body").value(contains(
                        "message 5", "message 6", "message 7")))
                .andExpect(jsonPath("$.data.hasMore").value(true)).andReturn();
        String oldestLoaded = Json.read(newest, "$.data.messages[0].id");
        call(admin, get(messages).param("limit", "3").param("before", oldestLoaded))
                .andExpect(jsonPath("$.data.messages[*].body").value(contains(
                        "message 2", "message 3", "message 4")))
                .andExpect(jsonPath("$.data.hasMore").value(true));
        // Sending marks everything before it read for the sender: only message 7 is unread for the manager.
        call(manager, get("/api/v1/chat/unread-count")).andExpect(jsonPath("$.data.unread").value(1));
    }

    @Test
    void presenceFollowsStreamsChosenStatusIdleTabsAndMeetings() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session manager = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales Manager", "maya", null);
        LiveStream adminStream = open(admin);
        expectPresence(admin, manager, "OFFLINE");

        // Opening the app makes the manager Available, and colleagues are told.
        LiveStream managerStream = open(manager);
        expectPresence(admin, manager, "AVAILABLE");
        adminStream.waitFor("event:presence", "{\"userId\":\"" + manager.userId() + "\",\"status\":\"AVAILABLE\"}");

        // A chosen status wins; "In a meeting" cannot be chosen; Available returns to automatic.
        call(manager, put("/api/v1/chat/presence"), Json.obj("status", "DO_NOT_DISTURB"))
                .andExpect(jsonPath("$.data.status").value("DO_NOT_DISTURB"));
        adminStream.waitFor("\"status\":\"DO_NOT_DISTURB\"");
        call(manager, put("/api/v1/chat/presence"), Json.obj("status", "IN_A_MEETING"))
                .andExpect(status().isBadRequest());
        call(manager, put("/api/v1/chat/presence"), Json.obj("status", "AVAILABLE"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // All tabs idle means Away; activity brings the person back.
        String connectionId = managerStream.connectionId();
        call(manager, post("/api/v1/chat/presence/activity"), Json.obj("connectionId", connectionId, "idle", true))
                .andExpect(jsonPath("$.data.status").value("AWAY"));
        call(admin, post("/api/v1/chat/presence/activity"), Json.obj("connectionId", connectionId, "idle", false))
                .andExpect(status().isNotFound());
        call(manager, post("/api/v1/chat/presence/activity"), Json.obj("connectionId", connectionId, "idle", false))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // A meeting in progress shows "In a meeting"; the sweep job announces it when it starts.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        call(manager, post("/api/v1/activities"), Json.write(Json.map("subject", "Pipeline review", "type", "meeting",
                "startAt", now.minusSeconds(30).toString(),
                "endAt", now.plus(30, ChronoUnit.MINUTES).toString()))).andExpect(status().isCreated());
        expectPresence(admin, manager, "IN_A_MEETING");
        assertThat(sweepJob.run(Instant.now())).isPositive();
        adminStream.waitFor("\"status\":\"IN_A_MEETING\"");

        // A closing tab says goodbye: Offline right away. Nobody can close someone else's stream.
        call(admin, delete("/api/v1/chat/stream/" + connectionId)).andExpect(status().isNoContent());
        expectPresence(admin, manager, "IN_A_MEETING");
        call(manager, delete("/api/v1/chat/stream/" + connectionId)).andExpect(status().isNoContent());
        expectPresence(admin, manager, "OFFLINE");

        // A stream that simply ends (network gone) means Offline too.
        managerStream = open(manager);
        expectPresence(admin, manager, "IN_A_MEETING");
        managerStream.close();
        expectPresence(admin, manager, "OFFLINE");

        // Connections of a server that stopped sending heartbeats are expired by the sweep job.
        open(manager);
        expectPresence(admin, manager, "IN_A_MEETING");
        expireConnectionsOf(manager.userId());
        expectPresence(admin, manager, "OFFLINE");
        assertThat(sweepJob.run(Instant.now())).isPositive();
        adminStream.waitFor("{\"userId\":\"" + manager.userId() + "\",\"status\":\"OFFLINE\"}");
    }

    private void expectPresence(Session viewer, Session person, String status) throws Exception {
        call(viewer, get("/api/v1/chat/presence").param("userIds", person.userId().toString()))
                .andExpect(jsonPath("$.data[0].status").value(status));
    }

    private static void expireConnectionsOf(UUID userId) throws Exception {
        try (Connection db = DriverManager.getConnection(PostgresTestContainer.jdbcUrl(),
                PostgresTestContainer.adminUser(), PostgresTestContainer.adminPassword());
             PreparedStatement update = db.prepareStatement("UPDATE chat_connections "
                     + "SET last_heartbeat = now() - interval '5 minutes' WHERE user_id = ?")) {
            update.setObject(1, userId);
            update.executeUpdate();
        }
    }

    private LiveStream open(Session session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/chat/stream").header("Authorization", session.bearer()))
                .andExpect(request().asyncStarted()).andReturn();
        LiveStream stream = new LiveStream(result);
        stream.waitFor("event:ready");
        return stream;
    }

    private ResultActions call(Session session,
                               MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", session.bearer()));
    }

    private ResultActions call(Session session,
                               MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        return call(session, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * An open chat stream in a test: reads what the server has written so far.
     *
     * <p>Purpose: wait for live events (with a timeout) and close the stream like a browser tab would.
     *
     * @param result the asynchronous MockMvc result holding the stream
     */
    private record LiveStream(MvcResult result) {

        String content() {
            return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        void waitFor(String... fragments) {
            Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(25))
                    .until(() -> List.of(fragments).stream().allMatch(content()::contains));
        }

        String connectionId() {
            Matcher matcher = CONNECTION_ID.matcher(content());
            assertThat(matcher.find()).isTrue();
            return matcher.group(1);
        }

        void close() {
            result.getRequest().getAsyncContext().complete();
        }
    }
}
