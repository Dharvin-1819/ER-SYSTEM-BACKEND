package com.kiot.csrm.service;

import com.kiot.csrm.model.*;
import com.kiot.csrm.repository.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** A persisted in-app notification doubles as an outbox. External delivery is opt-in. */
@Service
public class DeliveryService {
  private final NotificationRepository notifications;
  private final UserRepository users;
  private final ObjectProvider<JavaMailSender> mail;
  private final Environment env;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public DeliveryService(
      NotificationRepository n, UserRepository u, ObjectProvider<JavaMailSender> m, Environment e) {
    notifications = n;
    users = u;
    mail = m;
    env = e;
  }

  @Scheduled(fixedDelay = 30000)
  public void deliver() {
    boolean email = env.getProperty("app.notifications.email-enabled", Boolean.class, false);
    boolean sms = env.getProperty("app.notifications.sms-enabled", Boolean.class, false);
    if (!email && !sms) return;
    for (Notification n : notifications.findAll()) {
      if (n.attempts >= 3) continue;
      User u = users.findById(n.userId).orElse(null);
      if (u == null) continue;
      boolean needsEmail = email && !n.emailSent && u.email != null && !u.email.isBlank();
      boolean needsSms = sms && !n.smsSent && u.phone != null && !u.phone.isBlank();
      if (!needsEmail && !needsSms) continue;
      n.attempts++;
      if (needsEmail) {
        try {
          JavaMailSender sender = mail.getIfAvailable();
          if (sender == null) throw new IllegalStateException("SMTP not configured");
          SimpleMailMessage message = new SimpleMailMessage();
          message.setFrom(env.getRequiredProperty("app.notifications.from"));
          message.setTo(u.email);
          message.setSubject("Campus reservation update");
          message.setText(n.message);
          sender.send(message);
          n.emailSent = true;
        } catch (Exception e) {
          org.slf4j.LoggerFactory.getLogger(getClass())
              .warn(
                  "Email delivery failed for notification {} ({})",
                  n.id,
                  e.getClass().getSimpleName());
        }
      }
      if (needsSms) {
        try {
          String sid = env.getRequiredProperty("app.sms.account-sid"),
              token = env.getRequiredProperty("app.sms.auth-token"),
              from = env.getRequiredProperty("app.sms.from");
          if (!sid.matches("AC[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("Invalid Twilio account SID");
          String body = "To=" + enc(u.phone) + "&From=" + enc(from) + "&Body=" + enc(n.message);
          var req =
              HttpRequest.newBuilder(
                      URI.create(
                          "https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                  .timeout(Duration.ofSeconds(15))
                  .header(
                      "Authorization",
                      "Basic "
                          + Base64.getEncoder()
                              .encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8)))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build();
          var response = http.send(req, HttpResponse.BodyHandlers.discarding());
          if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("SMS provider rejected request");
          n.smsSent = true;
        } catch (Exception e) {
          org.slf4j.LoggerFactory.getLogger(getClass())
              .warn(
                  "SMS delivery failed for notification {} ({})",
                  n.id,
                  e.getClass().getSimpleName());
        }
      }
      n.delivery =
          "IN_APP"
              + (n.emailSent ? " + EMAIL" : "")
              + (n.smsSent ? " + SMS" : "")
              + ((needsEmail && !n.emailSent) || (needsSms && !n.smsSent)
                  ? " + RETRY_PENDING"
                  : "");
      if (n.attempts >= 3 && n.delivery.contains("RETRY_PENDING"))
        n.delivery = n.delivery.replace("RETRY_PENDING", "DELIVERY_FAILED");
      notifications.save(n);
    }
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
