package com.kiot.csrm.service;

import com.kiot.csrm.model.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@org.springframework.stereotype.Service
public class CampusOperations {
  private final CampusService s;
  private final AuthService auth;

  public CampusOperations(CampusService s, AuthService a) {
    this.s = s;
    auth = a;
  }

  User me(Authentication a) {
    return s.user(a.getName());
  }

  public record Register(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,40}") String username,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank String role,
      @NotBlank @Email String email,
      String phone) {}

  public record Login(@NotBlank String username, @NotBlank String password) {}

  public record Reserve(
      @NotNull Long resourceId, @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime) {}

  public Object register(Register r) {
    return auth.register(r.username, r.password, r.role, r.email, r.phone);
  }

  public Object login(Login r) {
    return auth.login(r.username, r.password);
  }

  public record PasswordChange(
      @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public Object changePassword(Authentication a, PasswordChange request) {
    User user = me(a);
    auth.changePassword(user, request.currentPassword, request.newPassword);
    return Map.of("message", "Password changed. Please sign in again.");
  }

  public Object current(Authentication a) {
    return me(a);
  }

  public Object resources(Authentication a, LocalDateTime start, LocalDateTime end) {
    me(a);
    if ((start == null) != (end == null) || (start != null && !end.isAfter(start)))
      throw CampusService.fail(400, "Supply a valid start and end");
    return s.resources().findAll().stream()
        .filter(
            r ->
                start == null
                    || (r.availability && s.bookings().conflicts(r.id, start, end, null) == 0))
        .toList();
  }

  public Object resource(Authentication a, Resource r) {
    User u = me(a);
    s.admin(u);
    validateResource(r);
    r.id = null;
    s.resources().save(r);
    s.audit(u, "RESOURCE_CREATED " + r.id);
    return r;
  }

  void validateResource(Resource r) {
    if (r.name == null
        || r.name.isBlank()
        || r.location == null
        || r.location.isBlank()
        || r.type == null
        || !Set.of("CLASSROOM", "LAB", "LOCKER", "EQUIPMENT").contains(r.type)
        || r.price == null
        || r.price.signum() < 0)
      throw CampusService.fail(
          400, "Provide a name, location, resource type and non-negative price");
  }

  public Object editResource(Authentication a, Long id, Resource r) {
    User u = me(a);
    s.admin(u);
    validateResource(r);
    if (!s.resources().existsById(id)) throw CampusService.fail(404, "Resource not found");
    r.id = id;
    s.resources().save(r);
    s.audit(u, "RESOURCE_UPDATED " + id);
    return r;
  }

  public Object removeResource(Authentication a, Long id) {
    User u = me(a);
    s.admin(u);
    Resource r =
        s.resources().findById(id).orElseThrow(() -> CampusService.fail(404, "Resource not found"));
    r.availability = false;
    s.resources().save(r);
    s.audit(u, "RESOURCE_RETIRED " + id);
    return Map.of("message", "Resource retired; reservation history retained");
  }

  public Object bookings(Authentication a) {
    User u = me(a);
    return s.bookings().findAll().stream()
        .filter(b -> u.role.equals("ADMIN") || b.userId.equals(u.id))
        .sorted(Comparator.comparing((Booking b) -> b.startTime).reversed())
        .toList();
  }

  public Object book(Authentication a, Reserve r) {
    User u = me(a);
    try {
      return s.saveBooking(u, null, r.resourceId, r.startTime, r.endTime);
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode().value() == 409) s.audit(u, "BOOKING_CONFLICT resource=" + r.resourceId);
      throw e;
    }
  }

  public Object modify(Authentication a, Long id, Reserve r) {
    User u = me(a);
    try {
      return s.saveBooking(u, id, r.resourceId, r.startTime, r.endTime);
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode().value() == 409) s.audit(u, "BOOKING_CONFLICT resource=" + r.resourceId);
      throw e;
    }
  }

  public Object cancel(Authentication a, Long id) {
    return s.cancel(me(a), id);
  }

  public Object schedule(Authentication a, Long id, LocalDate date) {
    me(a);
    return s.bookings().findAll().stream()
        .filter(
            b ->
                b.resourceId.equals(id)
                    && b.status.equals("CONFIRMED")
                    && b.startTime.isBefore(date.plusDays(1).atStartOfDay())
                    && b.endTime.isAfter(date.atStartOfDay()))
        .map(b -> Map.of("startTime", b.startTime, "endTime", b.endTime))
        .toList();
  }

  public Object users(Authentication a) {
    s.admin(me(a));
    return s.users().findAll();
  }

  public record UserUpdate(String status, String role) {}

  public Object approve(Authentication a, Long id, UserUpdate r) {
    User u = me(a);
    s.admin(u);
    User target =
        s.users().findById(id).orElseThrow(() -> CampusService.fail(404, "User not found"));
    if (target.id.equals(u.id)) throw CampusService.fail(400, "You cannot change your own access");
    if (r.status != null) {
      if (!Set.of("APPROVED", "REJECTED", "PENDING").contains(r.status))
        throw CampusService.fail(400, "Invalid status");
      target.status = r.status;
    }
    if (r.role != null) {
      if (!Set.of("ADMIN", "FACULTY", "STUDENT").contains(r.role))
        throw CampusService.fail(400, "Invalid role");
      target.role = r.role;
    }
    s.users().save(target);
    s.audit(u, "ACCOUNT_UPDATED " + id + " " + target.status + " " + target.role);
    return target;
  }

  public Object audit(Authentication a, Long userId, LocalDate date) {
    s.admin(me(a));
    return s.audits().findAll().stream()
        .filter(
            x ->
                (userId == null || userId.equals(x.userId))
                    && (date == null || x.timestamp.toLocalDate().equals(date)))
        .sorted(Comparator.comparing((AuditLog x) -> x.timestamp).reversed())
        .toList();
  }

  public Object notifications(Authentication a) {
    User u = me(a);
    return s.notifications().findAll().stream()
        .filter(n -> n.userId.equals(u.id))
        .sorted(Comparator.comparing((Notification n) -> n.timestamp).reversed())
        .toList();
  }

  public Object services(Authentication a) {
    User u = me(a);
    return s.requests().findAll().stream()
        .filter(r -> u.role.equals("ADMIN") || r.userId.equals(u.id))
        .toList();
  }

  public record ServiceInput(@NotBlank @Size(max = 250) String description) {}

  public Object service(Authentication a, ServiceInput input) {
    User u = me(a);
    ServiceRequest r = new ServiceRequest();
    r.userId = u.id;
    r.description = input.description;
    s.requests().save(r);
    s.audit(u, "SERVICE_REQUESTED " + r.id);
    return r;
  }

  public Object closeService(Authentication a, Long id) {
    User u = me(a);
    s.admin(u);
    ServiceRequest r =
        s.requests().findById(id).orElseThrow(() -> CampusService.fail(404, "Request not found"));
    r.status = "RESOLVED";
    s.requests().save(r);
    s.audit(u, "SERVICE_RESOLVED " + id);
    return r;
  }

  public Object reports(Authentication a, LocalDate date) {
    s.admin(me(a));
    LocalDate day = date == null ? LocalDate.now() : date;
    var all = s.bookings().findAll();
    var rows =
        s.resources().findAll().stream()
            .map(
                r -> {
                  long minutes =
                      all.stream()
                          .filter(
                              b ->
                                  b.resourceId.equals(r.id)
                                      && b.status.equals("CONFIRMED")
                                      && b.startTime.isBefore(day.plusDays(1).atStartOfDay())
                                      && b.endTime.isAfter(day.atStartOfDay()))
                          .mapToLong(
                              b ->
                                  Duration.between(
                                          b.startTime.isBefore(day.atStartOfDay())
                                              ? day.atStartOfDay()
                                              : b.startTime,
                                          b.endTime.isAfter(day.plusDays(1).atStartOfDay())
                                              ? day.plusDays(1).atStartOfDay()
                                              : b.endTime)
                                      .toMinutes())
                          .sum();
                  return Map.of(
                      "resource",
                      r.name,
                      "minutes",
                      minutes,
                      "utilization",
                      Math.round(minutes / 14.4));
                })
            .toList();
    return Map.of(
        "date",
        day,
        "resources",
        rows,
        "pending",
        s.users().findAll().stream().filter(u -> u.status.equals("PENDING")).count(),
        "active",
        all.stream()
            .filter(b -> b.status.equals("CONFIRMED") && b.endTime.isAfter(LocalDateTime.now()))
            .count(),
        "conflicts",
        s.audits().findAll().stream()
            .filter(
                l ->
                    l.action.startsWith("BOOKING_CONFLICT")
                        && l.timestamp.toLocalDate().equals(day))
            .count(),
        "userActivity",
        s.audits().findAll().stream()
            .filter(l -> l.timestamp.toLocalDate().equals(day))
            .collect(
                java.util.stream.Collectors.groupingBy(
                    l -> l.userId, java.util.stream.Collectors.counting())));
  }
}
