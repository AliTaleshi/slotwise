package com.slotwise.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

  private AuditDtos() {}

  public record AuditEntry(
      UUID id,
      UUID actorId,
      String action,
      String entityType,
      UUID entityId,
      JsonNode before,
      JsonNode after,
      Instant createdAt) {}
}
