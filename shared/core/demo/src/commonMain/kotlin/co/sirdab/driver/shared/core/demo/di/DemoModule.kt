package co.sirdab.driver.shared.core.demo.di

import co.sirdab.driver.shared.core.demo.DemoClock
import co.sirdab.driver.shared.core.demo.DemoControls
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.FixtureLoader
import co.sirdab.driver.shared.core.demo.actors.SimulationEngine
import org.koin.core.module.Module
import org.koin.dsl.module

val demoModule: Module = module {
    single { FixtureLoader(get()) }
    single { DemoWorld(get(), get(), get()) }
    single { DemoClock() }
    single { DemoControls() }
    single { SimulationEngine(emptyList()) }
}
