package com.vivek.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Wires Redis session storage when {@code fkblitz.redis.enabled=true}.
 *
 * <p>{@code @EnableRedisHttpSession} takes explicit control of the session
 * repository, bypassing the {@code spring.session.store-type: none} default
 * set in application.yml (that only suppresses Spring Boot auto-config).</p>
 */
@Configuration
@ConditionalOnProperty(name = "fkblitz.redis.enabled", havingValue = "true")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisConfig {

  private final FkBlitzConfigProperties props;

  public RedisConfig(FkBlitzConfigProperties props) {
    this.props = props;
  }

  @Bean
  public LettuceConnectionFactory redisConnectionFactory() {
    FkBlitzConfigProperties.Redis redisCfg = props.getRedis();
    RedisStandaloneConfiguration cfg =
        new RedisStandaloneConfiguration(redisCfg.getHost(), redisCfg.getPort());
    cfg.setDatabase(redisCfg.getDatabase());
    if (redisCfg.getPassword() != null && !redisCfg.getPassword().isBlank()) {
      cfg.setPassword(redisCfg.getPassword());
    }
    return new LettuceConnectionFactory(cfg);
  }

  /**
   * Required by Spring Session's RedisIndexedSessionRepository.
   * RedisAutoConfiguration is globally excluded (application.yml) to prevent connection
   * attempts when Redis is disabled, so we must declare this bean explicitly here.
   */
  @Bean
  public RedisTemplate<Object, Object> redisTemplate() {
    RedisTemplate<Object, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory());
    return template;
  }

  /**
   * Required by ConfigPropagationConfig for Redis pub/sub config invalidation.
   */
  @Bean
  public StringRedisTemplate stringRedisTemplate() {
    return new StringRedisTemplate(redisConnectionFactory());
  }
}
