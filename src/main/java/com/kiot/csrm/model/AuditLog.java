package com.kiot.csrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long userId;
  public String action;
  public java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
}
