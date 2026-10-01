package com.kiot.csrm;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.*;

@SpringBootApplication
@EnableScheduling
public class CsrmApplication {
  public static void main(String[] args) {
    SpringApplication.run(CsrmApplication.class, args);
  }
}
