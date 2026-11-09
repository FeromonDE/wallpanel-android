/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.wallpanel.app

import android.R.attr
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Process.myPid
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import androidx.multidex.MultiDex
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication
import timber.log.Timber
import xyz.wallpanel.app.di.DaggerApplicationComponent
import xyz.wallpanel.app.utils.WallpanelReleaseTree
import xyz.wallpanel.app.utils.LauncherShortcuts
import xyz.wallpanel.app.utils.WallpanelDebugTree


class WallPanel : DaggerApplication() {


    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerApplicationComponent.builder().create(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Gives clickable links to the issue in the Android Studio Logcat
            Timber.plant(WallpanelDebugTree())
            // Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(WallpanelReleaseTree())
            // Timber.plant(CrashlyticsTree())
        }
        strictMode()
        LauncherShortcuts.createShortcuts(this)
    }

    private fun strictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    // ART handles garbage collection on memory pressure. Forcing full GC
    // from Activity callbacks causes visible stalls on Android 7 tablets.
}
