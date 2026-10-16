package org.example.project.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(context: Context) {
    // Store applicationContext to prevent leaking MainActivity on rotation/destruction
    private val appContext: Context = context.applicationContext

    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = HotNoteDatabase.Schema,
            context = appContext,
            name = "HotNoteDatabase.db"
        )
    }
}