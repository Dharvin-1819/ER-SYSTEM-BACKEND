package com.kiot.csrm;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.kiot.csrm.model.*;
import com.kiot.csrm.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:csrm;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.jwt.secret=test-secret-at-least-32-bytes-long-for-jwt-tests",
      "app.admin.password=TestAdmin123!"
    })
@AutoConfigureMockMvc
class CampusIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired CampusService s;
  @Autowired AuthService auth;

  String token(String username, String password) {
    return (String) auth.login(username, password).get("token");
  }

  User approved(String role) {
    User u =
        auth.register(
            "u" + UUID.randomUUID().toString().replace("-", ""),
            "TestPass123!",
            role,
            "test@example.test",
            null);
    u.status = "APPROVED";
    return s.users().save(u);
  }

  @Test
  void pendingAccountCannotLoginAndPasswordsAreNeverReturned() throws Exception {
    String username = "pending" + System.nanoTime();
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "username",
                            username,
                            "password",
                            "TestPass123!",
                            "role",
                            "STUDENT",
                            "email",
                            "test@example.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.status").value("PENDING"));
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("username", username, "password", "TestPass123!"))))
        .andExpect(status().isForbidden());
    assertNotEquals("TestPass123!", s.users().findByUsername(username).orElseThrow().password);
  }

  @Test
  void studentsCannotReadAdminDataOrReserveClassrooms() throws Exception {
    User u = approved("STUDENT");
    String t = token(u.username, "TestPass123!");
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + t))
        .andExpect(status().isForbidden());
    Resource r =
        s.resources().findAll().stream()
            .filter(x -> x.type.equals("CLASSROOM"))
            .findFirst()
            .orElseThrow();
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            s.saveBooking(
                u,
                null,
                r.id,
                LocalDateTime.now().withNano(0).plusDays(2),
                LocalDateTime.now().withNano(0).plusDays(2).plusHours(1)));
  }

  @Test
  void overlapAdjacentModificationOwnershipAndCancellation() {
    User u = approved("FACULTY"), other = approved("FACULTY");
    Resource r = new Resource();
    r.name = "Booking test";
    r.type = "LAB";
    r.location = "Test";
    s.resources().save(r);
    LocalDateTime start = LocalDateTime.now().withNano(0).plusDays(5);
    Booking first = s.saveBooking(u, null, r.id, start, start.plusHours(1));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> s.saveBooking(other, null, r.id, start.plusMinutes(30), start.plusHours(2)));
    Booking adjacent = s.saveBooking(u, null, r.id, start.plusHours(1), start.plusHours(2));
    assertNotNull(adjacent.id);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> s.cancel(other, first.id));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            s.saveBooking(
                other, first.id, r.id, start.plusDays(1), start.plusDays(1).plusHours(1)));
    s.cancel(u, first.id);
    assertNotNull(s.saveBooking(other, null, r.id, start, start.plusHours(1)).id);
    assertTrue(
        s.notifications().findAll().stream()
            .anyMatch(n -> n.userId.equals(u.id) && n.message.contains("cancelled")));
  }

  @Test
  void concurrentRequestsCannotDoubleBook() throws Exception {
    User u = approved("FACULTY");
    Resource r = new Resource();
    r.name = "Concurrency test";
    r.type = "LAB";
    r.location = "Test";
    s.resources().save(r);
    LocalDateTime start = LocalDateTime.now().withNano(0).plusDays(7);
    CountDownLatch gate = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> work =
          () -> {
            gate.await();
            try {
              s.saveBooking(u, null, r.id, start, start.plusHours(1));
              return true;
            } catch (org.springframework.web.server.ResponseStatusException e) {
              assertEquals(409, e.getStatusCode().value());
              return false;
            }
          };
      Future<Boolean> a = pool.submit(work), b = pool.submit(work);
      gate.countDown();
      assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    }
  }

  @Test
  void rejectingAccountRevokesExistingToken() throws Exception {
    User u = approved("STUDENT");
    String t = token(u.username, "TestPass123!");
    u.status = "REJECTED";
    s.users().save(u);
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + t))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/resources")).andExpect(status().isUnauthorized());
  }

  @Test
  void changingPasswordRequiresCurrentPasswordAndRevokesOldToken() throws Exception {
    User u = approved("STUDENT");
    String oldToken = token(u.username, "TestPass123!");
    mvc.perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", "wrong-password", "newPassword", "NewPass456!"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", "TestPass123!", "newPassword", "NewPass456!"))))
        .andExpect(status().isOk());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> auth.login(u.username, "TestPass123!"));
    assertNotNull(auth.login(u.username, "NewPass456!"));
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isUnauthorized());
  }
}
