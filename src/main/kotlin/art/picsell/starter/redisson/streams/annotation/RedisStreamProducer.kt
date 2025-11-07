package art.picsell.starter.redisson.streams.annotation

/**
 * Marks a method whose arguments should be published to a Redis Stream once it completes.
 *
 * The first argument is treated as the payload and will be serialized with Jackson into the
 * default entry structure used by the starter.
 *
 * @property stream Redis Stream key that will receive produced events.
 * @property async When `true`, events are appended via Redisson's async API.
 * @property type Optional logical event type. Defaults to the payload class simple name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RedisStreamProducer(
    val stream: String,
    val async: Boolean = false,
    val type: String = ""
)
