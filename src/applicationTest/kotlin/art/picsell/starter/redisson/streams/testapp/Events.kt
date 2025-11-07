package art.picsell.starter.redisson.streams.testapp

data class OrderPlaced(
    val orderId: String,
    val amount: Long
)

data class PromotionAnnounced(
    val code: String,
    val message: String
)
