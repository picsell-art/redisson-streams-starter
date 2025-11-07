package art.picsell.starter.redisson.streams.testapp

import art.picsell.starter.redisson.streams.annotation.RedisStreamProducer
import org.springframework.stereotype.Service

@Service
class OrderStreamProducer {

    @RedisStreamProducer(stream = "orders", async = false)
    fun publishOrder(event: OrderPlaced) = event

    @RedisStreamProducer(stream = "notifications", async = true)
    fun publishPromotion(event: PromotionAnnounced) = event
}
