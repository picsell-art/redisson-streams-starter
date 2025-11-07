package art.picsell.starter.redisson.streams.testapp

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.RedisConnectionFactory

@Configuration
@EnableConfigurationProperties(RedisProperties::class, TestStreamProperties::class)
class RedisTestConfig(
    private val redisProperties: RedisProperties
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val standalone = RedisStandaloneConfiguration(redisProperties.host, redisProperties.port).apply {
            if (!redisProperties.password.isNullOrBlank()) {
                password = RedisPassword.of(redisProperties.password)
            }
            redisProperties.username?.let { username = it }
            database = redisProperties.database
        }
        return LettuceConnectionFactory(standalone)
    }

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config().apply {
            val singleServer = useSingleServer()
                .setAddress("redis://${redisProperties.host}:${redisProperties.port}")
                .setDatabase(redisProperties.database)
            redisProperties.username?.let { singleServer.username = it }
            redisProperties.password?.takeIf { it.isNotBlank() }?.let { singleServer.password = it }
        }
        return Redisson.create(config)
    }
}
