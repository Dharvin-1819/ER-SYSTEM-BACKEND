package com.kiot.csrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(unique = true, nullable = false)
  public String username;

  @com.fasterxml.jackson.annotation.JsonIgnore public String password;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public java.util.UUID tokenVersion = java.util.UUID.randomUUID();

  @PrePersist
  void initializeTokenVersion() {
    if (tokenVersion == null) tokenVersion = java.util.UUID.randomUUID();
  }

  public String role;
  public String status;
  public String email;
  public String phone;
}
