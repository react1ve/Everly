package app.android.cloud

import app.sync.RemoteBatch
import app.sync.SyncBackend
import app.sync.SyncRecord
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Cloud layout: users/{uid}/{habits|folders|entries}/{id}
 *   { ...fields, updatedAt: Long (client clock), deleted: Boolean, serverTime: server timestamp }
 * Security rules (firebase/firestore.rules) restrict every path to its owner and refuse writes
 * whose updatedAt is older than what's stored.
 * Firestore keeps its own offline cache and write queue, so pushes never wait for the network.
 */
class FirestoreSyncBackend(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : SyncBackend {

    private fun col(uid: String, kind: String) = db.collection("users").document(uid).collection(kind)

    override suspend fun push(uid: String, records: List<SyncRecord>) {
        records.forEach { r ->
            val doc = HashMap<String, Any?>(r.data)
            doc["updatedAt"] = r.updatedAt
            doc["deleted"] = r.deleted
            doc["serverTime"] = FieldValue.serverTimestamp()
            // Fire and forget: queued locally while offline; a rules rejection (older than cloud) is only logged.
            col(uid, r.kind).document(r.id).set(doc)
                .addOnFailureListener { e -> Log.w("Sync", "write rejected: ${r.key}", e) }
        }
    }

    override fun observe(uid: String): Flow<RemoteBatch> = callbackFlow {
        val registrations = mutableListOf<ListenerRegistration>()
        val pendingFirst = SyncRecord.KINDS.toMutableSet()
        val firstBatch = mutableListOf<SyncRecord>()

        SyncRecord.KINDS.forEach { kind ->
            registrations += col(uid, kind).addSnapshotListener(MetadataChanges.EXCLUDE) { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val changed = snap.documentChanges
                    .filter { it.type != DocumentChange.Type.REMOVED }
                    .mapNotNull { change -> change.document.let { d -> toRecord(kind, d.id, d.data) } }
                // Hold back until all three collections reported once, so the engine's "first snapshot"
                // really is everything the cloud has.
                if (kind in pendingFirst) {
                    pendingFirst -= kind
                    firstBatch += changed
                    if (pendingFirst.isEmpty()) trySend(RemoteBatch(firstBatch.toList(), snap.metadata.isFromCache))
                } else if (changed.isNotEmpty()) {
                    trySend(RemoteBatch(changed, snap.metadata.isFromCache))
                }
            }
        }
        awaitClose { registrations.forEach { it.remove() } }
    }

    override suspend fun deleteAll(uid: String) {
        SyncRecord.KINDS.forEach { kind ->
            while (true) {
                val page = col(uid, kind).limit(400).get().await()
                if (page.isEmpty) break
                val batch = db.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        db.collection("users").document(uid).delete().await()
    }

    private fun toRecord(kind: String, id: String, data: Map<String, Any?>): SyncRecord? {
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: return null
        val deleted = data["deleted"] as? Boolean ?: false
        val fields = data - setOf("updatedAt", "deleted", "serverTime")
        return SyncRecord(kind, id, updatedAt, deleted, fields)
    }
}
