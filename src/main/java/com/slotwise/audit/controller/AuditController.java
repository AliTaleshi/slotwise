package com.slotwise.audit.controller;

import com.slotwise.audit.dto.AuditDtos.AuditEntry;
import com.slotwise.audit.service.AuditQueryService;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.common.web.PageResponse;
import com.slotwise.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/businesses/{businessId}/audit-log")
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Audit")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AuditController {

  private final AuditQueryService service;

  public AuditController(AuditQueryService service) {
    this.service = service;
  }

  @Operation(summary = "Who changed what, with before/after values (newest first)")
  @GetMapping
  public PageResponse<AuditEntry> list(
      @PathVariable UUID businessId,
      @RequestParam(required = false) String entityType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return service.list(businessId, entityType, page, size, user);
  }
}
