package com.slotwise.notification.webhook.repository;

import com.slotwise.notification.webhook.entity.WebhookEndpoint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

  List<WebhookEndpoint> findByBusinessIdAndActiveTrueOrderByCreatedAtAsc(UUID businessId);
}
