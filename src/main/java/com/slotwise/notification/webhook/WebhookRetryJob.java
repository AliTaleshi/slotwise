package com.slotwise.notification.webhook;

import com.slotwise.notification.webhook.repository.WebhookDeliveryRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries pending deliveries whose backoff has elapsed (also recovers anything left by a crash).
 */
@Component
public class WebhookRetryJob {

  private final WebhookDeliveryRepository deliveries;
  private final WebhookDispatcher dispatcher;
  private final Clock clock;

  public WebhookRetryJob(
      WebhookDeliveryRepository deliveries, WebhookDispatcher dispatcher, Clock clock) {
    this.deliveries = deliveries;
    this.dispatcher = dispatcher;
    this.clock = clock;
  }

  /** Entry point for the scheduler; ShedLock cannot lock methods that return a primitive. */
  @Scheduled(fixedDelayString = "${slotwise.jobs.webhook-retry-interval:PT30S}")
  @SchedulerLock(name = "retry-webhooks", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  public void scheduled() {
    run();
  }

  /**
   * @return number of deliveries attempted
   */
  public int run() {
    List<UUID> due = deliveries.findDueIds(clock.instant(), Pageable.ofSize(100));
    due.forEach(dispatcher::deliver);
    return due.size();
  }
}
