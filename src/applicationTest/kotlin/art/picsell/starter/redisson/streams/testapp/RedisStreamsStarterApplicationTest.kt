package art.picsell.starter.redisson.streams.testapp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.redisson.api.RedissonClient
import org.redisson.api.StreamMessageId
import org.redisson.api.stream.StreamAddArgs
import org.redisson.client.codec.StringCodec
import java.time.Duration
import java.util.UUID
import kotlin.test.assertTrue

@SpringBootTest(classes = [TestApplication::class])
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisStreamsStarterApplicationTest @Autowired constructor(
    private val producer: OrderStreamProducer,
    private val tracker: OrderProcessingTracker,
    private val redisson: RedissonClient,
    private val mapper: ObjectMapper,
    private val streamProperties: TestStreamProperties
) {

    @BeforeEach
    fun setup() {
        tracker.reset()
        purgeStream(streamProperties.orders)
        purgeStream(streamProperties.notifications)
    }

    @Test
    fun `producer persists order payload with metadata`() {
        val event = OrderPlaced(orderId = UUID.randomUUID().toString(), amount = 3000)

        producer.publishOrder(event)

        assertTrue(
            waitForCondition { streamContainsEvent(streamProperties.orders, event, OrderPlaced::class.java) },
            "Orders stream does not contain payload produced by @RedisStreamProducer"
        )
    }

    @Test
    fun `async producer eventually persists promotion payload`() {
        val event = PromotionAnnounced(code = "SPRING", message = "Spring sale")

        producer.publishPromotion(event)

        assertTrue(
            waitForCondition(Duration.ofSeconds(10)) {
                streamContainsEvent(streamProperties.notifications, event, PromotionAnnounced::class.java)
            },
            "Notifications stream never received async payload"
        )
    }

    @Test
    fun `exclusive consumer processes message inserted externally`() {
        val event = OrderPlaced(orderId = UUID.randomUUID().toString(), amount = 999)

        appendRawEvent(streamProperties.orders, event)

        assertTrue(
            waitForCondition {
                tracker.exclusiveEvents().contains(event)
            },
            "Exclusive handler did not consume externally inserted order $event"
        )
    }

    @Test
    fun `broadcast consumer processes message inserted externally`() {
        val event = PromotionAnnounced(code = "VIP", message = "Private sale")

        appendRawEvent(streamProperties.notifications, event)

        assertTrue(
            waitForCondition {
                tracker.broadcastEvents().contains(event)
            },
            "Broadcast handler did not consume externally inserted promotion $event"
        )
    }

    @Test
    fun `orders flow writes to stream and delivers to consumer`() {
        val event = OrderPlaced(orderId = UUID.randomUUID().toString(), amount = 4500)

        producer.publishOrder(event)

        assertTrue(
            waitForCondition { tracker.exclusiveEvents().contains(event) },
            "Exclusive handler did not process order $event"
        )

        assertTrue(
            waitForCondition {
                streamContainsEvent(streamProperties.orders, event, OrderPlaced::class.java)
            },
            "Orders stream is missing payload after successful handler invocation"
        )
    }

    @Test
    fun `promotion flow writes to stream and delivers to consumer`() {
        val event = PromotionAnnounced(code = "BF", message = "Black Friday deals")

        producer.publishPromotion(event)

        assertTrue(
            waitForCondition(Duration.ofSeconds(10)) { tracker.broadcastEvents().contains(event) },
            "Broadcast handler did not process promotion $event"
        )

        assertTrue(
            waitForCondition(Duration.ofSeconds(10)) {
                streamContainsEvent(streamProperties.notifications, event, PromotionAnnounced::class.java)
            },
            "Notifications stream is missing payload after broadcast delivery"
        )
    }

    private fun waitForCondition(timeout: Duration = Duration.ofSeconds(5), condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun purgeStream(streamName: String) {
        val stream = stream(streamName)
        val ids = stream.range(StreamMessageId.MIN, StreamMessageId.MAX).keys
        if (ids.isNotEmpty()) {
            stream.remove(*ids.toTypedArray())
        }
    }

    private fun stream(streamName: String) =
        redisson.getStream<String, String>(streamName, StringCodec.INSTANCE)

    private fun <T : Any> streamContainsEvent(
        streamName: String,
        expected: T,
        eventClass: Class<T>,
        expectedType: String = eventClass.simpleName
    ): Boolean {
        val typeToMatch = expectedType
        val entries = stream(streamName).range(StreamMessageId.MIN, StreamMessageId.MAX).values
        if (entries.isEmpty()) return false

        return entries.any { entry ->
            val typeMatches = entry[TYPE_FIELD] == typeToMatch
            val payloadMatches = entry[PAYLOAD_FIELD]
                ?.let { runCatching { mapper.readValue(it, eventClass) }.getOrNull() }
                ?.equals(expected) == true
            typeMatches && payloadMatches
        }
    }

    private fun <T : Any> appendRawEvent(streamName: String, event: T) {
        val type = requireNotNull(event::class.simpleName) {
            "Cannot derive payload type for ${event::class}"
        }
        val payload = mapper.writeValueAsString(event)
        stream(streamName).add(
            StreamAddArgs.entries(
                mapOf(TYPE_FIELD to type, PAYLOAD_FIELD to payload)
            )
        )
    }

    companion object {
        private const val TYPE_FIELD = "type"
        private const val PAYLOAD_FIELD = "payload"

        @Container
        private val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.4.1-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            if (!redis.isRunning) {
                redis.start()
            }
            println("Redis Testcontainer running at ${redis.host}:${redis.firstMappedPort}")
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }
}
