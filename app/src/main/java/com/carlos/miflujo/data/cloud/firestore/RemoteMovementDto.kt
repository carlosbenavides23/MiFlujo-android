package com.carlos.miflujo.data.cloud.firestore

import com.google.firebase.Timestamp

data class RemoteMovementDto(
    var uuid: String? = null,
    var type: String? = null,
    var amountMinor: Long? = null,
    var currency: String? = null,
    var date: String? = null,
    var category: String? = null,
    var subcategory: String? = null,
    var detail: String? = null,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null,
    var deletedAt: Timestamp? = null,
    var schemaVersion: Int? = null,
)
