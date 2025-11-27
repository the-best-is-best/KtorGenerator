package io.github.tbib.ktorgeneratorapp.network

import io.github.tbib.ktorgenerator.annotations.engine.KtorGeneratorClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

internal abstract class KtorApi {


    init {
        KtorGeneratorClient.initKtorClient(createClient())
    }

    private fun createClient(): HttpClient {
        return HttpClient {
            expectSuccess = true

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println(message)
                    }
                }
                level = LogLevel.ALL
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    useAlternativeNames = false
                })
            }

            install(HttpRequestRetry) {
                maxRetries = 3
                retryIf { _, response ->
                    response.status.value == 401 || response.status.value in 500..599
                }
                retryOnExceptionIf { _, cause -> cause is IOException }
                delayMillis { retry -> retry * 1000L }
                modifyRequest { request ->
                    request.timeout {
                        requestTimeoutMillis = 10000
                    }
                }
            }



            defaultRequest {
                header("Content-Type", "application/json")
            }
        }
    }

    // ✅ Reset Client
    fun resetClient() {
        KtorGeneratorClient.initKtorClient(createClient())
    }

    open fun HttpRequestBuilder.pathUrl(path: String) {
        url {
//            takeFrom(BuildKonfig.DOMAIN)
//                .path(BuildKonfig.API_PATH, path)
        }
    }
}
