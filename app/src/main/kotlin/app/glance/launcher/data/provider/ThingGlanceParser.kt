package app.glance.launcher.data.provider

import app.glance.launcher.data.ThingCharge
import app.glance.launcher.data.ThingCounts
import app.glance.launcher.data.ThingEvent
import app.glance.launcher.data.ThingGlanceData
import app.glance.launcher.data.ThingItem
import app.glance.launcher.data.ThingMonth
import java.time.Instant
import org.json.JSONObject

/** thingGLANCE's launcher snapshot as the launcher's models (its `docs/launcher.md`). */
data class ThingGlanceSnapshot(
    val data: ThingGlanceData,
    val updatedAt: Instant?,
    val handledWrites: List<HandledWrite>,
    val meta: AppMeta = AppMeta.EMPTY,
) {
    /** thingGLANCE creates nothing for the launcher, so the ledger is the only way a write shows as landed. */
    val reflected: Set<String> get() = handledWrites.mapTo(HashSet()) { it.clientId }
}

/**
 * Turns the rows thingGLANCE's provider serves into the launcher's models. Its `meta` row
 * carries the same freshness fields as the other two apps', so [SnapshotParser.appMeta] reads
 * them; the rest is thingGLANCE's own snapshot (`docs/snapshot.md` there), field for field.
 * Pure over the row text, and a malformed row is skipped, never fatal.
 */
object ThingGlanceParser {

    fun parse(rows: Map<String, List<String>>): ThingGlanceSnapshot {
        val meta = rows["meta"]?.firstOrNull()?.let { obj(it) }
        val appMeta = SnapshotParser.appMeta(meta)
        val handled = handledWrites(meta)
        if (meta == null || meta.optBoolean("empty", false)) {
            return ThingGlanceSnapshot(ThingGlanceData(hasData = false, builtFor = null), appMeta.updatedAt, handled, appMeta)
        }
        val counts = meta.optJSONObject("counts")
        val burn = meta.optJSONObject("burn")
        val charges = meta.optJSONObject("charges")
        val months = ArrayList<ThingMonth>()
        charges?.optJSONArray("months")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val month = SnapshotParser.date(m.str("month")) ?: continue
                months.add(ThingMonth(month, m.finite("total") ?: 0.0))
            }
        }
        val verdicts = HashMap<String, String>()
        meta.optJSONObject("labels")?.optJSONObject("verdicts")?.let { v -> for (k in v.keys()) v.str(k)?.let { verdicts[k] = it } }
        val data = ThingGlanceData(
            hasData = true,
            builtFor = SnapshotParser.date(meta.str("today")),
            currency = meta.str("currency")?.takeIf { it.length == 3 } ?: "USD",
            locale = meta.str("locale"),
            counts = ThingCounts(
                things = counts?.optInt("things", 0) ?: 0,
                plans = counts?.optInt("subscriptions", 0) ?: 0,
                attention = counts?.optInt("attention", 0) ?: 0,
                urgent = counts?.optInt("urgent", 0) ?: 0,
                covered = counts?.optInt("covered", 0) ?: 0,
            ),
            monthlyPlans = burn?.finite("monthly") ?: 0.0,
            fundMonthly = burn?.finite("replacementMonthly") ?: 0.0,
            chargesDays = charges?.optInt("days", 30) ?: 30,
            chargesTotal = charges?.finite("total") ?: 0.0,
            chargesCount = charges?.optInt("count", 0) ?: 0,
            months = months,
            attention = rows.objects("attention").mapNotNull { event(it) },
            upcoming = rows.objects("upcoming").mapNotNull { event(it) },
            things = rows.objects("things").mapNotNull { thing(it) },
            charges = rows.objects("charges").mapNotNull { charge(it) },
            canSend = meta.optBoolean("canSend", false),
            verdictLabels = verdicts,
        )
        return ThingGlanceSnapshot(data, appMeta.updatedAt, handled, appMeta)
    }

    private fun event(o: JSONObject): ThingEvent? {
        val key = o.str("key") ?: return null
        val date = SnapshotParser.date(o.str("date")) ?: return null
        return ThingEvent(
            key = key,
            type = o.str("type") ?: "",
            date = date,
            severity = o.str("severity") ?: "later",
            title = o.str("title") ?: "",
            detail = o.str("detail") ?: "",
            amount = o.finite("amount"),
            state = o.str("state") ?: "open",
            link = o.str("link") ?: "",
        )
    }

    private fun thing(o: JSONObject): ThingItem? {
        val id = o.str("id") ?: return null
        return ThingItem(
            id = id,
            name = o.str("name") ?: "",
            category = o.str("category") ?: "",
            verdict = o.str("verdict") ?: "unknown",
            remaining = o.finite("remaining")?.toFloat()?.coerceIn(0f, 1f),
            lifeEnd = SnapshotParser.date(o.str("lifeEnd")),
            lifeStart = SnapshotParser.date(o.str("lifeStart")),
            coverageEnd = SnapshotParser.date(o.str("coverageEnd")),
            supportEnd = SnapshotParser.date(o.str("supportEnd")),
            lifeEndReason = o.str("lifeEndReason"),
        )
    }

    private fun charge(o: JSONObject): ThingCharge? {
        val id = o.str("subscriptionId") ?: return null
        val date = SnapshotParser.date(o.str("date")) ?: return null
        return ThingCharge(id, o.str("name") ?: "", date, o.finite("amount") ?: 0.0, o.str("link") ?: "")
    }

    private fun handledWrites(meta: JSONObject?): List<HandledWrite> {
        val arr = meta?.optJSONArray("handledWrites") ?: return emptyList()
        val out = ArrayList<HandledWrite>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(HandledWrite(o.str("clientId") ?: continue, o.str("action"), o.str("entityId")))
        }
        return out
    }

    private fun obj(json: String): JSONObject? = try { JSONObject(json) } catch (e: Exception) { null }

    private fun Map<String, List<String>>.objects(collection: String): List<JSONObject> = this[collection]?.mapNotNull { obj(it) } ?: emptyList()

    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.finite(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
}
