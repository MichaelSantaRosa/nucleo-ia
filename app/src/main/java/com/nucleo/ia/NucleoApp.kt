package com.nucleo.ia

import android.app.Application
import com.nucleo.ia.core.IaEngine
import com.nucleo.ia.net.ConnectivityModule
import com.nucleo.ia.work.IdleTrainingService

class NucleoApp : Application() {
    lateinit var engine: IaEngine private set
    lateinit var connectivity: ConnectivityModule private set
    override fun onCreate() {
        super.onCreate()
        engine = IaEngine(this)
        connectivity = ConnectivityModule(this)
        engine.init()
        connectivity.startMonitoring()
        IdleTrainingService.scheduleNext(this)
    }
}