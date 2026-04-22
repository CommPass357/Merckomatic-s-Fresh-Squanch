package com.commsfreshsquanch.app

import android.app.Application
import com.commsfreshsquanch.app.data.CfsDatabase

class CfsApplication : Application() {
    val database by lazy { CfsDatabase.create(this) }
}
