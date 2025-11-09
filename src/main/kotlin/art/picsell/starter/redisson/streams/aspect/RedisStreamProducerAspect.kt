package art.picsell.starter.redisson.streams.aspect

import art.picsell.starter.redisson.streams.annotation.RedisStreamProducer
import com.fasterxml.jackson.databind.ObjectMapper
import mu.two.KLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamAddArgs
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Role
import org.springframework.stereotype.Component

/**
 * Publishes method arguments as Redis Stream entries for functions annotated with
 * [RedisStreamProducer]. Serialization, type resolution, and stream selection are handled
 * transparently so that producers focus on business logic.
 */
@Aspect
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class RedisStreamProducerAspect(
    private val redisson: RedissonClient,
    private val mapper: ObjectMapper
) {

    /**
     * Wraps producer methods, serializes the first argument, and appends it to the target stream.
     *
     * @param joinPoint target invocation used to obtain arguments and proceed with execution.
     * @param ann metadata provided by [RedisStreamProducer].
     */
    @Around("@annotation(ann)")
    fun publish(joinPoint: ProceedingJoinPoint, ann: RedisStreamProducer): Any? {
        val args = joinPoint.args
        require(args.isNotEmpty()) {
            "Producer method ${joinPoint.signature.toShortString()} requires at least one argument"
        }

        val event = args[0] ?: error(
            "Producer method ${joinPoint.signature.toShortString()} received null payload"
        )

        val payload = mapper.writeValueAsString(event)
        val className = ann.type.ifBlank { event::class.java.name }
        val stream = redisson.getStream<String, String>(ann.stream, StringCodec.INSTANCE)
        val result = joinPoint.proceed()

        val streamArgs = StreamAddArgs.entries(mapOf(CLASS_FIELD to className, PAYLOAD_FIELD to payload))

        try {
            logger.debug { "Publishing Redis stream message to '${ann.stream}' class=$className async=${ann.async}" }
            if (ann.async) {
                stream.addAsync(streamArgs)
            } else {
                stream.add(streamArgs)
            }
        } catch (ex: Exception) {
            throw RuntimeException(
                "Failed to publish Redis Stream message to '${ann.stream}' from ${joinPoint.signature.toShortString()}: ${ex.message}",
                ex
            )
        }

        return result
    }

    companion object : KLogging() {
        private const val CLASS_FIELD = "_class"
        private const val PAYLOAD_FIELD = "payload"
    }
}
