package com.carlos.miflujo.domain.model

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    PENDING_DELETE,
    SYNC_ERROR,
}
