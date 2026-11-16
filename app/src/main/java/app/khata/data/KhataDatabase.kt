package app.khata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Client::class, Due::class, Payment::class, DismissedSms::class],
    version = 1,
    exportSchema = false,
)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun dao(): KhataDao

    companion object {
        fun build(context: Context): KhataDatabase =
            Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db").build()
    }
}
