package com.kiot.csrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "bookings")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public long version;
  public Long userId;
  public Long resourceId;
  public java.time.LocalDateTime startTime;
  public java.time.LocalDateTime endTime;
  public String status;
  public boolean reminded = false;
}
