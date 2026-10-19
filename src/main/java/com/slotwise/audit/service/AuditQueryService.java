package com.slotwise.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.audit.dto.AuditDtos.AuditEntry;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.web.PageResponse;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final BusinessAccess access;

  public AuditQueryService(JdbcTemplate jdbc, ObjectMapper mapper, BusinessAccess access) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditEntry> list(
      UUID businessId, String entityType, int page, int size, AuthUser owner) {
    access.requireOwned(businessId, owner);
    int pageSize = Math.max(1, Math.min(size, 100));
    int offset = Math.max(0, page) * pageSize;
    String filter = entityType == null ? "" : " AND entity_type = ?";
    Object[] countArgs =
        entityType == null ? new Object[] {businessId} : new Object[] {businessId, entityType};
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_log WHERE business_id = ?" + filter, Long.class, countArgs);
    Object[] args =
        entityType == null
            ? new Object[] {businessId, pageSize, offset}
            : new Object[] {businessId, entityType, pageSize, offset};
    List<AuditEntry> rows =
        jdbc.query(
            "SELECT id, actor_id, action, entity_type, entity_id, before::text, after::text, created_at"
                + " FROM audit_log WHERE business_id = ?"
                + filter
                + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
            (rs, i) -> map(rs),
            args);
    long totalCount = total == null ? 0 : total;
    return new PageResponse<>(
        rows, page, pageSize, totalCount, (int) Math.ceil(totalCount / (double) pageSize));
  }

  private AuditEntry map(ResultSet rs) throws java.sql.SQLException {
    try {
      return new AuditEntry(
          rs.getObject("id", UUID.class),
          rs.getObject("actor_id", UUID.class),
          rs.getString("action"),
          rs.getString("entity_type"),
          rs.getObject("entity_id", UUID.class),
          node(rs.getString("before")),
          node(rs.getString("after")),
          rs.getObject("created_at", OffsetDateTime.class).toInstant());
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode node(String json) throws java.io.IOException {
    return json == null ? null : mapper.readTree(json);
  }
}
