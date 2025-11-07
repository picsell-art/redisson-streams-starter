package art.picsell.starter.redisson.streams.config

import art.picsell.starter.redisson.streams.aspect.RedisStreamProducerAspect
import art.picsell.starter.redisson.streams.registar.RedisStreamHandlerRegistrar
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * Auto-configuration entry point that wires the Redis Stream infrastructure when both
 * Redisson and Spring Data Redis are on the classpath.
 *
 * It sets up listener container beans, handler registration, and the producer aspect so that
 * applications can declare handlers/producers declaratively.
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient::class, RedisConnectionFactory::class)
@Import(
    RedisStreamsConfig::class,
    RedisStreamHandlerRegistrar::class,
    RedisStreamProducerAspect::class
)
class RedisStreamsAutoConfiguration
