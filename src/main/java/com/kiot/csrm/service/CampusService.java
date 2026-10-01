package com.kiot.csrm.service;

import com.kiot.csrm.model.*;
import com.kiot.csrm.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampusService {
  public UserRepository users() {
    return users;
  }

  public ResourceRepository resources() {
    return resources;
  }

  public BookingRepository bookings() {
    return bookings;
  }

  public AuditLogRepository audits() {
    return audits;
  }

  public NotificationRepository notifications() {
    return notifications;
  }

  public ServiceRequestRepository requests() {
    return requests;
  }

  private final UserRepository users;
  private final ResourceRepository resources;
  private final BookingRepository bookings;
  private final AuditLogRepository audits;
  private final NotificationRepository notifications;
  private final ServiceRequestRepository requests;

  public CampusService(
      UserRepository u,
      ResourceRepository r,
      BookingRepository b,
      AuditLogRepository a,
      NotificationRepository n,
      ServiceRequestRepository s) {
    users = u;
    resources = r;
    bookings = b;
    audits = a;
    notifications = n;
    requests = s;
  }

  public static ResponseStatusException fail(int code, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(code), message);
  }

  public User user(String name) {
    User u = users.findByUsername(name).orElseThrow(() -> fail(401, "Unknown account"));
    if (!"APPROVED".equals(u.status)) throw fail(403, "Your account is not approved");
    return u;
  }

  public void admin(User u) {
    if (!"ADMIN".equals(u.role)) throw fail(403, "Administrator access required");
  }

  public void audit(User u, String action) {
    AuditLog a = new AuditLog();
    a.userId = u.id;
    a.action = action;
    audits.save(a);
  }

  public void notify(User u, String text) {
    Notification n = new Notification();
    n.userId = u.id;
    n.message = text;
    notifications.save(n);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Booking saveBooking(
      User u, Long id, Long resourceId, LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null || !end.isAfter(start) || !start.isAfter(LocalDateTime.now()))
      throw fail(400, "Choose a future start and a later end time");
    Booking b =
        id == null
            ? new Booking()
            : bookings.findById(id).orElseThrow(() -> fail(404, "Booking not found"));
    if (id != null && !u.role.equals("ADMIN") && !b.userId.equals(u.id))
      throw fail(403, "This booking belongs to another user");
    if (id != null && !"CONFIRMED".equals(b.status))
      throw fail(400, "Cancelled bookings cannot be modified");
    Resource r = resources.lockById(resourceId).orElseThrow(() -> fail(404, "Resource not found"));
    if (!r.availability) throw fail(400, "Resource is unavailable");
    User owner = id == null ? u : users.findById(b.userId).orElseThrow();
    if (owner.role.equals("STUDENT") && r.type.equals("CLASSROOM"))
      throw fail(403, "Students can reserve labs, lockers and equipment");
    if (bookings.conflicts(resourceId, start, end, id) > 0)
      throw fail(409, "This time overlaps an existing reservation");
    b.userId = owner.id;
    b.resourceId = r.id;
    b.startTime = start;
    b.endTime = end;
    b.status = "CONFIRMED";
    b.reminded = false;
    bookings.save(b);
    audit(u, (id == null ? "BOOKING_CREATED " : "BOOKING_MODIFIED ") + b.id);
    notify(owner, "Booking #" + b.id + " confirmed: " + r.name + " at " + start);
    return b;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Booking cancel(User u, Long id) {
    Booking b = bookings.findById(id).orElseThrow(() -> fail(404, "Booking not found"));
    resources.lockById(b.resourceId);
    if (!u.role.equals("ADMIN") && !b.userId.equals(u.id))
      throw fail(403, "This booking belongs to another user");
    if (!"CANCELLED".equals(b.status)) {
      b.status = "CANCELLED";
      bookings.save(b);
      audit(u, "BOOKING_CANCELLED " + id);
      notify(users.findById(b.userId).orElseThrow(), "Booking #" + id + " cancelled");
    }
    return b;
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public void reminders() {
    LocalDateTime now = LocalDateTime.now();
    for (Booking b : bookings.findAll()) {
      if (b.status.equals("CONFIRMED")
          && !b.reminded
          && b.startTime.isAfter(now)
          && b.startTime.isBefore(now.plusHours(1))) {
        notify(
            users.findById(b.userId).orElseThrow(),
            "Reminder: booking #" + b.id + " starts at " + b.startTime);
        b.reminded = true;
        bookings.save(b);
      }
    }
  }
}
