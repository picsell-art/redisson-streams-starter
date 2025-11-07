package art.picsell.starter.redisson.streams.testapp

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "testapp.streams")
data class TestStreamProperties(
    val orders: String,
    val notifications: String,
    val ordersGroup: String
)
