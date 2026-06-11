package com.carlos.miflujo.data.cloud.firestore

import com.carlos.miflujo.domain.sync.MovementRemoteSnapshot
import com.carlos.miflujo.domain.sync.RemoteMovementInput

interface CloudMovementRemoteDataSource {
    suspend fun fetchAll(uid: String): List<RemoteMovementInput>

    suspend fun upsertVisible(
        uid: String,
        movement: MovementRemoteSnapshot,
    )

    suspend fun upsertTombstone(
        uid: String,
        movement: MovementRemoteSnapshot,
    )
}
