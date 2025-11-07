# Redisson Streams Starter

Spring Boot starter that wires Redis Stream consumers and producers on top of Redisson. Instead of hand-writing container registration and serialization glue, you annotate methods with `@RedisStreamHandler` and `@RedisStreamProducer` and let the starter do the rest.

## Features

- Auto-configuration triggered when both `RedissonClient` and `RedisConnectionFactory` beans exist.
- Declarative consumers via `@RedisStreamHandler` with support for **exclusive** (consumer-group) and **broadcast** delivery modes.
- Automatic creation of consumer groups (with `MKSTREAM`) for exclusive handlers.
- Coroutine-powered dispatch so suspending handlers are supported out of the box.
- Aspect-driven producers that serialize payloads with Jackson and append them synchronously or asynchronously.

## Quick start

1. **Add the dependency**
   ```kotlin
   dependencies {
       implementation("art.picsell.starter:redisson-streams-starter")
   }
   ```

2. **Expose Redisson & Redis beans** – the starter reuses your existing `RedissonClient`, `RedisConnectionFactory`, and `ObjectMapper`.
   ```kotlin
   @Configuration
   class RedisConfig {
       @Bean fun redissonClient(): RedissonClient = Redisson.create()
       // Spring Boot auto-configures RedisConnectionFactory for you.
   }
   ```

3. **Publish events declaratively**
   ```kotlin
   @Service
   class OrderProducer {

       @RedisStreamProducer(stream = "orders", async = true)
       fun publishOrder(event: OrderPlaced) = event
   }
   ```

4. **Handle events with annotations**
   ```kotlin
   @Component
   class OrderHandlers(
       private val tracker: OrderProcessingTracker
   ) {

       @RedisStreamHandler(
           stream = "orders",
           group = "orders-service",
           mode = DeliveryMode.EXCLUSIVE
       )
       suspend fun onOrder(event: OrderPlaced) {
           tracker.record(event)
       }

       @RedisStreamHandler(stream = "notifications", mode = DeliveryMode.BROADCAST)
       fun onPromotion(event: PromotionAnnounced) = tracker.recordPromotion(event)
   }
   ```

After the context starts, the starter spins up a `StreamMessageListenerContainer`, ensures consumer groups exist for exclusive handlers, and begins dispatching messages to your methods. Producer methods serialize the first argument (payload) to JSON and publish entries shaped as `{type, payload}`.

## Configuration notes

- **Type resolution** – when `type` is not set on the annotations, the starter defaults to the payload class simple name. Consumer and producer types must align to match messages.
- **Serialization** – Jackson `ObjectMapper` is used for both serialization and deserialization. Customize it globally using standard Spring Boot mechanisms.
- **Concurrency** – handler execution happens on a shared `CoroutineScope(SupervisorJob + Dispatchers.IO)`; override by providing your own bean named `redisStreamsScope`.
- **Backpressure** – the listener container polls every second. Tune this via a custom `StreamMessageListenerContainer` bean if needed.

## Testing locally

Spin up Redis (e.g., `docker run -p 6379:6379 redis:7`), start your Spring Boot application, and publish events using the annotated producer methods. The sample application under `src/applicationTest` demonstrates both exclusive and broadcast flows.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contributor guidelines and publishing instructions.
