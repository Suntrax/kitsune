package com.blissless.manga.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AniListMangaEntry(
    val mediaId: Int,
    val title: String,
    val englishTitle: String? = null,
    val nativeTitle: String? = null,
    val coverUrl: String? = null,
    val siteUrl: String? = null,
    val chapters: Int? = null,
    val description: String? = null,
    val progress: Int = 0,
    val status: String = "",
    val score: Int? = null,
    val localMangaUrl: String? = null
) {
    fun toReadingStatus(): ReadingStatus = when (status) {
        "CURRENT", "REPEATING" -> ReadingStatus.READING
        "PLANNING" -> ReadingStatus.PLANNING
        "COMPLETED" -> ReadingStatus.COMPLETED
        "PAUSED" -> ReadingStatus.ON_HOLD
        "DROPPED" -> ReadingStatus.DROPPED
        else -> ReadingStatus.PLANNING
    }

    fun withLocalUrl(url: String?): AniListMangaEntry = copy(localMangaUrl = url)
}

class AniListManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val PREFS_NAME = "anilist_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_SYNCED_MANGA = "synced_manga"

        const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
        const val GRAPHQL_URL = "https://graphql.anilist.co"
        const val REDIRECT_URI = "animescraper://success"
        const val CLIENT_ID = 36828
    }

    fun getAuthUrl(): String {
        return "$AUTH_URL?client_id=$CLIENT_ID&response_type=token"
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun isLoggedIn(): Boolean = !prefs.getString(KEY_ACCESS_TOKEN, "").isNullOrBlank()

    fun getLoggedInUser(): String? {
        val json = prefs.getString(KEY_USER_DATA, null) ?: return null
        return try {
            JSONObject(json).optString("name")
        } catch (e: Exception) { null }
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_USER_DATA)
            .remove(KEY_SYNCED_MANGA)
            .apply()
    }

    suspend fun getUserInfo(): Result<JSONObject> {
        val token = getAccessToken() ?: return Result.failure(Exception("Not logged in"))
        val query = "query { Viewer { id name avatar { large } } }"
        return executeGraphQL(query, token)
    }

    suspend fun fetchAndCacheUserInfo(): Result<String> {
        val result = getUserInfo()
        return result.map { data ->
            val viewer = data.optJSONObject("Viewer")
            if (viewer != null) {
                prefs.edit().putString(KEY_USER_DATA, viewer.toString()).apply()
            }
            viewer?.optString("name") ?: "Unknown"
        }
    }

    suspend fun getUserMangaLists(): Result<List<AniListMangaEntry>> {
        val token = getAccessToken() ?: return Result.failure(Exception("Not logged in"))

        val userResult = getUserInfo()
        val data = userResult.getOrNull() ?: return Result.failure(Exception("Failed to get user info"))
        val viewer = data.optJSONObject("Viewer") ?: return Result.failure(Exception("No Viewer data"))
        val userId = viewer.optInt("id", -1)
        if (userId < 0) return Result.failure(Exception("Invalid user ID"))

        val query = """
            query {
              MediaListCollection(userId: $userId, type: MANGA) {
                lists {
                  name
                  entries {
                    media {
                      id
                      title { romaji english native }
                      coverImage { large }
                      siteUrl
                      chapters
                    }
                    progress
                    status
                    score
                  }
                }
              }
            }
        """.trimIndent()

        return withContext(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply { put("query", query) }
                val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(GRAPHQL_URL)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val root = JSONObject(responseBody)
                    val collection = root.optJSONObject("data")?.optJSONObject("MediaListCollection")
                    val lists = collection?.optJSONArray("lists")
                    val entries = mutableListOf<AniListMangaEntry>()

                    if (lists != null) {
                        for (i in 0 until lists.length()) {
                            val list = lists.getJSONObject(i)
                            val listEntries = list.optJSONArray("entries")
                            if (listEntries != null) {
                                for (j in 0 until listEntries.length()) {
                                    val entry = listEntries.getJSONObject(j)
                                    val media = entry.optJSONObject("media")
                                    val title = media?.optJSONObject("title")
                                    entries.add(AniListMangaEntry(
                                        mediaId = media?.optInt("id") ?: 0,
                                        title = title?.optString("romaji") ?: "",
                                        englishTitle = title?.optString("english"),
                                        nativeTitle = title?.optString("native"),
                                        coverUrl = media?.optJSONObject("coverImage")?.optString("large"),
                                        siteUrl = media?.optString("siteUrl"),
                                        chapters = if (media?.has("chapters") == true && !media.isNull("chapters")) media.optInt("chapters") else null,
                                        progress = entry.optInt("progress", 0),
                                        status = entry.optString("status", ""),
                                        score = if (entry.has("score") && !entry.isNull("score")) entry.optInt("score") else null
                                    ))
                                }
                            }
                        }
                    }

                    saveSyncedManga(entries)
                    Result.success(entries)
                } else {
                    Result.failure(Exception("API error: ${response.code} $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getSyncedManga(): List<AniListMangaEntry> {
        val json = prefs.getString(KEY_SYNCED_MANGA, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AniListMangaEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun saveSyncedManga(entries: List<AniListMangaEntry>) {
        prefs.edit().putString(KEY_SYNCED_MANGA, gson.toJson(entries)).apply()
    }

    fun updateSyncedMangaEntry(entry: AniListMangaEntry) {
        val all = getSyncedManga().toMutableList()
        val idx = all.indexOfFirst { it.mediaId == entry.mediaId }
        if (idx >= 0) all[idx] = entry else all.add(entry)
        saveSyncedManga(all)
    }

    private suspend fun executeGraphQL(query: String, token: String): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply { put("query", query) }
                val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(GRAPHQL_URL)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val root = JSONObject(responseBody)
                    val data = root.optJSONObject("data")
                    if (data != null) Result.success(data)
                    else Result.failure(Exception("No data in response: $responseBody"))
                } else {
                    Result.failure(Exception("GraphQL error: ${response.code} $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun createTrackingEntries(entries: List<AniListMangaEntry>, trackingManager: TrackingManager) {
        for (entry in entries) {
            val mangaId = "anilist_${entry.mediaId}"
            val existing = trackingManager.getMangaTracking(mangaId)
            if (existing != null) {
                val updated = existing.copy(
                    status = entry.toReadingStatus(),
                    currentChapterNumber = entry.progress,
                    currentChapterIndex = (entry.progress - 1).coerceAtLeast(0),
                    totalChapters = entry.chapters ?: existing.totalChapters,
                    lastReadTimestamp = System.currentTimeMillis(),
                    anilistMediaId = entry.mediaId
                )
                trackingManager.updateTracking(updated)
            } else {
                val track = MangaTrack(
                    mangaId = mangaId,
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    currentChapterIndex = (entry.progress - 1).coerceAtLeast(0),
                    currentChapterNumber = entry.progress,
                    currentChapterUrl = "",
                    totalChapters = entry.chapters ?: 0,
                    status = entry.toReadingStatus(),
                    lastReadTimestamp = System.currentTimeMillis(),
                    mangaUrl = entry.localMangaUrl ?: entry.siteUrl ?: "https://anilist.co/manga/${entry.mediaId}",
                    anilistMediaId = entry.mediaId
                )
                trackingManager.updateTracking(track)
            }
        }
    }

    suspend fun updateMediaListEntry(mediaId: Int, progress: Int, status: String): Result<Unit> {
        val token = getAccessToken() ?: return Result.failure(Exception("Not logged in"))

        val dollar = "${'$'}"
        val mutation = """
            mutation(${dollar}mediaId: Int, ${dollar}progress: Int, ${dollar}status: MediaListStatus) {
                SaveMediaListEntry(mediaId: ${dollar}mediaId, progress: ${dollar}progress, status: ${dollar}status) {
                    id
                    progress
                    status
                }
            }
        """.trimIndent()

        val variables = JSONObject().apply {
            put("mediaId", mediaId)
            put("progress", progress)
            put("status", status)
        }

        return executeGraphQLWithVariables(mutation, variables, token).map { }
    }

    private suspend fun executeGraphQLWithVariables(query: String, variables: JSONObject, token: String): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("query", query)
                    put("variables", variables)
                }
                val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(GRAPHQL_URL)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val root = JSONObject(responseBody)
                    val data = root.optJSONObject("data")
                    if (data != null) Result.success(data)
                    else Result.failure(Exception("No data in response: $responseBody"))
                } else {
                    Result.failure(Exception("GraphQL error: ${response.code} $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
