package com.slotwise.booking.controller;

import com.slotwise.booking.dto.BookingDtos.CreateHoldRequest;
import com.slotwise.booking.dto.BookingDtos.HoldResponse;
import com.slotwise.booking.service.HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/holds")
@Tag(name = "Bookings")
public class HoldController {

  private final HoldService service;

  public HoldController(HoldService service) {
    this.service = service;
  }

  @Operation(
      summary = "Hold a slot for 10 minutes",
      description =
          "Returns 409 SLOT_UNAVAILABLE if the slot is taken or already held by someone else.")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public HoldResponse create(@Valid @RequestBody CreateHoldRequest req) {
    return service.create(req);
  }
}
