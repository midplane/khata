// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class KhataDao {
    @Query("SELECT * FROM orgs ORDER BY name COLLATE NOCASE")
    abstract fun orgs(): Flow<List<Org>>

    @Insert
    abstract suspend fun insertOrg(org: Org): Long

    @Update
    abstract suspend fun updateOrg(org: Org)

    @Query("UPDATE dues SET orgId = NULL WHERE orgId = :orgId")
    protected abstract suspend fun clearOrgFromDues(orgId: Long)

    @Query("DELETE FROM orgs WHERE id = :orgId")
    protected abstract suspend fun deleteOrgRow(orgId: Long)

    /** Removes the org; its dues are kept and become "no org". */
    @Transaction
    open suspend fun deleteOrg(orgId: Long) {
        clearOrgFromDues(orgId)
        deleteOrgRow(orgId)
    }

    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE")
    abstract fun clients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun clientByName(name: String): Client?

    @Insert
    abstract suspend fun insertClient(client: Client): Long

    @Update
    abstract suspend fun updateClient(client: Client)

    @Delete
    abstract suspend fun deleteClient(client: Client)

    @Insert
    abstract suspend fun insertDue(due: Due): Long

    @Update
    abstract suspend fun updateDue(due: Due)

    @Query("DELETE FROM dues WHERE id = :id")
    abstract suspend fun deleteDue(id: Long)

    @Query(
        """
        SELECT d.*, c.name AS clientName, c.phone AS clientPhone, o.name AS orgName,
               COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.dueId = d.id), 0) AS paid
        FROM dues d JOIN clients c ON c.id = d.clientId
        LEFT JOIN orgs o ON o.id = d.orgId
        ORDER BY d.invoiceDate DESC, d.id DESC
        """
    )
    abstract fun dueRows(): Flow<List<DueRow>>

    @Query(
        """
        SELECT p.*, d.title AS dueTitle, c.name AS clientName, c.id AS clientId, d.orgId AS orgId
        FROM payments p
        JOIN dues d ON d.id = p.dueId
        JOIN clients c ON c.id = d.clientId
        ORDER BY p.receivedOn DESC, p.id DESC
        """
    )
    abstract fun paymentRows(): Flow<List<PaymentRow>>

    @Insert
    abstract suspend fun insertPayment(payment: Payment): Long

    @Query("DELETE FROM payments WHERE id = :id")
    abstract suspend fun deletePayment(id: Long)

    @Query("SELECT smsKey FROM dismissed_sms")
    abstract fun dismissedSmsKeys(): Flow<List<String>>

    @Query("SELECT smsKey FROM payments WHERE smsKey IS NOT NULL")
    abstract fun usedSmsKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun dismissSms(item: DismissedSms)

    /** Finds the client by name (case-insensitive) or creates it; fills in a missing phone number. */
    protected suspend fun resolveClient(clientName: String, phone: String?): Long {
        val existing = clientByName(clientName)
        return when {
            existing == null -> insertClient(Client(name = clientName, phone = phone))
            existing.phone.isNullOrBlank() && !phone.isNullOrBlank() -> {
                updateClient(existing.copy(phone = phone)); existing.id
            }
            else -> existing.id
        }
    }

    @Transaction
    open suspend fun addDue(clientName: String, phone: String?, due: Due): Long =
        insertDue(due.copy(clientId = resolveClient(clientName, phone)))

    /** Updates a due in place (payments stay attached); may move it to another or a new client. */
    @Transaction
    open suspend fun editDue(clientName: String, phone: String?, due: Due) =
        updateDue(due.copy(clientId = resolveClient(clientName, phone)))
}
