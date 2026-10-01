package com.kiot.csrm.config;

import com.kiot.csrm.model.*;
import com.kiot.csrm.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SeedData {
  @Bean
  CommandLineRunner seed(
      CampusService s, BCryptPasswordEncoder p, @Value("${app.admin.password}") String password) {
    return args -> {
      if (s.users().findByUsername("admin").isEmpty()) {
        User u = new User();
        u.username = "admin";
        u.password = p.encode(password);
        u.role = "ADMIN";
        u.status = "APPROVED";
        u.email = "admin@example.test";
        s.users().save(u);
      }
      if (s.resources().count() == 0) {
        String[][] data = {
          {"Innovation Lab", "LAB", "Block A · Floor 2"},
          {"Seminar Hall 201", "CLASSROOM", "Academic Block · Floor 2"},
          {"Student Locker A12", "LOCKER", "Library · Ground floor"},
          {"Presentation Kit", "EQUIPMENT", "Media Centre"},
          {"Computing Lab", "LAB", "Technology Block"},
          {"Research Projector", "EQUIPMENT", "Faculty Centre"}
        };
        for (String[] row : data) {
          Resource r = new Resource();
          r.name = row[0];
          r.type = row[1];
          r.location = row[2];
          s.resources().save(r);
        }
      }
    };
  }
}
