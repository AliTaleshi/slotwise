package com.slotwise.booking.service;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.booking.dto.BookingDtos.BookingResponse;
import com.slotwise.booking.dto.ReportDtos.DayCount;
import com.slotwise.booking.dto.ReportDtos.StatsResponse;
import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.entity.BookingStatus;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.common.web.PageResponse;
import com.slotwise.staff.entity.Staff;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side tools for owners and staff: filtered lists, a personal schedule, statistics. */
@Service
public class BookingReportService {

  private static final int MAX_RANGE_DAYS = 366;

  private final BookingRepository bookings;
  private final BusinessRepository businesses;
  private final BusinessAccess access;
  private final BookingViews views;
  private final JdbcTemplate jdbc;

  public BookingReportService(
      BookingRepository bookings,
      BusinessRepository businesses,
      BusinessAccess access,
      BookingViews views,
      JdbcTemplate jdbc) {
    this.bookings = bookings;
    this.businesses = businesses;
    this.access = access;
    this.views = views;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public PageResponse<BookingResponse> list(
      UUID businessId,
      UUID staffId,
      BookingStatus status,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      AuthUser owner) {
    Business business = access.requireOwned(businessId, owner);
    if (from != null && to != null) {
      checkRange(from, to);
    }
    Specification<Booking> spec =
        (root, query, cb) -> {
          List<Predicate> p = new ArrayList<>();
          p.add(cb.equal(root.get("businessId"), businessId));
          if (staffId != null) p.add(cb.equal(root.get("staffId"), staffId));
          if (status != null) p.add(cb.equal(root.get("status"), status));
          if (from != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("startsAt"), start(from, business)));
          }
          if (to != null) {
            p.add(cb.lessThan(root.get("startsAt"), start(to.plusDays(1), business)));
          }
          return cb.and(p.toArray(new Predicate[0]));
        };
    var result =
        bookings.findAll(
            spec,
            PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.ASC, "startsAt").and(Sort.by("id"))));
    return PageResponse.of(result, b -> views.view(b, false));
  }

  /**
   * A staff member's own (or, for owners, anyone's) non-cancelled bookings in a local date range.
   */
  @Transactional(readOnly = true)
  public List<BookingResponse> schedule(UUID staffId, LocalDate from, LocalDate to, AuthUser user) {
    Staff member = access.requireStaffOwnerOrSelf(staffId, user);
    checkRange(from, to);
    Business business = businesses.findById(member.getBusinessId()).orElseThrow();
    return bookings
        .findByStaffIdAndStatusNotAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
            staffId,
            BookingStatus.CANCELLED,
            start(from, business),
            start(to.plusDays(1), business))
        .stream()
        .map(b -> views.view(b, false))
        .toList();
  }

  @Transactional(readOnly = true)
  public StatsResponse stats(UUID businessId, LocalDate from, LocalDate to, AuthUser owner) {
    Business business = access.requireOwned(businessId, owner);
    checkRange(from, to);
    Instant start = start(from, business);
    Instant end = start(to.plusDays(1), business);

    Map<BookingStatus, Long> counts = new HashMap<>();
    Map<String, BigDecimal> revenue = new TreeMap<>();
    jdbc.query(
        "SELECT status, currency, count(*) AS n, sum(price) AS revenue FROM bookings"
            + " WHERE business_id = ? AND starts_at >= ? AND starts_at < ? GROUP BY status, currency",
        rs -> {
          BookingStatus st = BookingStatus.valueOf(rs.getString("status"));
          counts.merge(st, rs.getLong("n"), Long::sum);
          if (st == BookingStatus.CONFIRMED || st == BookingStatus.COMPLETED) {
            revenue.merge(rs.getString("currency"), rs.getBigDecimal("revenue"), BigDecimal::add);
          }
        },
        businessId,
        java.sql.Timestamp.from(start),
        java.sql.Timestamp.from(end));

    Map<LocalDate, Long> perDay = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      perDay.put(d, 0L);
    }
    jdbc.query(
        "SELECT (starts_at AT TIME ZONE ?)::date AS day, count(*) AS n FROM bookings"
            + " WHERE business_id = ? AND status <> 'CANCELLED' AND starts_at >= ? AND starts_at < ?"
            + " GROUP BY day",
        rs -> {
          Date day = rs.getDate("day");
          perDay.put(day.toLocalDate(), rs.getLong("n"));
        },
        business.getTimeZone(),
        businessId,
        java.sql.Timestamp.from(start),
        java.sql.Timestamp.from(end));

    long confirmed = counts.getOrDefault(BookingStatus.CONFIRMED, 0L);
    long completed = counts.getOrDefault(BookingStatus.COMPLETED, 0L);
    long noShow = counts.getOrDefault(BookingStatus.NO_SHOW, 0L);
    long cancelled = counts.getOrDefault(BookingStatus.CANCELLED, 0L);
    long total = confirmed + completed + noShow + cancelled;
    return new StatsResponse(
        from,
        to,
        business.getTimeZone(),
        total,
        confirmed,
        completed,
        noShow,
        cancelled,
        rate(cancelled, total),
        rate(noShow, total),
        revenue,
        perDay.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList());
  }

  private static double rate(long part, long total) {
    return total == 0 ? 0.0 : Math.round(part * 10_000.0 / total) / 10_000.0;
  }

  private static Instant start(LocalDate date, Business business) {
    return date.atStartOfDay(business.zone()).toInstant();
  }

  private static void checkRange(LocalDate from, LocalDate to) {
    if (to.isBefore(from)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "'to' must not be before 'from'");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Range may span at most " + MAX_RANGE_DAYS + " days");
    }
  }
}
