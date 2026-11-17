package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException

enum class OperationType {
    READ,
    WRITE,
    DELETE
}

fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String?
): String {
    val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    val uid = auth?.currentUser?.uid
    val code = (error as? FirebaseFirestoreException)?.code?.name ?: error::class.simpleName.orEmpty()
    Log.e(
        "CloudVault",
        "Firestore ${operationType.name} error at path=${path.orEmpty()} uid=${uid.orEmpty()} code=$code msg=${error.message}"
    )
    return when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permission denied for cloud vault sync. Please sign in again."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Cloud sync offline; changes saved locally and will sync when online."
        else -> error.message ?: "Cloud sync failed."
    }
}
