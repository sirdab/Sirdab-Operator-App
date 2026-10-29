package co.sirdab.driver.shared.feature.guide.impl

import co.sirdab.driver.shared.core.auth.SessionManager
import org.koin.core.module.Module
import org.koin.dsl.module

val guideModule: Module = module {
    single { GuideStore(get(), get<SessionManager>().state) }
}
