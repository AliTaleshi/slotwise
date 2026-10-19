package com.slotwise.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Post-commit side effects (emails, webhooks) run off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {}
