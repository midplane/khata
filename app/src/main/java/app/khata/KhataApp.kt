package app.khata

import android.app.Application
import app.khata.data.KhataDatabase

class KhataApp : Application() {
    val db: KhataDatabase by lazy { KhataDatabase.build(this) }
}
