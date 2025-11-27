package io.github.tbib.ktorgenerator.annotations.engine

import io.ktor.client.HttpClient

object KtorGeneratorClient {
    var ktorClient: HttpClient? = null
    var baseUrl: String? = null

    fun initKtorClient(client: HttpClient, baseUrl: String) {
        this.baseUrl = baseUrl
        ktorClient?.close()
        ktorClient = client
    }

}