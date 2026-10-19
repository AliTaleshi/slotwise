package com.slotwise.notification.service;

import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.service.BookingViews;
import com.slotwise.booking.service.ManageTokenService;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.offering.repository.OfferingRepository;
import com.slotwise.staff.repository.StaffRepository;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/** Renders and sends booking emails. All times are shown in the business's time zone. */
@Service
public class BookingEmailService {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.ENGLISH);
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("HH:mm (zzz, 'UTC'xxx)", Locale.ENGLISH);

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine templates;
  private final BusinessRepository businesses;
  private final StaffRepository staff;
  private final OfferingRepository offerings;
  private final ManageTokenService manageTokens;
  private final BookingViews views;
  private final NotificationProperties props;
  private final Clock clock;

  public BookingEmailService(
      JavaMailSender mailSender,
      SpringTemplateEngine templates,
      BusinessRepository businesses,
      StaffRepository staff,
      OfferingRepository offerings,
      ManageTokenService manageTokens,
      BookingViews views,
      NotificationProperties props,
      Clock clock) {
    this.mailSender = mailSender;
    this.templates = templates;
    this.businesses = businesses;
    this.staff = staff;
    this.offerings = offerings;
    this.manageTokens = manageTokens;
    this.views = views;
    this.props = props;
    this.clock = clock;
  }

  public void send(EmailMessage kind, Booking booking) {
    try {
      mailSender.send(build(kind, booking));
    } catch (Exception e) {
      throw new IllegalStateException(
          "Could not send " + kind + " email for " + booking.getId(), e);
    }
  }

  MimeMessage build(EmailMessage kind, Booking booking) throws Exception {
    Business business = businesses.findById(booking.getBusinessId()).orElseThrow();
    String serviceName = offerings.findById(booking.getOfferingId()).orElseThrow().getName();
    String staffName = staff.findById(booking.getStaffId()).orElseThrow().getName();
    var start = booking.getStartsAt().atZone(business.zone());

    Context ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("guestName", booking.getGuestName());
    ctx.setVariable("businessName", business.getName());
    ctx.setVariable("serviceName", serviceName);
    ctx.setVariable("staffName", staffName);
    ctx.setVariable("date", DATE.format(start));
    ctx.setVariable("time", TIME.format(start));
    ctx.setVariable("timeZone", business.getTimeZone());
    ctx.setVariable("price", booking.getPrice() + " " + booking.getCurrency());
    ctx.setVariable("manageUrl", views.manageUrl(booking, manageTokens.issue(booking)));
    ctx.setVariable("cutoffHours", business.getCancelCutoffMinutes() / 60);

    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
    helper.setFrom(props.from());
    helper.setTo(booking.getGuestEmail());
    helper.setSubject(kind.subject() + " – " + business.getName());
    helper.setText(templates.process(kind.template(), ctx), true);

    if (kind != EmailMessage.REMINDER) {
      boolean cancel = kind == EmailMessage.CANCELLED;
      var event =
          new IcsBuilder.Event(
              booking.getId(),
              booking.getStartsAt(),
              booking.getEndsAt(),
              serviceName + " – " + business.getName(),
              "With " + staffName,
              business.getName(),
              cancel,
              (int) booking.getVersion());
      String ics = IcsBuilder.single(event, cancel, clock.instant());
      helper.addAttachment(
          "appointment.ics",
          new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)),
          "text/calendar; method=" + (cancel ? "CANCEL" : "REQUEST") + "; charset=UTF-8");
    }
    return message;
  }
}
