package io.github.tbib.ktorgenerator.annotations.engine

import io.ktor.client.HttpClient

object KtorGeneratorClient {
    var ktorClient: HttpClient? = null

    fun initKtorClient(client: HttpClient) {
        ktorClient?.close()
        ktorClient = client
    }

}