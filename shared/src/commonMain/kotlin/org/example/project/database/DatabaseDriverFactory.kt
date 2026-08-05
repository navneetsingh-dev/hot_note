package org.example.project.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): HotNoteDatabase {
    return HotNoteDatabase(driver = driverFactory.createDriver())
}
