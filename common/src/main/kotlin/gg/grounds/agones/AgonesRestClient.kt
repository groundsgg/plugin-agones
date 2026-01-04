package gg.grounds.agones

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URI

class AgonesRestClient
private constructor(private val baseUri: URI, private val httpClient: HttpClient) {
    private val gson = Gson()

    suspend fun ready(): HttpResponse {
        return send("/ready", HttpMethod.Post, "Ready", body = "{}")
    }

    suspend fun allocate(): HttpResponse {
        return send("/allocate", HttpMethod.Post, "Allocate", body = "{}")
    }

    suspend fun getGameServer(): GameServerResponse {
        val response = send("/gameserver", HttpMethod.Get, "GameServer fetch")
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            return GameServerResponse(response.status.value, body, null)
        }

        val gameServer =
            try {
                gson.fromJson(body, GameServer::class.java)
            } catch (_: Throwable) {
                null
            }

        return GameServerResponse(response.status.value, body, gameServer)
    }

    suspend fun isGameServerInState(desiredState: String): Boolean {
        val response = getGameServer()
        if (response.statusCode !in 200..299) {
            throw IllegalStateException(
                "Agones GameServer fetch failed with status ${response.statusCode}: ${response.body}"
            )
        }

        return response.gameServer?.status?.state == desiredState
    }

    private suspend fun send(
        path: String,
        method: HttpMethod,
        action: String,
        body: String? = null,
    ): HttpResponse {
        val url = baseUri.resolve(path).toString()
        val response =
            httpClient.request(url) {
                this.method = method
                contentType(ContentType.Application.Json)
                if (body != null) {
                    setBody(body)
                }
            }
        if (response.status.value !in 200..299) {
            val responseBody = response.bodyAsText()
            throw IllegalStateException(
                "Agones $action call failed with status ${response.status.value}: $responseBody"
            )
        }
        return response
    }

    companion object {
        fun forSidecar(): AgonesRestClient {
            val baseUri = URI("http://localhost:9358")
            val httpClient =
                HttpClient(CIO) {
                    install(HttpTimeout) { requestTimeoutMillis = 5_000 }
                    install(HttpRequestRetry) {
                        retryOnExceptionOrServerErrors(maxRetries = 3)
                        delayMillis { 500L }
                    }
                }
            return AgonesRestClient(baseUri, httpClient)
        }
    }
}
