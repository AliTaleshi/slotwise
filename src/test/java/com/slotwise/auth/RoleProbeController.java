package com.slotwise.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints used to prove role gating works. */
@RestController
class RoleProbeController {

  @GetMapping("/v1/_probe/owner")
  @PreAuthorize("hasRole('OWNER')")
  String owner() {
    return "owner";
  }

  @GetMapping("/v1/_probe/staff")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  String staff() {
    return "staff";
  }

  @GetMapping("/v1/_probe/any")
  String any() {
    return "any";
  }
}
