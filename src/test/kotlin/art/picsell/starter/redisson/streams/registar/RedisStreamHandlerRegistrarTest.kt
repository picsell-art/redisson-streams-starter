package art.picsell.starter.redisson.streams.registar

import art.picsell.starter.redisson.streams.annotation.RedisStreamHandler
import art.picsell.starter.redisson.streams.model.enum.DeliveryMode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStreamCommands
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import kotlin.test.assertEquals

class RedisStreamHandlerRegistrarTest {

    private val container: StreamMessageListenerContainer<String, MapRecord<String, String, String>> = mockk(relaxed = true)
    private val mapper = ObjectMapper().registerKotlinModule()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val connectionFactory: RedisConnectionFactory = mockk()
    private val connection: RedisConnection = mockk(relaxed = true)
    private val streamCommands: RedisStreamCommands = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        every { connectionFactory.connection } returns connection
        every { connection.streamCommands() } returns streamCommands
        every { connection.close() } just Runs
        every { streamCommands.xGroupCreate(any(), any<String>(), any(), any()) } returns "OK"
    }

    @Test
    fun `exclusive handler deserializes matching events`() {
        lateinit var listener: StreamListener<String, MapRecord<String, String, String>>
        every { container.receiveAutoAck(any(), any(), any()) } answers {
            listener = thirdArg()
            mockk(relaxed = true)
        }
        every { container.start() } just Runs

        val bean = ExclusiveHandler()
        val registrar = registrar()

        registrar.postProcessAfterInitialization(bean, "exclusiveHandler")
        registrar.afterSingletonsInstantiated()

        val payload = mapper.writeValueAsString(TestEvent("42"))
        val record = MapRecord.create(
            "orders",
            mapOf("_class" to TestEvent::class.qualifiedName!!, "payload" to payload)
        )

        listener.onMessage(record)

        assertEquals(listOf(TestEvent("42")), bean.events)
        verify { container.receiveAutoAck(any(), any(), any()) }
        verify { streamCommands.xGroupCreate(any(), "order-workers", any(), true) }
        verify { container.start() }
    }

    @Test
    fun `broadcast handler uses receive without ensuring group`() {
        every { container.receive(any(), any()) } returns mockk(relaxed = true)
        every { container.start() } just Runs

        val bean = BroadcastHandler()
        val registrar = registrar()

        registrar.postProcessAfterInitialization(bean, "broadcastHandler")
        registrar.afterSingletonsInstantiated()

        verify(exactly = 1) { container.receive(any(), any()) }
        verify(exactly = 0) { streamCommands.xGroupCreate(any(), any<String>(), any(), any()) }
    }

    @Test
    fun `skips infrastructure beans when scanning`() {
        val beanFactory = mockk<ConfigurableListableBeanFactory>()
        val beanDefinition = RootBeanDefinition().apply { role = BeanDefinition.ROLE_INFRASTRUCTURE }
        every { beanFactory.containsBeanDefinition("exclusiveHandler") } returns true
        every { beanFactory.getBeanDefinition("exclusiveHandler") } returns beanDefinition

        val registrar = registrar()
        registrar.setBeanFactory(beanFactory)

        registrar.postProcessAfterInitialization(ExclusiveHandler(), "exclusiveHandler")

        assertEquals(0, registrar.handlerCount())
    }

    private fun registrar() =
        RedisStreamHandlerRegistrar(container, mapper, scope, connectionFactory)

    private fun RedisStreamHandlerRegistrar.handlerCount(): Int {
        val field = javaClass.getDeclaredField("handlers")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(this) as MutableList<Any>).size
    }

    data class TestEvent(val id: String)

    class ExclusiveHandler {
        val events = mutableListOf<TestEvent>()

        @RedisStreamHandler(
            stream = "orders",
            group = "order-workers",
            mode = DeliveryMode.EXCLUSIVE
        )
        fun handle(event: TestEvent) {
            events += event
        }
    }

    class BroadcastHandler {

        @RedisStreamHandler(
            stream = "broadcast",
            mode = DeliveryMode.BROADCAST
        )
        fun handle(event: TestEvent) = Unit
    }
}
