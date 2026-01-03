package gg.grounds.agones

import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.future.await

class AgonesRestClient
private constructor(private val baseUri: URI, private val httpClient: HttpClient) {
    private val gson = Gson()

    suspend fun ready(): HttpResponse<String> {
        return send(
            requestBuilder("/ready").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            "Ready",
        )
    }

    suspend fun allocate(): HttpResponse<String> {
        return send(
            requestBuilder("/allocate").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            "Allocate",
        )
    }

    suspend fun getGameServer(): GameServerResponse {
        val request = requestBuilder("/gameserver").GET().build()
        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        val body = response.body()
        if (response.statusCode() !in 200..299) {
            return GameServerResponse(response.statusCode(), body, null)
        }

        val gameServer =
            try {
                gson.fromJson(body, GameServer::class.java)
            } catch (_: Throwable) {
                null
            }

        return GameServerResponse(response.statusCode(), body, gameServer)
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

    private fun requestBuilder(path: String): HttpRequest.Builder {
        val normalizedPath = path.removePrefix("/")
        val targetUri = baseUri.resolve(normalizedPath)
        return HttpRequest.newBuilder(targetUri)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
    }

    private suspend fun send(request: HttpRequest, action: String): HttpResponse<String> {
        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Agones $action call failed with status ${response.statusCode()}: ${response.body()}"
            )
        }
        return response
    }

    companion object {
        fun fromEnvironment(): AgonesRestClient {
            val baseUri = URI("http://localhost:9358/")
            return AgonesRestClient(baseUri, HttpClient.newHttpClient())
        }
    }
}
