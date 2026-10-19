package com.slotwise.config.dev;

import com.slotwise.auth.dto.AuthDtos.RegisterRequest;
import com.slotwise.auth.dto.AuthDtos.TokenResponse;
import com.slotwise.auth.entity.Role;
import com.slotwise.auth.repository.UserRepository;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.auth.service.AuthService;
import com.slotwise.business.dto.BusinessDtos.BusinessResponse;
import com.slotwise.business.dto.BusinessDtos.CreateBusinessRequest;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.business.service.BusinessService;
import com.slotwise.offering.dto.OfferingDtos.CreateOfferingRequest;
import com.slotwise.offering.dto.OfferingDtos.OfferingResponse;
import com.slotwise.offering.service.OfferingService;
import com.slotwise.staff.dto.StaffDtos.CreateStaffRequest;
import com.slotwise.staff.dto.StaffDtos.SetWorkingHoursRequest;
import com.slotwise.staff.dto.StaffDtos.StaffResponse;
import com.slotwise.staff.dto.StaffDtos.WorkingHoursEntry;
import com.slotwise.staff.service.ScheduleService;
import com.slotwise.staff.service.StaffService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sample data for the {@code dev} profile so the API can be tried immediately. Goes through the
 * normal services (so validation and auditing apply) and is idempotent.
 *
 * <pre>
 * owner@demo.test / mia@demo.test / tom@demo.test / customer@demo.test   password: demo-password-123
 * business slug: demo-clinic (Europe/Berlin)
 * </pre>
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

  public static final String PASSWORD = "demo-password-123";
  public static final String SLUG = "demo-clinic";

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

  private final BusinessRepository businesses;
  private final UserRepository users;
  private final AuthService auth;
  private final BusinessService businessService;
  private final OfferingService offeringService;
  private final StaffService staffService;
  private final ScheduleService scheduleService;

  public DevDataSeeder(
      BusinessRepository businesses,
      UserRepository users,
      AuthService auth,
      BusinessService businessService,
      OfferingService offeringService,
      StaffService staffService,
      ScheduleService scheduleService) {
    this.businesses = businesses;
    this.users = users;
    this.auth = auth;
    this.businessService = businessService;
    this.offeringService = offeringService;
    this.staffService = staffService;
    this.scheduleService = scheduleService;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (businesses.existsBySlug(SLUG)) {
      return;
    }
    TokenResponse ownerLogin =
        auth.register(new RegisterRequest("owner@demo.test", PASSWORD, "Dana Owner", Role.OWNER));
    AuthUser owner = new AuthUser(ownerLogin.user().id(), ownerLogin.user().email(), Role.OWNER);
    if (!users.existsByEmail("customer@demo.test")) {
      auth.register(
          new RegisterRequest("customer@demo.test", PASSWORD, "Casey Customer", Role.CUSTOMER));
    }

    BusinessResponse business =
        businessService.create(
            new CreateBusinessRequest(
                "Demo Clinic",
                SLUG,
                "Europe/Berlin",
                "hello@demo-clinic.test",
                "+49 30 1234567",
                60,
                60,
                1440,
                30),
            owner);

    OfferingResponse consultation =
        offeringService.create(
            business.id(),
            new CreateOfferingRequest("Consultation", 30, new BigDecimal("50.00"), "EUR", 0, 5),
            owner);
    OfferingResponse checkUp =
        offeringService.create(
            business.id(),
            new CreateOfferingRequest("Check-up", 60, new BigDecimal("90.00"), "EUR", 0, 10),
            owner);

    StaffResponse mia =
        staffService.create(
            business.id(),
            new CreateStaffRequest(
                "Dr. Mia Keller",
                "mia@demo.test",
                Set.of(consultation.id(), checkUp.id()),
                PASSWORD),
            owner);
    StaffResponse tom =
        staffService.create(
            business.id(),
            new CreateStaffRequest(
                "Tom Becker", "tom@demo.test", Set.of(consultation.id()), PASSWORD),
            owner);

    List<WorkingHoursEntry> miaHours = new ArrayList<>();
    List<WorkingHoursEntry> tomHours = new ArrayList<>();
    for (DayOfWeek day :
        List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY)) {
      miaHours.add(new WorkingHoursEntry(day, LocalTime.of(9, 0), LocalTime.of(13, 0)));
      miaHours.add(new WorkingHoursEntry(day, LocalTime.of(14, 0), LocalTime.of(18, 0)));
      tomHours.add(new WorkingHoursEntry(day, LocalTime.of(10, 0), LocalTime.of(16, 0)));
    }
    scheduleService.setWorkingHours(mia.id(), new SetWorkingHoursRequest(miaHours), owner);
    scheduleService.setWorkingHours(tom.id(), new SetWorkingHoursRequest(tomHours), owner);

    log.info(
        "Seeded demo data: business '{}' (owner@demo.test / {}), services {} and {}",
        SLUG,
        PASSWORD,
        consultation.name(),
        checkUp.name());
  }
}
