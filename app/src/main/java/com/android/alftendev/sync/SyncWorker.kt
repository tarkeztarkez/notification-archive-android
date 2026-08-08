package com.android.alftendev.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.alftendev.MyApplication
import com.android.alftendev.models.SyncEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!SyncPreferences.isEnabled()) return@withContext Result.success()
        val url = SyncPreferences.serverUrl()
        val token = SyncPreferences.token(applicationContext)
        if (url.isBlank() || token.isBlank()) return@withContext failAll("Sync server URL or API token is missing")

        val box = MyApplication.syncEvents
        val events = box.all.asSequence()
            .filter { it.syncState == SyncEvent.STATE_PENDING || it.syncState == SyncEvent.STATE_FAILED || it.syncState == SyncEvent.STATE_SYNCING }
            .sortedBy { it.capturedAt }.take(100).toList()
        if (events.isEmpty()) return@withContext Result.success()

        events.forEach {
            it.syncState = SyncEvent.STATE_SYNCING
            it.syncAttempts += 1
            it.lastSyncError = null
        }
        box.put(events)

        try {
            val payload = JSONObject().put("events", JSONArray(events.map(::toJson))).toString()
            val request = Request.Builder()
                .url("$url/api/v1/notifications/batch")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val permanent = response.code == 401 || response.code == 403 || response.code == 422
                    markFailed(events, "Server returned HTTP ${response.code}")
                    return@withContext if (permanent) Result.failure() else Result.retry()
                }
                val acknowledged = JSONObject(response.body?.string().orEmpty())
                    .getJSONArray("acknowledgements")
                val acceptedIds = (0 until acknowledged.length()).mapNotNull { index ->
                    acknowledged.getJSONObject(index).takeIf { it.optBoolean("accepted") }?.getString("event_id")
                }.toSet()
                val now = System.currentTimeMillis()
                events.forEach {
                    if (it.eventId in acceptedIds) {
                        it.syncState = SyncEvent.STATE_SYNCED
                        it.syncedAt = now
                        it.lastSyncError = null
                    } else {
                        it.syncState = SyncEvent.STATE_FAILED
                        it.lastSyncError = "Server did not acknowledge event"
                    }
                }
                box.put(events)
                MyApplication.sharedPref.edit().putLong("sync_last_success", now)
                    .remove("sync_last_error").apply()
                applyLocalRetention(now)
                if (box.all.any { it.syncState == SyncEvent.STATE_PENDING || it.syncState == SyncEvent.STATE_FAILED }) {
                    SyncScheduler.enqueue(applicationContext)
                }
                Result.success()
            }
        } catch (error: Exception) {
            markFailed(events, error.javaClass.simpleName)
            Result.retry()
        }
    }

    private fun failAll(message: String): Result {
        MyApplication.sharedPref.edit().putString("sync_last_error", message).apply()
        return Result.failure()
    }

    private fun markFailed(events: List<SyncEvent>, message: String) {
        events.forEach { it.syncState = SyncEvent.STATE_FAILED; it.lastSyncError = message }
        MyApplication.syncEvents.put(events)
        MyApplication.sharedPref.edit().putString("sync_last_error", message).apply()
    }

    private fun applyLocalRetention(now: Long) {
        val days = MyApplication.sharedPref.getString(SyncPreferences.KEY_RETENTION_DAYS, "0")
            ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (days == 0) return
        val cutoff = now - TimeUnit.DAYS.toMillis(days.toLong())
        val expired = MyApplication.syncEvents.all.filter {
            it.syncState == SyncEvent.STATE_SYNCED && (it.syncedAt ?: Long.MAX_VALUE) < cutoff
        }
        MyApplication.syncEvents.remove(expired)
    }

    private fun iso(value: Long?) = value?.let { Instant.ofEpochMilli(it).toString() }

    private fun toJson(event: SyncEvent) = JSONObject()
        .put("event_id", event.eventId).put("device_id", event.deviceId)
        .put("device_name", event.deviceName).put("package_name", event.packageName)
        .put("app_name", event.appName).put("notification_key", event.notificationKey)
        .put("notification_id", event.notificationId).put("notification_tag", event.notificationTag)
        .put("title", event.title).put("body", event.body).put("expanded_text", event.expandedText)
        .put("subtext", event.subtext).put("category", event.category).put("channel_id", event.channelId)
        .put("group_key", event.groupKey).put("posted_at", iso(event.postedAt))
        .put("captured_at", iso(event.capturedAt)).put("removed_at", iso(event.removedAt))
        .put("event_type", event.eventType).put("raw_metadata", JSONObject(event.rawMetadataJson))
        .put("payload_version", event.payloadVersion)
}
