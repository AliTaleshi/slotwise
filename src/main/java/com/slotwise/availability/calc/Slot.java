package com.slotwise.availability.calc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A bookable start time and the staff members who are free for it. */
public record Slot(Instant start, Instant end, List<UUID> staffIds) {}
