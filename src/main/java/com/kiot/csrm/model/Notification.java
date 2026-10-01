package com.kiot.csrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long userId;
  public String message;
  public java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
  public boolean seen = false;
  public boolean emailSent = false;
  public boolean smsSent = false;
  public int attempts = 0;
  public String delivery = "IN_APP";
}
