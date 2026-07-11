package com.freecritter.dispatch

import android.app.Application
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.data.db.DispatchDatabase

/**
 * Manual DI, deliberately simple (no Hilt in v1 — spec working style: no over-engineering).
 * One database, one repository, app-wide.
 */
class DispatchApp : Application() {
    val database: DispatchDatabase by lazy { DispatchDatabase.get(this) }
    val repository: DispatchRepository by lazy { DispatchRepository(database) }
}
