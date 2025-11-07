package art.picsell.starter.redisson.streams.testapp

import art.picsell.starter.redisson.streams.annotation.RedisStreamHandler
import art.picsell.starter.redisson.streams.model.enum.DeliveryMode
import org.springframework.stereotype.Component

@Component
class OrderStreamHandlers(
    private val tracker: OrderProcessingTracker
) {

    @RedisStreamHandler(
        stream = "orders",
        group = "orders-group",
        mode = DeliveryMode.EXCLUSIVE
    )
    fun handleExclusive(event: OrderPlaced) {
        tracker.recordExclusive(event)
    }

    @RedisStreamHandler(
        stream = "notifications",
        mode = DeliveryMode.BROADCAST
    )
    fun handleBroadcast(event: PromotionAnnounced) {
        tracker.recordBroadcast(event)
    }
}
