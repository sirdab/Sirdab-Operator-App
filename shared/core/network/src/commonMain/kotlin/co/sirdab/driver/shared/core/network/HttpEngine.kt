package co.sirdab.driver.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The platform HTTP engine: OkHttp on Android, NSURLSession on iOS. */
expect fun httpClientEngine(): HttpClient

/**
 * Lenient on unknown keys on purpose. The contract is versioned by package, and the API adds fields
 * ahead of the app upgrading; an additive server change must not break a driver mid-shift.
 */
val TmsJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun tmsHttpClient(
    environment: TmsEnvironment,
    logLevel: LogLevel = LogLevel.NONE,
): HttpClient = httpClientEngine().config {
    expectSuccess = false

    install(ContentNegotiation) {
        json(TmsJson)
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    install(Logging) {
        logger = platformHttpLogger()
        level = logLevel
        sanitizeHeader { it.equals("Authorization", ignoreCase = true) }
        // Supabase takes the anon key as its own header, which is a credential
        // like any other and does not belong in a log.
        sanitizeHeader { it.equals("apikey", ignoreCase = true) }
        filter { request -> isLoggableBody(request.url.buildString()) }
    }

    defaultRequest {
        contentType(ContentType.Application.Json)
        url(environment.apiBaseUrl.trimEnd('/') + "/")
    }
}
