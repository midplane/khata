package app.khata.data

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

    @Query("DELETE FROM dues WHERE id = :id")
    abstract suspend fun deleteDue(id: Long)

    @Query(
        """
        SELECT d.*, c.name AS clientName, c.phone AS clientPhone,
               COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.dueId = d.id), 0) AS paid
        FROM dues d JOIN clients c ON c.id = d.clientId
        ORDER BY d.invoiceDate DESC, d.id DESC
        """
    )
    abstract fun dueRows(): Flow<List<DueRow>>

    @Query(
        """
        SELECT p.*, d.title AS dueTitle, c.name AS clientName, c.id AS clientId
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

    /** Finds the client by name (case-insensitive) or creates it, then adds the due. */
    @Transaction
    open suspend fun addDue(clientName: String, phone: String?, due: Due): Long {
        val existing = clientByName(clientName)
        val clientId = when {
            existing == null -> insertClient(Client(name = clientName, phone = phone))
            existing.phone.isNullOrBlank() && !phone.isNullOrBlank() -> {
                updateClient(existing.copy(phone = phone)); existing.id
            }
            else -> existing.id
        }
        return insertDue(due.copy(clientId = clientId))
    }
}
