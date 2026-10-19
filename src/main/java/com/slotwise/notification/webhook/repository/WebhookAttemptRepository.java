package com.slotwise.notification.webhook.repository;

import com.slotwise.notification.webhook.entity.WebhookAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookAttemptRepository extends JpaRepository<WebhookAttempt, UUID> {

  List<WebhookAttempt> findByDeliveryIdOrderByAttemptedAtAsc(UUID deliveryId);
}
