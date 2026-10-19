package com.slotwise.auth.security;

import com.slotwise.auth.entity.Role;
import java.util.UUID;

/** Authenticated principal extracted from a verified access token. */
public record AuthUser(UUID id, String email, Role role) {}
