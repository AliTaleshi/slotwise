package com.slotwise.booking.service;

import com.slotwise.booking.repository.HoldRepository;
import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Physically removes expired holds. Availability already ignores them before this runs. */
@Component
public class HoldCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(HoldCleanupJob.class);

  private final HoldRepository holds;
  private final Clock clock;

  public HoldCleanupJob(HoldRepository holds, Clock clock) {
    this.holds = holds;
    this.clock = clock;
  }

  /** Entry point for the scheduler; ShedLock cannot lock methods that return a primitive. */
  @Scheduled(fixedDelayString = "${slotwise.jobs.hold-cleanup-interval:PT1M}")
  @SchedulerLock(name = "cleanup-holds", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
  public void scheduled() {
    run();
  }

  /**
   * @return number of holds removed
   */
  public int run() {
    int removed = holds.deleteExpired(clock.instant());
    if (removed > 0) {
      log.info("Released {} expired holds", removed);
    }
    return removed;
  }
}
