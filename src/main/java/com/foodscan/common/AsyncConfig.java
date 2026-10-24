package com.foodscan.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async; with spring.threads.virtual.enabled each task runs on a virtual thread. */
@Configuration
@EnableAsync
class AsyncConfig {
}
