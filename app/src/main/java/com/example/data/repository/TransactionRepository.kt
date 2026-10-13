package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Transaction
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TransactionRepository(private val db: FirebaseFirestore) {

    // Secondary constructor that retrieves the named database ID from string resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeTransactions(): Flow<List<Transaction>> = callbackFlow {
        val uid = requireUserId()
        val path = "users/$uid/transactions"
        val registration = db.collection("users").document(uid).collection("transactions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(Transaction::class.java) }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeUserProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = requireUserId()
        val path = "users/$uid"
        val registration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(UserProfile::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createTransaction(
        title: String,
        amount: Double,
        type: String,
        category: String,
        date: String,
        note: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("transactions")
        val docRef = collectionRef.document()
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "title" to title.trim(),
            "amount" to amount,
            "type" to type,
            "category" to category,
            "date" to date,
            "note" to note.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        transactionId: String,
        title: String,
        amount: Double,
        type: String,
        category: String,
        date: String,
        note: String = ""
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("transactions").document(transactionId)
        val updatePayload = mapOf<String, Any>(
            "title" to title.trim(),
            "amount" to amount,
            "type" to type,
            "category" to category,
            "date" to date,
            "note" to note.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updatePayload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("transactions").document(transactionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCurrency(newCurrency: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "currency" to newCurrency,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "currency" to newCurrency,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addCustomCategory(newCategory: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val userProfile = snapshot.toObject(UserProfile::class.java)
                val currentList = userProfile?.customCategories ?: emptyList()
                if (!currentList.contains(newCategory)) {
                    val updatedList = currentList + newCategory
                    docRef.update(
                        mapOf(
                            "customCategories" to updatedList,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "currency" to "PKR",
                    "customCategories" to listOf(newCategory),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(
        displayName: String,
        email: String,
        currency: String = "PKR",
        monthlyBudget: Double = 0.0
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updatePayload = mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "currency" to currency,
                    "monthlyBudget" to monthlyBudget,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "currency" to currency,
                    "monthlyBudget" to monthlyBudget,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMonthlyBudget(monthlyBudget: Double): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "monthlyBudget" to monthlyBudget,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "currency" to "PKR",
                    "monthlyBudget" to monthlyBudget,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
