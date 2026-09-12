package com.emre.bilbakalim.arsiv

import android.app.Application
import com.emre.bilbakalim.arsiv.data.AppDatabase
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.Repo

class ArsivApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repo: Repo
        private set

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        repo = Repo(database.questionDao())
        prefs = Prefs(this)
    }

    companion object {
        lateinit var instance: ArsivApp
            private set
    }
}
