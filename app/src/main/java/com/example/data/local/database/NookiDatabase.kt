package com.example.data.local.database

/**
 * Alias preserving compatibility with references to [NookiDatabase] while delegating
 * to the canonical [AppDatabase] class defined in the technical specification.
 */
typealias NookiDatabase = AppDatabase
