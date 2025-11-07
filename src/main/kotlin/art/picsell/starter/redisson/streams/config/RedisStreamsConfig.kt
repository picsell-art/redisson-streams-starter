package art.picsell.starter.redisson.streams.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import java.time.Duration

/**
 * Infrastructure configuration that exposes beans required by the starter.
 */
@Configuration
class RedisStreamsConfig {

    /**
     * Creates the Spring Data Redis listener container responsible for reading stream entries.
     * The container polls every second and is stopped automatically when the context closes.
     */
    @Bean(destroyMethod = "stop")
    fun streamMessageListenerContainer(
        connectionFactory: RedisConnectionFactory
    ): StreamMessageListenerContainer<String, MapRecord<String, String, String>> {
        val options = StreamMessageListenerContainer
            .StreamMessageListenerContainerOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(1))
            .build()

        return StreamMessageListenerContainer.create(connectionFactory, options)
    }

    /**
     * Coroutine scope backing asynchronous handler dispatch.
     */
    @Bean
    fun redisStreamsScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
