package app.khata.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All money is stored in paise. All calendar dates are stored as epoch days (LocalDate.toEpochDay). */

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "dues",
    foreignKeys = [ForeignKey(
        entity = Client::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("clientId")],
)
data class Due(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val title: String,
    val amount: Long,
    val invoiceDate: Long,
    val dueDate: Long?,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = Due::class,
        parentColumns = ["id"],
        childColumns = ["dueId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("dueId"), Index("smsKey")],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dueId: Long,
    val amount: Long,
    val receivedOn: Long,
    val note: String? = null,
    /** Set when the payment was recorded from an SMS suggestion, so that SMS isn't suggested again. */
    val smsKey: String? = null,
)

@Entity(tableName = "dismissed_sms")
data class DismissedSms(@PrimaryKey val smsKey: String)

data class DueRow(
    @Embedded val due: Due,
    val clientName: String,
    val clientPhone: String?,
    val paid: Long,
)

val DueRow.outstanding: Long get() = (due.amount - paid).coerceAtLeast(0)
val DueRow.isPaid: Boolean get() = paid >= due.amount
fun DueRow.isOverdue(today: Long): Boolean = !isPaid && (due.dueDate ?: Long.MAX_VALUE) < today

data class PaymentRow(
    @Embedded val payment: Payment,
    val dueTitle: String,
    val clientName: String,
    val clientId: Long,
)
