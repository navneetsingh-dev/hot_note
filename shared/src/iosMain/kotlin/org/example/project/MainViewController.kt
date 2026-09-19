package org.example.project
import org.example.project.database.DatabaseDriverFactory

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App(driverFactory = DatabaseDriverFactory()) }