package com.kiot.csrm.repository;

import com.kiot.csrm.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select count(b) from Booking b where b.resourceId=:resource and b.status='CONFIRMED' and"
          + " b.startTime<:end and b.endTime>:start and (:exclude is null or b.id<>:exclude)")
  long conflicts(
      @org.springframework.data.repository.query.Param("resource") Long resource,
      @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
      @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
      @org.springframework.data.repository.query.Param("exclude") Long exclude);
}
