package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TransactionRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createTransaction_validPayload_createsDocumentAndReturnsId(): Unit = runBlocking {
        val aliceEmail = "alice_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        signInTestUser(aliceEmail)
        val repository = TransactionRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTransaction(
                title = "Monthly Salary",
                amount = 4500.0,
                type = "INCOME",
                category = "Salary",
                date = "2026-10-12",
                note = "Tech company stipend"
            )
        }
        assertTrue(createResult.isSuccess)
        val transactionId = createResult.getOrThrow()
        assertTrue(transactionId.isNotEmpty())
    }

    @Test
    fun observeTransactions_authenticatedOwner_emitsRealtimeUpdates(): Unit = runBlocking {
        val aliceEmail = "alice_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        signInTestUser(aliceEmail)
        val repository = TransactionRepository(firestore)
        val transactionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTransaction(
                title = "Grocery Shopping",
                amount = 85.50,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-12"
            ).getOrThrow()
        }

        val emittedList = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTransactions().first { list -> list.any { it.id == transactionId } }
        }
        assertTrue(emittedList.any { it.id == transactionId })
    }

    @Test
    fun deleteTransaction_authenticatedOwner_succeeds(): Unit = runBlocking {
        val aliceEmail = "alice_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        signInTestUser(aliceEmail)
        val repository = TransactionRepository(firestore)
        val transactionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTransaction(
                title = "Coffee",
                amount = 4.50,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-12"
            ).getOrThrow()
        }

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteTransaction(transactionId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceTransactions(): Unit = runBlocking {
        val aliceEmail = "alice_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        val bobEmail = "bob_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = TransactionRepository(firestore)
        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTransaction(
                title = "Alice Secret Expense",
                amount = 100.0,
                type = "EXPENSE",
                category = "Other",
                date = "2026-10-12"
            ).getOrThrow()
        }

        signInTestUser(bobEmail)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("transactions").document(txId).get().await()
            }
            fail("Expected FirebaseFirestoreException for cross-user read")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun updateTransaction_authenticatedOwner_succeeds(): Unit = runBlocking {
        val aliceEmail = "alice_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        signInTestUser(aliceEmail)
        val repository = TransactionRepository(firestore)
        val transactionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTransaction(
                title = "Dinner",
                amount = 25.0,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-12"
            ).getOrThrow()
        }

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateTransaction(
                transactionId = transactionId,
                title = "Fancy Dinner",
                amount = 45.0,
                type = "EXPENSE",
                category = "Food",
                date = "2026-10-12",
                note = "With friends"
            )
        }
        assertTrue(updateResult.isSuccess)
    }

    @Test
    fun updateCurrency_authenticatedOwner_persists(): Unit = runBlocking {
        val aliceEmail = "alice_${java.util.UUID.randomUUID().toString().take(8)}@hisaab.test"
        signInTestUser(aliceEmail)
        val repository = TransactionRepository(firestore)
        val currencyResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateCurrency("USD")
        }
        assertTrue(currencyResult.isSuccess)
    }

    @Test
    fun unauthenticatedAccess_fails(): Unit = runBlocking {
        auth.signOut()
        val repository = TransactionRepository(firestore)
        try {
            repository.requireUserId()
            fail("Expected IllegalStateException for unauthenticated user")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("User must be signed in") == true)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
