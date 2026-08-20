package com.qweet.rider.data

import android.content.Context

/**
 * Persists which server-assigned delivery IDs the rider has explicitly accepted in this app
 * (tapped "Accept Order" on the inline card, or accepted the global new-order popup).
 *
 * BUG FIX: this used to live only in Compose `remember` state inside DashboardScreen/AppEntry.
 * That state is wiped the instant DashboardScreen leaves composition — switching to the
 * Wallet/Profile tab, pressing back to exit the app, or the process simply being killed in the
 * background (all normal, common things a rider does mid-delivery). But by the time a delivery
 * shows up assigned to this rider at all, it's already committed to them server-side (declining
 * is the only thing that actually unassigns it — see order-action.php). So losing this
 * in-memory flag made the app show the Accept/Decline card again for an order the rider had
 * already accepted, which looked — and functionally risked becoming — like the order vanished.
 *
 * Plain (unencrypted) SharedPreferences is fine here: these are just delivery IDs, nothing
 * sensitive. The set is always re-verified against the server's live orders() list (see
 * retainOnly), so a stale/tampered local value can never resurrect an order that isn't really
 * the rider's anymore — it can only ever suppress an unnecessary Accept/Decline prompt.
 */
class AcceptedOrderStore(context: Context) {
    private val prefs = context.getSharedPreferences("qweet_rider_order_state", Context.MODE_PRIVATE)

    fun getAll(): Set<Int> =
        prefs.getStringSet(KEY_ACCEPTED, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun add(deliveryId: Int) {
        save(getAll() + deliveryId)
    }

    /**
     * Call after every fresh orders() fetch with the set of delivery_ids the server currently
     * has assigned to this rider. Drops anything no longer in that list (delivered, declined,
     * or reassigned elsewhere) so this doesn't grow forever and never trusts a stale ID.
     */
    fun retainOnly(activeDeliveryIds: Set<Int>) {
        val current = getAll()
        val trimmed = current.intersect(activeDeliveryIds)
        if (trimmed != current) save(trimmed)
    }

    private fun save(ids: Set<Int>) {
        prefs.edit().putStringSet(KEY_ACCEPTED, ids.map { it.toString() }.toSet()).apply()
    }

    companion object {
        private const val KEY_ACCEPTED = "accepted_delivery_ids"
    }
}
