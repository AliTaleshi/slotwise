package com.slotwise.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Append-only record of who changed what. Called inside the business transaction, so an audit entry
 * exists if and only if the change committed.
 */
@Service
public class AuditService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  public AuditService(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  public void record(
      UUID actorId,
      UUID businessId,
      String action,
      String entityType,
      UUID entityId,
      Object before,
      Object after) {
    jdbc.update(
        "INSERT INTO audit_log (id, actor_id, business_id, action, entity_type, entity_id, before, after, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
        UUID.randomUUID(),
        actorId,
        businessId,
        action,
        entityType,
        entityId,
        json(before),
        json(after),
        java.sql.Timestamp.from(clock.instant()));
  }

  private String json(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
