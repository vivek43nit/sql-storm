package com.vivek.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction.
 *
 * <ul>
 *   <li>When Redis is enabled ({@code fkblitz.redis.enabled=true}): Spring Boot's
 *       {@code RedisCacheAutoConfiguration} detects the {@link
 *       org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory}
 *       bean from {@link RedisConfig} and creates a {@code RedisCacheManager}
 *       automatically — no extra config needed here.</li>
 *   <li>When Redis is disabled: the {@link ConcurrentMapCacheManager} below acts as
 *       a simple in-process fallback.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

  /** Fallback in-memory cache — used only when no other CacheManager is present. */
  @Bean
  @ConditionalOnMissingBean(CacheManager.class)
  public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("fkblitz-metadata");
  }
}
