package art.picsell.starter.redisson.streams.aspect

import art.picsell.starter.redisson.streams.annotation.RedisStreamProducer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.junit.jupiter.api.Test
import org.redisson.api.RStream
import org.redisson.api.RedissonClient
import org.redisson.api.StreamMessageId
import org.redisson.client.codec.StringCodec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RedisStreamProducerAspectTest {

    private val redisson: RedissonClient = mockk()
    private val stream: RStream<String, String> = mockk()
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val aspect = RedisStreamProducerAspect(redisson, mapper)

    @Test
    fun `publish writes payload synchronously and returns join point result`() {
        val event = TestEvent("1")
        val ann = RedisStreamProducer(stream = "orders")
        val joinPoint = stubJoinPoint(event)

        every { redisson.getStream<String, String>("orders", StringCodec.INSTANCE) } returns stream
        every { stream.add(any()) } returns mockk<StreamMessageId>()

        val result = aspect.publish(joinPoint, ann)

        verify(exactly = 1) { stream.add(any()) }
        verify(exactly = 0) { stream.addAsync(any()) }
        assertEquals("result", result)
    }

    @Test
    fun `publish respects async flag`() {
        val event = TestEvent("1")
        val ann = RedisStreamProducer(stream = "orders", async = true)
        val joinPoint = stubJoinPoint(event)
        val future = mockk<org.redisson.api.RFuture<StreamMessageId>>()

        every { redisson.getStream<String, String>("orders", StringCodec.INSTANCE) } returns stream
        every { stream.addAsync(any()) } returns future

        aspect.publish(joinPoint, ann)

        verify(exactly = 1) { stream.addAsync(any()) }
        verify(exactly = 0) { stream.add(any()) }
    }

    @Test
    fun `publish requires payload argument`() {
        val ann = RedisStreamProducer(stream = "orders")
        val joinPoint = mockk<ProceedingJoinPoint> {
            every { args } returns emptyArray()
            every { signature } returns stubSignature()
        }

        assertFailsWith<IllegalArgumentException> { aspect.publish(joinPoint, ann) }
    }

    private fun stubJoinPoint(event: Any?): ProceedingJoinPoint =
        mockk {
            every { args } returns arrayOf(event)
            every { signature } returns stubSignature()
            every { proceed() } returns "result"
        }

    private fun stubSignature(): Signature =
        mockk {
            every { toShortString() } returns "Test.produce()"
        }

    private data class TestEvent(val id: String)
}
