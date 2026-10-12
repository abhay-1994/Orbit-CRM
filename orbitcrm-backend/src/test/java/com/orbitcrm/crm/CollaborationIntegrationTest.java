package com.orbitcrm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.orbitcrm.activity.job.ReminderJob;
import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;
import com.orbitcrm.support.TestUsers;

/**
 * Integration tests of the collaboration features built on the CRM modules (phase 5).
 *
 * <p>Purpose: verify activities, reminders, notes, attachments, the timeline and notifications through the public API
 * against a real database:
 * <ul>
 *   <li>activities validate their times, track completion and are visible to invited participants;</li>
 *   <li>the reminder job notifies owner and participants exactly once, even when run again;</li>
 *   <li>notes support mentions (with notifications) and only authors can edit them;</li>
 *   <li>attachments are type-checked from their bytes, stored and streamed back with safe headers;</li>
 *   <li>the timeline merges field changes, notes, files and related activities;</li>
 *   <li>assignment notifications are created and their read state can be managed;</li>
 *   <li>lead conversion moves notes, activities and files to the new contact.</li>
 * </ul>
 *
 * @author Abhay
 */
class CollaborationIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired
    private ReminderJob reminderJob;

    @Test
    void activitiesValidateTimesTrackCompletionAndAreVisibleToParticipants() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "pooja", null);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        create(admin, "activities", Json.map("subject", "Kickoff", "type", "meeting"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("REQUIRED"));
        create(admin, "activities", Json.map("subject", "Kickoff", "type", "meeting", "startAt", start.toString(),
                "endAt", start.minusSeconds(60).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("BEFORE_START"));

        String meeting = id(create(admin, "activities", Json.map("subject", "Kickoff", "type", "meeting",
                "startAt", start.toString(), "endAt", start.plus(1, ChronoUnit.HOURS).toString(),
                "participantIds", List.of(rep.userId().toString()))));

        // The rep has OWN scope on activities but sees the meeting they are invited to.
        mockMvc.perform(get("/api/v1/activities/" + meeting).header("Authorization", rep.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values.status").value("not_started"));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("ACTIVITY_INVITE"))
                .andExpect(jsonPath("$.data.content[0].title").value("You were invited: Kickoff"));

        mockMvc.perform(get("/api/v1/calendar").header("Authorization", rep.bearer())
                        .param("from", start.minus(1, ChronoUnit.DAYS).toString())
                        .param("to", start.plus(2, ChronoUnit.DAYS).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Kickoff"));

        update(admin, "activities", meeting, Json.map("status", "completed"))
                .andExpect(jsonPath("$.data.values.completedAt").value(notNullValue()));
        update(admin, "activities", meeting, Json.map("status", "in_progress"))
                .andExpect(jsonPath("$.data.values.completedAt").value(nullValue()));
    }

    @Test
    void reminderJobNotifiesOwnerAndParticipantsExactlyOnce() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "remi", null);
        Instant now = Instant.now();
        create(admin, "activities", Json.map("subject", "Call Acme", "type", "call",
                "startAt", now.plus(10, ChronoUnit.MINUTES).toString(),
                "reminderAt", now.minus(1, ChronoUnit.MINUTES).toString(),
                "participantIds", List.of(rep.userId().toString())))
                .andExpect(status().isCreated());

        assertThat(reminderJob.run(Instant.now())).isGreaterThanOrEqualTo(1);
        assertThat(reminderJob.run(Instant.now())).isZero();

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("ACTIVITY_REMINDER"))
                .andExpect(jsonPath("$.data.content[0].title").value("Reminder: Call Acme"));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", rep.bearer())
                        .param("unreadOnly", "true"))
                .andExpect(jsonPath("$.data.content[*].type", hasItem("ACTIVITY_REMINDER")));
    }

    @Test
    void notesSupportMentionsAndOnlyAuthorsCanEdit() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Management", "mentioned", null);
        String lead = id(create(admin, "leads", Json.map("lastName", "Noted", "companyName", "Noted Co")));

        MvcResult created = mockMvc.perform(post("/api/v1/leads/" + lead + "/notes")
                        .header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("body", "Call back on Monday about pricing",
                                "mentionedUserIds", List.of(rep.userId().toString())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdByName").value("Asha Owner"))
                .andReturn();
        String noteId = Json.read(created, "$.data.id");

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.content[?(@.type == 'MENTION')].body").value("Call back on Monday about pricing"));
        mockMvc.perform(get("/api/v1/leads/" + lead + "/notes").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(patch("/api/v1/notes/" + noteId).header("Authorization", rep.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("body", "Hijacked")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/notes/" + noteId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("pinned", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(true));
        mockMvc.perform(delete("/api/v1/notes/" + noteId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());

        Session stranger = TestAuth.register(mockMvc);
        mockMvc.perform(get("/api/v1/leads/" + lead + "/notes").header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachmentsAreTypeCheckedStoredAndStreamedBack() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String company = id(create(admin, "companies", Json.map("name", "Files Inc")));

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/companies/" + company + "/attachments")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.sizeBytes").value(PNG.length))
                .andExpect(jsonPath("$.data.sha256").value(org.hamcrest.Matchers.hasLength(64)))
                .andReturn();
        String attachmentId = Json.read(uploaded, "$.data.id");

        MvcResult pending = mockMvc.perform(get("/api/v1/attachments/" + attachmentId + "/download")
                        .header("Authorization", admin.bearer()))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult downloaded = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(PNG);

        mockMvc.perform(multipart("/api/v1/companies/" + company + "/attachments")
                        .file(new MockMultipartFile("file", "evil.html", "text/html",
                                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));
        mockMvc.perform(multipart("/api/v1/companies/" + company + "/attachments")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", PNG))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(get("/api/v1/companies/" + company + "/attachments").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/companies/" + company + "/attachments").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void timelineMergesChangesNotesFilesAndActivities() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String deal = id(create(admin, "deals", Json.map("name", "Timeline deal", "amount", 1000)));
        update(admin, "deals", deal, Json.map("amount", 2000)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/deals/" + deal + "/notes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.obj("body", "Budget approved")))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/deals/" + deal + "/attachments")
                        .file(new MockMultipartFile("file", "proposal.txt", "text/plain",
                                "Proposal v1".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated());
        create(admin, "activities", Json.map("subject", "Send contract", "relatedModule", "deals", "relatedId", deal))
                .andExpect(status().isCreated());

        MvcResult timeline = mockMvc.perform(get("/api/v1/deals/" + deal + "/timeline").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind == 'note')].summary").value("Budget approved"))
                .andExpect(jsonPath("$.data.items[?(@.kind == 'attachment')].summary").value("proposal.txt"))
                .andExpect(jsonPath("$.data.items[?(@.kind == 'activity')].summary").value("Send contract"))
                .andExpect(jsonPath("$.data.items[?(@.action == 'update')].summary").value("Updated Amount, Weighted amount"))
                .andReturn();
        List<String> times = Json.read(timeline, "$.data.items[*].at");
        assertThat(times).isSortedAccordingTo((a, b) -> Instant.parse(b).compareTo(Instant.parse(a)));
    }

    @Test
    void assignmentNotificationsAndReadState() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "assignee", null);
        create(admin, "leads", Json.map("lastName", "Assigned", "ownerId", rep.userId().toString()))
                .andExpect(status().isCreated());
        // Creating your own record does not notify yourself.
        create(rep, "leads", Json.map("lastName", "Self made")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.unread").value(1));
        MvcResult list = mockMvc.perform(get("/api/v1/notifications").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("RECORD_ASSIGNED"))
                .andExpect(jsonPath("$.data.content[0].title").value("Lead assigned to you: Assigned"))
                .andExpect(jsonPath("$.data.content[0].actorName").value("Asha Owner"))
                .andReturn();
        String id = Json.read(list, "$.data.content[0].id");
        mockMvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", rep.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.unread").value(0));
        // Other users cannot touch it.
        mockMvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void conversionMovesNotesActivitiesAndFilesToTheNewContact() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String lead = id(create(admin, "leads", Json.map("lastName", "Mover", "companyName", "Mover Ltd")));
        mockMvc.perform(post("/api/v1/leads/" + lead + "/notes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.obj("body", "Met at the expo")))
                .andExpect(status().isCreated());
        create(admin, "activities", Json.map("subject", "Follow up", "relatedModule", "leads", "relatedId", lead))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/leads/" + lead + "/attachments")
                        .file(new MockMultipartFile("file", "card.png", "image/png", PNG))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated());

        MvcResult converted = mockMvc.perform(post("/api/v1/leads/" + lead + "/convert").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn();
        String contact = Json.read(converted, "$.data.contact.id");

        mockMvc.perform(get("/api/v1/contacts/" + contact + "/notes").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/contacts/" + contact + "/attachments").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(post("/api/v1/activities/search").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("filter", Map.of("and", List.of(
                                Map.of("field", "relatedModule", "op", "eq", "value", "contacts"),
                                Map.of("field", "relatedId", "op", "eq", "value", contact)))))))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].labels.relatedId").value("Mover"));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions create(Session session, String module, Map<String, Object> values) throws Exception {
        return mockMvc.perform(post("/api/v1/" + module).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)));
    }

    private ResultActions update(Session session, String module, String id, Map<String, Object> values) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/" + module + "/" + id).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)));
    }

    private static String id(ResultActions actions) throws Exception {
        return Json.read(actions.andExpect(status().isCreated()).andReturn(), "$.data.id");
    }
}
