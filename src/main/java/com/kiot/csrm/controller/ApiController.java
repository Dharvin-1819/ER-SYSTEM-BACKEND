package com.kiot.csrm.controller;

import com.kiot.csrm.model.*;
import com.kiot.csrm.service.CampusOperations;
import com.kiot.csrm.service.CampusOperations.*;
import jakarta.validation.Valid;
import java.time.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final CampusOperations operations;

  public ApiController(CampusOperations operations) {
    this.operations = operations;
  }

  @PostMapping("/auth/register")
  Object register(@Valid @RequestBody Register r) {
    return operations.register(r);
  }

  @PostMapping("/auth/login")
  Object login(@Valid @RequestBody Login r) {
    return operations.login(r);
  }

  @PostMapping("/auth/change-password")
  Object changePassword(
      Authentication a, @Valid @RequestBody CampusOperations.PasswordChange request) {
    return operations.changePassword(a, request);
  }

  @GetMapping("/me")
  Object current(Authentication a) {
    return operations.current(a);
  }

  @GetMapping("/resources")
  Object resources(
      Authentication a,
      @RequestParam(required = false) LocalDateTime start,
      @RequestParam(required = false) LocalDateTime end) {
    return operations.resources(a, start, end);
  }

  @PostMapping("/resources")
  Object resource(Authentication a, @RequestBody Resource r) {
    return operations.resource(a, r);
  }

  @PutMapping("/resources/{id}")
  Object editResource(Authentication a, @PathVariable Long id, @RequestBody Resource r) {
    return operations.editResource(a, id, r);
  }

  @DeleteMapping("/resources/{id}")
  Object removeResource(Authentication a, @PathVariable Long id) {
    return operations.removeResource(a, id);
  }

  @GetMapping("/bookings")
  Object bookings(Authentication a) {
    return operations.bookings(a);
  }

  @PostMapping("/bookings")
  Object book(Authentication a, @Valid @RequestBody Reserve r) {
    return operations.book(a, r);
  }

  @PutMapping("/bookings/{id}")
  Object modify(Authentication a, @PathVariable Long id, @Valid @RequestBody Reserve r) {
    return operations.modify(a, id, r);
  }

  @DeleteMapping("/bookings/{id}")
  Object cancel(Authentication a, @PathVariable Long id) {
    return operations.cancel(a, id);
  }

  @GetMapping("/resources/{id}/schedule")
  Object schedule(Authentication a, @PathVariable Long id, @RequestParam LocalDate date) {
    return operations.schedule(a, id, date);
  }

  @GetMapping("/admin/users")
  Object users(Authentication a) {
    return operations.users(a);
  }

  @PatchMapping("/admin/users/{id}")
  Object approve(Authentication a, @PathVariable Long id, @RequestBody UserUpdate r) {
    return operations.approve(a, id, r);
  }

  @GetMapping("/audit")
  Object audit(
      Authentication a,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) LocalDate date) {
    return operations.audit(a, userId, date);
  }

  @GetMapping("/notifications")
  Object notifications(Authentication a) {
    return operations.notifications(a);
  }

  @GetMapping("/services")
  Object services(Authentication a) {
    return operations.services(a);
  }

  @PostMapping("/services")
  Object service(Authentication a, @Valid @RequestBody ServiceInput input) {
    return operations.service(a, input);
  }

  @PatchMapping("/services/{id}")
  Object closeService(Authentication a, @PathVariable Long id) {
    return operations.closeService(a, id);
  }

  @GetMapping("/admin/reports")
  Object reports(Authentication a, @RequestParam(required = false) LocalDate date) {
    return operations.reports(a, date);
  }
}
