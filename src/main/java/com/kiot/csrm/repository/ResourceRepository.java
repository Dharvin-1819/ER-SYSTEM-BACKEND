package com.kiot.csrm.repository;

import com.kiot.csrm.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select r from Resource r where r.id=:id")
  java.util.Optional<Resource> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
