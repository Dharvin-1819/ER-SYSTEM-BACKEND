package com.kiot.csrm.service;

import com.kiot.csrm.model.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final CampusService s;
  private final BCryptPasswordEncoder passwords;
  private final JwtEncoder jwt;

  public AuthService(CampusService s, BCryptPasswordEncoder p, JwtEncoder j) {
    this.s = s;
    passwords = p;
    jwt = j;
  }

  public User register(String name, String password, String role, String email, String phone) {
    if (!Set.of("STUDENT", "FACULTY").contains(role))
      throw CampusService.fail(400, "Choose STUDENT or FACULTY");
    if (s.users().findByUsername(name).isPresent())
      throw CampusService.fail(409, "Username already exists");
    User u = new User();
    u.username = name;
    u.password = passwords.encode(password);
    u.role = role;
    u.status = "PENDING";
    u.email = email;
    u.phone = phone;
    s.users().save(u);
    s.audit(u, "ACCOUNT_REGISTERED");
    return u;
  }

  public Map<String, Object> login(String name, String password) {
    User u =
        s.users()
            .findByUsername(name)
            .orElseThrow(() -> CampusService.fail(401, "Invalid username or password"));
    if (!passwords.matches(password, u.password))
      throw CampusService.fail(401, "Invalid username or password");
    s.user(name);
    // Existing rows created before token versioning was introduced have a null value.
    if (u.tokenVersion == null) {
      u.tokenVersion = UUID.randomUUID();
      s.users().save(u);
    }
    Instant now = Instant.now();
    String token =
        jwt.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder()
                        .subject(name)
                        .claim("version", u.tokenVersion.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(28800))
                        .build()))
            .getTokenValue();
    s.audit(u, "LOGIN");
    return Map.of("token", token, "user", u);
  }

  public void changePassword(User user, String currentPassword, String newPassword) {
    if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72)
      throw CampusService.fail(400, "New password must be 8–72 characters");
    if (!passwords.matches(currentPassword, user.password))
      throw CampusService.fail(400, "Current password is incorrect");
    if (passwords.matches(newPassword, user.password))
      throw CampusService.fail(400, "Choose a password you have not used before");
    user.password = passwords.encode(newPassword);
    user.tokenVersion = UUID.randomUUID();
    s.users().save(user);
    s.audit(user, "PASSWORD_CHANGED");
  }
}
