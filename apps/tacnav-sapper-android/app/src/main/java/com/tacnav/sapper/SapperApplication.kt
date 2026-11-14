package com.tacnav.sapper

import android.app.Application
import android.os.SystemClock
import com.tacnav.sapper.activation.HttpActivationApi
import com.tacnav.sapper.game.Clock
import com.tacnav.sapper.game.GameState
import com.tacnav.sapper.game.SapperController
import com.tacnav.sapper.game.Session
import com.tacnav.sapper.network.HttpMapApi
import com.tacnav.sapper.security.SapperRepository
import com.tacnav.sapper.security.SecureStateStore
import com.tacnav.sapper.session.Beeper
import com.tacnav.sapper.session.Compass
import com.tacnav.sapper.session.Locator
import java.util.UUID

class SapperApplication : Application() {
    private val store by lazy { SecureStateStore(this) }

    val compass by lazy { Compass(this) }
    val locator by lazy { Locator(this) }
    val beeper by lazy { Beeper() }

    val repository: SapperRepository by lazy {
        SapperRepository(store, HttpActivationApi())
    }

    val controller: SapperController by lazy {
        SapperController(
            store = store,
            api = HttpMapApi(activation = { repository.state.value?.activation }),
            signals = beeper,
            session = { repository.state.value?.let { Session(it.settings, it.context) } },
            clock = { Clock(SystemClock.elapsedRealtime(), System.currentTimeMillis()) },
            newKey = { UUID.randomUUID().toString() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        controller.load(if (repository.state.value == null) null else store.loadGame())
    }

    /** Every activation and every Reset connection starts with no counters or progress. */
    fun startFresh() {
        val fresh = GameState()
        if (repository.state.value != null) store.saveGame(fresh)
        controller.load(fresh)
    }
}
