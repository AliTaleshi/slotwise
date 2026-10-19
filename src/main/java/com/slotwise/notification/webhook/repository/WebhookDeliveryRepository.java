package com.slotwise.notification.webhook.repository;

import com.slotwise.notification.webhook.entity.WebhookDelivery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

  @Query(
      "select d.id from WebhookDelivery d where d.status = com.slotwise.notification.webhook.entity.WebhookDelivery.Status.PENDING"
          + " and d.nextAttemptAt <= :now order by d.nextAttemptAt")
  List<UUID> findDueIds(Instant now, Pageable limit);

  /**
   * Lease: pushes next_attempt_at forward so only one dispatcher (this instance's after-commit
   * trigger, the retry job, another instance) works on the delivery. True if we got the lease.
   */
  @Transactional
  @Modifying(clearAutomatically = true)
  @Query(
      "update WebhookDelivery d set d.nextAttemptAt = :leaseUntil where d.id = :id"
          + " and d.status = com.slotwise.notification.webhook.entity.WebhookDelivery.Status.PENDING"
          + " and d.nextAttemptAt <= :now")
  int lease(UUID id, Instant now, Instant leaseUntil);

  @Transactional
  @Modifying(clearAutomatically = true)
  @Query(
      "update WebhookDelivery d set d.status = :status, d.attempts = :attempts,"
          + " d.nextAttemptAt = :next, d.deliveredAt = :deliveredAt where d.id = :id")
  int finishAttempt(
      UUID id,
      com.slotwise.notification.webhook.entity.WebhookDelivery.Status status,
      int attempts,
      Instant next,
      Instant deliveredAt);

  List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId, Pageable limit);
}
