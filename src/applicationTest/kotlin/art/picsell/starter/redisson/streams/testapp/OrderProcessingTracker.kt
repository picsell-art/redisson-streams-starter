package art.picsell.starter.redisson.streams.testapp

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
class OrderProcessingTracker {
    private val exclusiveEvents = CopyOnWriteArrayList<OrderPlaced>()
    private val broadcastEvents = CopyOnWriteArrayList<PromotionAnnounced>()

    fun recordExclusive(event: OrderPlaced) {
        exclusiveEvents += event
    }

    fun recordBroadcast(event: PromotionAnnounced) {
        broadcastEvents += event
    }

    fun exclusiveEvents(): List<OrderPlaced> = exclusiveEvents.toList()
    fun broadcastEvents(): List<PromotionAnnounced> = broadcastEvents.toList()

    fun reset() {
        exclusiveEvents.clear()
        broadcastEvents.clear()
    }
}
