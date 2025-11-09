package art.picsell.starter.redisson.streams.registar

import art.picsell.starter.redisson.streams.annotation.RedisStreamHandler
import art.picsell.starter.redisson.streams.model.enum.DeliveryMode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.two.KLogging
import org.springframework.aop.support.AopUtils
import org.springframework.beans.BeansException
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Role
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure

/**
 * Scans Spring beans for [RedisStreamHandler] functions, configures appropriate
 * listeners, and dispatches payloads to Kotlin suspending or regular handlers.
 */
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class RedisStreamHandlerRegistrar(
    private val container: StreamMessageListenerContainer<String, MapRecord<String, String, String>>,
    private val mapper: ObjectMapper,
    private val scope: CoroutineScope,
    private val connectionFactory: RedisConnectionFactory,
) : BeanPostProcessor, SmartInitializingSingleton {

    /**
     * Captures resolved handler metadata used during container registration.
     */
    private data class HandlerDef(
        val bean: Any,
        val fn: KFunction<*>,
        val ann: RedisStreamHandler,
        val typeName: String,
        val paramClass: Class<*>
    )

    private val handlers = mutableListOf<HandlerDef>()

    /**
     * Detects handler annotations on beans that belong to the starter's base package.
     */
    @Throws(BeansException::class)
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        // 1) Берём таргет-класс, чтобы не упереться в AOP-прокси
        val targetClass = AopUtils.getTargetClass(bean)

        // 2) Фильтруем по пакету — сюда подойдут любые твои корневые пакеты
        val pkg = targetClass.packageName
        if (!pkg.startsWith("art.picsell")) {
            return bean
        }

        // 3) Только теперь лезем в Kotlin-рефлексию
        val kClass = targetClass.kotlin

        kClass.declaredMemberFunctions.forEach { fn ->
            val ann = fn.findAnnotation<RedisStreamHandler>() ?: return@forEach

            val param = fn.parameters.getOrNull(1)
                ?: error("Handler ${fn.name} in ${kClass.simpleName} must have exactly one argument")

            val paramClass = param.type.jvmErasure.java
            val typeName = ann.type.ifEmpty { param.type.jvmErasure.simpleName ?: fn.name }

            if (ann.mode == DeliveryMode.EXCLUSIVE && ann.group.isBlank()) {
                error("Handler ${fn.name} in ${kClass.simpleName} must define a non-blank group for EXCLUSIVE mode")
            }

            handlers += HandlerDef(
                bean = bean,
                fn = fn,
                ann = ann,
                typeName = typeName,
                paramClass = paramClass
            )

            logger.debug {
                "Discovered Redis stream handler ${kClass.simpleName}.${fn.name} for stream '${ann.stream}' " +
                    "mode=${ann.mode} group='${ann.group}' type=$typeName"
            }
        }

        return bean
    }

    /**
     * Ensures that the target consumer group exists before registering listeners.
     */
    private fun ensureGroup(stream: String, group: String) {
        val conn = connectionFactory.connection
        try {
            conn.streamCommands()
                .xGroupCreate(
                    stream.toByteArray(StandardCharsets.UTF_8),
                    group,
                    ReadOffset.from("0-0"),
                    true // MKSTREAM
                )
            logger.debug { "Ensured Redis stream group '$group' on stream '$stream'" }
        } catch (ex: Exception) {
            if (!isBusyGroup(ex)) {
                throw ex
            }
        } finally {
            conn.close()
        }
    }

    private fun isBusyGroup(ex: Exception): Boolean =
        when (ex) {
            is RedisSystemException -> ex.cause?.message?.contains("BUSYGROUP", ignoreCase = true) == true
            is DataAccessException -> ex.cause?.message?.contains("BUSYGROUP", ignoreCase = true) == true
            else -> ex.message?.contains("BUSYGROUP", ignoreCase = true) == true
        }

    /**
     * Registers every discovered handler within the listener container and starts it up.
     */
    override fun afterSingletonsInstantiated() {
        handlers.forEach { handler ->
            val stream = handler.ann.stream

            if (handler.ann.mode == DeliveryMode.EXCLUSIVE) {
                ensureGroup(stream, handler.ann.group)
            }

            val listener =
                StreamListener<String, MapRecord<String, String, String>> { msg: MapRecord<String, String, String> ->
                    scope.launch {
                        try {
                            val type = msg.value[TYPE_FIELD] ?: return@launch
                            if (type != handler.typeName) return@launch

                            val payload = msg.value[PAYLOAD_FIELD] ?: return@launch
                            @Suppress("UNCHECKED_CAST")
                            val paramClass = handler.paramClass as Class<Any>
                            val event = mapper.readValue(payload, paramClass)

                            invokeHandler(handler, event)
                        } catch (t: Throwable) {
                            logger.error(t) { "Failed to process Redis stream message for ${handler.fn.name}" }
                        }
                    }
                }

            when (handler.ann.mode) {
                DeliveryMode.EXCLUSIVE ->
                    container.receiveAutoAck(
                        Consumer.from(handler.ann.group, "${UUID.randomUUID()}"),
                        StreamOffset.create(stream, ReadOffset.lastConsumed()),
                        listener
                    )

                DeliveryMode.BROADCAST ->
                    container.receive(
                        StreamOffset.create(stream, ReadOffset.latest()),
                        listener
                    )
            }
        }

        container.start()
        logger.debug { "Redis stream listener container started with ${handlers.size} handlers" }
    }

    /**
     * Invokes handlers while respecting suspending functions.
     */
    private suspend fun invokeHandler(handler: HandlerDef, event: Any) {
        if (handler.fn.isSuspend) {
            handler.fn.callSuspend(handler.bean, event)
        } else {
            handler.fn.call(handler.bean, event)
        }
    }

    companion object : KLogging() {
        private const val TYPE_FIELD = "type"
        private const val PAYLOAD_FIELD = "payload"
    }
}
