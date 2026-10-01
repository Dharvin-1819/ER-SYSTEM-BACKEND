package com.kiot.csrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "resources")
public class Resource {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String name;
  public String type;
  public String location;
  public boolean availability = true;
  public java.math.BigDecimal price = java.math.BigDecimal.ZERO;
}
