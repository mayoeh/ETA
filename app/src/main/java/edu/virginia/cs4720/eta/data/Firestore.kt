package edu.virginia.cs4720.eta.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Reads and writes users/{uid}/plans. */
object PlanRepository {
    private val db get() = FirebaseFirestore.getInstance()
    private fun plans(uid: String) = db.collection("users").document(uid).collection("plans")

    /** Live list of the user's plans; re-emits whenever Firestore changes. */
    fun observe(uid: String): Flow<List<Plan>> = callbackFlow {
        val reg = plans(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            val list = snap?.documents?.map { Plan.from(it.id, it.data ?: emptyMap()) } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun get(uid: String, id: String): Plan? {
        val snap = plans(uid).document(id).get().await()
        val data = snap.data ?: return null
        return Plan.from(snap.id, data)
    }

    /** Creates the plan if it has no id yet, otherwise overwrites it. Returns the id. */
    suspend fun save(uid: String, plan: Plan): String {
        val col = plans(uid)
        return if (plan.id.isBlank()) {
            col.add(plan.toMap()).await().id
        } else {
            col.document(plan.id).set(plan.toMap()).await()
            plan.id
        }
    }

    suspend fun delete(uid: String, id: String) {
        plans(uid).document(id).delete().await()
    }
}

/** Writes the public profile at users/{uid} (used later for friend search). */
object UserRepository {
    suspend fun createProfile(uid: String, displayName: String, email: String) {
        val profile = mapOf(
            "displayName" to displayName,
            "displayNameLower" to displayName.lowercase(),
            "email" to email,
            "emailLower" to email.lowercase(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        FirebaseFirestore.getInstance().collection("users").document(uid).set(profile).await()
    }
}
