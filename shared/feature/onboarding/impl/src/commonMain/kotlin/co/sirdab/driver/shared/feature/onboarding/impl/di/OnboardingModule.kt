package co.sirdab.driver.shared.feature.onboarding.impl.di

import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileRemote
import co.sirdab.driver.shared.feature.onboarding.impl.data.AuthRepositoryMock
import co.sirdab.driver.shared.feature.onboarding.impl.data.AuthRepositoryTms
import co.sirdab.driver.shared.feature.onboarding.impl.data.DriverProfileRemoteHttp
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.otp.OtpViewModel
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.phone.PhoneViewModel
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.splash.SplashViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val onboardingModule: Module = module {
    single { AuthRepositoryMock(get()) } bind AuthRepository::class

    viewModelOf(::SplashViewModel)
    viewModelOf(::PhoneViewModel)
    viewModel { (phone: String) -> OtpViewModel(phone, get()) }
}

/**
 * Overrides the mock with the real sign-in. Loaded after [onboardingModule] in `createTmsModules`.
 */
val tmsOnboardingModule: Module = module {
    single { DriverProfileRemoteHttp(get()) } bind DriverProfileRemote::class
    single { AuthRepositoryTms(get(), get(), get()) } bind AuthRepository::class
}
