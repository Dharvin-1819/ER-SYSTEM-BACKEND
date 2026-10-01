package com.kiot.csrm.repository;

import com.kiot.csrm.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  java.util.Optional<User> findByUsername(String username);
}
