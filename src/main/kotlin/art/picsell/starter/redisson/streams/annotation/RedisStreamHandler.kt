package art.picsell.starter.redisson.streams.annotation

import art.picsell.starter.redisson.streams.model.enum.DeliveryMode

/**
 * Marks a method as a Redis Stream handler that will be auto-registered by the starter.
 *
 * The annotated method must accept exactly one argument that represents the event payload.
 * Depending on the delivery [mode], the handler can either join a consumer group or receive
 * broadcasted messages.
 *
 * @property stream Redis Stream key to subscribe to.
 * @property group Consumer group name used with [DeliveryMode.EXCLUSIVE].
 * @property mode Delivery strategy that controls listener registration.
 * @property type Optional logical event type. Defaults to the handler parameter's fully qualified name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RedisStreamHandler(
    val stream: String,
    val group: String = "",
    val mode: DeliveryMode = DeliveryMode.EXCLUSIVE,
    val type: String = ""
)
