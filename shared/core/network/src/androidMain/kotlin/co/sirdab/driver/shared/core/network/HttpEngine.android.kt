package co.sirdab.driver.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClientEngine(): HttpClient = HttpClient(OkHttp)
