package com.rotatsulabs.specty

import android.app.Application
import android.content.Context
import com.rotatsulabs.specty.ui.AppLanguage

class SpectyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
