package co.sirdab.driver.shared.feature.profile.impl

import co.sirdab.driver.shared.feature.profile.impl.presentation.ProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val profileModule: Module = module {
    viewModelOf(::ProfileViewModel)
}
