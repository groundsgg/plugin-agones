package gg.grounds.agones

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.future.await

class AgonesRestClient
private constructor(private val baseUri: URI, private val httpClient: HttpClient) {
    suspend fun ready(): HttpResponse<String> {
        val request =
            requestBuilder("/ready").POST(HttpRequest.BodyPublishers.ofString("{}")).build()
        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Agones Ready call failed with status ${response.statusCode()}: ${response.body()}"
            )
        }
        return response
    }

    suspend fun allocate(): HttpResponse<String> {
        val request =
            requestBuilder("/allocate").POST(HttpRequest.BodyPublishers.ofString("{}")).build()
        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Agones Allocate call failed with status ${response.statusCode()}: ${response.body()}"
            )
        }
        return response
    }

    suspend fun getGameServer(): GameServerResponse {
        val request = requestBuilder("/gameserver").GET().build()
        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        val body = response.body()
        if (response.statusCode() !in 200..299) {
            return GameServerResponse(response.statusCode(), body, null)
        }

        val state =
            try {
                val root = JsonParser.parseString(body).asJsonObject
                val status = root.getAsJsonObject("status")
                val stateElement = status?.get("state")
                if (stateElement != null && stateElement.isJsonPrimitive) {
                    stateElement.asString
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }

        return GameServerResponse(response.statusCode(), body, state)
    }

    suspend fun isGameServerInState(desiredState: String): Boolean {
        val response = getGameServer()
        if (response.statusCode !in 200..299) {
            throw IllegalStateException(
                "Agones GameServer fetch failed with status ${response.statusCode}: ${response.body}"
            )
        }
        return response.state == desiredState
    }

    private fun requestBuilder(path: String): HttpRequest.Builder {
        val normalizedPath = path.removePrefix("/")
        val targetUri = baseUri.resolve(normalizedPath)
        return HttpRequest.newBuilder(targetUri)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
    }

    companion object {
        fun fromEnvironment(): AgonesRestClient {
            val baseUri = URI("http://localhost:9358/")
            return AgonesRestClient(baseUri, HttpClient.newHttpClient())
        }
    }
}

data class GameServerResponse(val statusCode: Int, val body: String, val state: String?)
