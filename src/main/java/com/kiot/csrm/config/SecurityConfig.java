package com.kiot.csrm.config;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  BCryptPasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtDecoder decoder(
      @Value("${app.jwt.secret}") String secret, com.kiot.csrm.repository.UserRepository users) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(), currentAccountValidator(users)));
    return decoder;
  }

  @Bean
  OAuth2TokenValidator<Jwt> currentAccountValidator(com.kiot.csrm.repository.UserRepository users) {
    return jwt -> {
      var user = users.findByUsername(jwt.getSubject());
      if (user.isPresent()
          && "APPROVED".equals(user.get().status)
          && user.get().tokenVersion != null
          && user.get().tokenVersion.toString().equals(jwt.getClaimAsString("version")))
        return OAuth2TokenValidatorResult.success();
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Account access changed", null));
    };
  }

  @Bean
  JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
    return new NimbusJwtEncoder(
        new com.nimbusds.jose.jwk.source.ImmutableSecret<>(
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity h) throws Exception {
    return h.csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            c -> c.requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                .requestMatchers("/api/auth/**").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(
            c ->
                c.jwt(j -> {})
                    .authenticationEntryPoint(
                        (q, s, e) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          s.getWriter().write("{\"message\":\"Please log in again\"}");
                        }))
        .build();
  }
}
