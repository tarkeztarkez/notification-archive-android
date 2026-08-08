package com.android.alftendev.models

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

@Entity
data class SyncEvent(
    @Id var id: Long = 0,
    @Unique @Index var eventId: String = "",
    var deviceId: String = "",
    var deviceName: String = "",
    var packageName: String = "",
    var appName: String = "",
    var notificationKey: String? = null,
    var notificationId: Int = 0,
    var notificationTag: String? = null,
    var title: String? = null,
    var body: String? = null,
    var expandedText: String? = null,
    var subtext: String? = null,
    var category: String? = null,
    var channelId: String? = null,
    var groupKey: String? = null,
    var postedAt: Long? = null,
    var capturedAt: Long = System.currentTimeMillis(),
    var removedAt: Long? = null,
    var eventType: String = "posted",
    var rawMetadataJson: String = "{}",
    var syncState: String = STATE_PENDING,
    var syncedAt: Long? = null,
    var syncAttempts: Int = 0,
    var lastSyncError: String? = null,
    var payloadVersion: Int = 1
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_SYNCING = "syncing"
        const val STATE_SYNCED = "synced"
        const val STATE_FAILED = "failed"
    }
}

