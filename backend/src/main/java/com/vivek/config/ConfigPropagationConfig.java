package com.vivek.config;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.loader.DbConfigLoader;
import com.vivek.sqlstorm.config.loader.RefreshableConfigLoader;
import com.vivek.sqlstorm.config.loader.RelationRowDbLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires Redis pub/sub for cross-node config invalidation.
 *
 * When any node's loader detects a config change (via polling), it publishes to
 * {@code fkblitz:config-changed}. All other nodes subscribe here and immediately
 * call {@code refresh()} — collapsing inter-node staleness from
 * {@code refreshIntervalSeconds} to sub-second.
 *
 * Degrades gracefully: if Redis is not configured, the beans here are not created
 * and each node falls back to pure polling.
 */
@Configuration
public class ConfigPropagationConfig {

    private static final Logger log = LoggerFactory.getLogger(ConfigPropagationConfig.class);

    /**
     * Subscribe to the config-changed channel.
     * On message receipt, immediately trigger a refresh on the custom-mapping loader.
     * Only created when a RedisConnectionFactory bean is present.
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer configChangeListener(
            RedisConnectionFactory connectionFactory,
            RefreshableConfigLoader<CustomRelationConfig> customMappingConfigLoader) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> {
                    log.info("Received config-changed signal from Redis — triggering immediate refresh");
                    customMappingConfigLoader.refresh();
                },
                new ChannelTopic(RelationRowDbLoader.REDIS_CHANNEL));

        log.info("Redis config-change listener registered on channel '{}'",
                RelationRowDbLoader.REDIS_CHANNEL);
        return container;
    }

    /**
     * Inject the StringRedisTemplate into whichever loader supports publishing.
     * Only wired when a StringRedisTemplate bean exists.
     */
    @Autowired(required = false)
    public void injectRedisIntoLoaders(
            StringRedisTemplate redisTemplate,
            RefreshableConfigLoader<CustomRelationConfig> customMappingConfigLoader) {

        if (customMappingConfigLoader instanceof RelationRowDbLoader loader) {
            loader.setRedisTemplate(redisTemplate);
            log.info("Redis pub/sub wired into RelationRowDbLoader");
        } else if (customMappingConfigLoader instanceof DbConfigLoader<?> loader) {
            ((DbConfigLoader<CustomRelationConfig>) loader).setRedisTemplate(redisTemplate);
            log.info("Redis pub/sub wired into DbConfigLoader");
        }
    }
}
