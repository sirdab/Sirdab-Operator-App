package co.sirdab.driver.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun httpClientEngine(): HttpClient = HttpClient(Darwin)
