package com.slotwise.support;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Test double for the SMTP server: records messages instead of sending them. */
public class CapturingMailSender extends JavaMailSenderImpl {

  public record Sent(
      String to, String subject, String html, List<String> attachments, String ics) {}

  private final List<Sent> outbox = new CopyOnWriteArrayList<>();
  private final AtomicBoolean failing = new AtomicBoolean();

  public List<Sent> outbox() {
    return outbox;
  }

  public void clear() {
    outbox.clear();
    failing.set(false);
  }

  public void setFailing(boolean failing) {
    this.failing.set(failing);
  }

  @Override
  public void send(MimeMessage... mimeMessages) {
    if (failing.get()) {
      throw new MailSendException("SMTP server down (test)");
    }
    for (MimeMessage m : mimeMessages) {
      try {
        Address[] to = m.getAllRecipients();
        m.saveChanges();
        Collected c = new Collected();
        collect(m, c);
        outbox.add(new Sent(to[0].toString(), m.getSubject(), c.html, c.attachments, c.ics));
      } catch (Exception e) {
        throw new MailSendException("capture failed", e);
      }
    }
  }

  private static class Collected {
    String html = "";
    String ics;
    List<String> attachments = new ArrayList<>();
  }

  private static void collect(Part part, Collected out) throws Exception {
    if (part.isMimeType("multipart/*")) {
      Multipart mp = (Multipart) part.getContent();
      for (int i = 0; i < mp.getCount(); i++) {
        BodyPart bp = mp.getBodyPart(i);
        collect(bp, out);
      }
    } else if (part.getFileName() != null) {
      out.attachments.add(part.getFileName());
      if (part.isMimeType("text/calendar")) {
        out.ics =
            new String(
                part.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
    } else if (part.isMimeType("text/html")) {
      out.html = (String) part.getContent();
    }
  }
}
