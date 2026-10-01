package com.kiot.csrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "service_requests")
public class ServiceRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long userId;
  public String description;
  public String status = "PENDING";
  public java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
}
