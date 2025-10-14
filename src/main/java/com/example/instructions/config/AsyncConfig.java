package com.example.instructions.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for asynchronous processing and retry mechanisms
 */
@Slf4j
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {

}
