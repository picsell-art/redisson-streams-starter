package art.picsell.starter.redisson.streams.model.enum

/**
 * Delivery semantics supported by the starter.
 */
enum class DeliveryMode {
    /**
     * Messages are consumed via Redis consumer groups, guaranteeing single processing per group.
     */
    EXCLUSIVE,

    /**
     * Every registered handler receives each message using fan-out semantics.
     */
    BROADCAST
}
