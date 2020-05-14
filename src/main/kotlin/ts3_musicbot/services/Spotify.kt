package ts3_musicbot.services

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ts3_musicbot.util.sendHttpRequest
import java.net.HttpURLConnection
import java.net.URL

class Spotify(private val market: String = "") {
    private var accessToken = ""

    fun updateToken() {
        println("Updating Spotify access token...")
        accessToken = getSpotifyToken()
    }

    private fun getSpotifyToken(): String {
        fun getData(): Pair<Int, String> {
            val auth = "ZGUzZGFlNGUxZTE3NGRkNGFjYjY0YWYyMjcxMWEwYmI6ODk5OGQxMmJjZDBlNDAzM2E2Mzg2ZTg4Y2ZjZTk2NDg="
            val url = URL("https://accounts.spotify.com/api/token")
            val requestMethod = "POST"
            val properties = arrayOf(
                "Authorization: Basic $auth",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            val data = arrayOf("grant_type=client_credentials")
            return sendHttpRequest(url, requestMethod, properties, data)
        }

        var token = ""
        var gettingData = true
        while (gettingData) {
            val data = getData()
            //check http return code
            when (data.first) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        val tokenData = JSONObject(data.second)
                        token = tokenData.getString("access_token")
                        gettingData = false
                    } catch (e: JSONException) {
                        //JSON broken, try getting the data again
                        println("Failed JSON:\n${data.second}\n")
                        println("Failed to get data from JSON, trying again...")
                    }
                }
                else -> {
                    println("HTTP ERROR! CODE: ${data.first}")
                }
            }
        }
        return token
    }

    fun searchSpotify(searchType: String, searchQuery: String): String {
        val searchResult = StringBuilder()

        fun searchData(): Pair<Int, String> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/search?")
            urlBuilder.append("q=${searchQuery.replace(" ", "%20").replace("\"", "%22")}")
            urlBuilder.append("&type=$searchType")
            //urlBuilder.append("&limit=10")
            if (market.isNotEmpty()) {
                urlBuilder.append("&market=$market")
            }
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            return sendHttpRequest(url, requestMethod, properties)
        }

        fun parseResults(searchData: JSONObject) {
            //token is valid, parse data
            when (searchType) {
                "track" -> {
                    val trackList = searchData.getJSONObject("tracks").getJSONArray("items")
                    for (trackData in trackList) {
                        trackData as JSONObject

                        val artists = StringBuilder()
                        artists.append("Artist: ")
                        for (artistData in trackData.getJSONArray("artists")) {
                            val artist = artistData as JSONObject
                            artists.append("${artist.getString("name")}, ")
                        }

                        val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                            "${trackData.getJSONObject("album").getString("name")} (Single)"
                        } else {
                            trackData.getJSONObject("album").getString("name")
                        }

                        val songName = trackData.getString("name")

                        val songLink = trackData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "${artists.toString().substringBeforeLast(",")}\n" +
                                    "Album:  $albumName\n" +
                                    "Title:  $songName\n" +
                                    "Link:   $songLink\n"
                        )
                    }
                }
                "album" -> {
                    val albums = searchData.getJSONObject("albums").getJSONArray("items")
                    for (album in albums) {
                        album as JSONObject

                        val artists = StringBuilder()
                        artists.append("Artist: ")
                        for (artistData in album.getJSONArray("artists")) {
                            val artist = artistData as JSONObject
                            artists.append("${artist.getString("name")}, ")
                        }


                        val albumName = if (album.getString("album_type") == "single") {
                            "${album.getString("name")} (Single)"
                        } else {
                            album.getString("name")
                        }

                        val albumLink = album.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "${artists.toString().substringBeforeLast(",")}\n" +
                                    "Album:  $albumName\n" +
                                    "Link:   $albumLink\n"
                        )
                    }

                }
                "playlist" -> {
                    val playlists = searchData.getJSONObject("playlists").getJSONArray("items")
                    for (listData in playlists) {
                        listData as JSONObject

                        val listName = listData.getString("name")
                        val listOwner = if (listData.getJSONObject("owner").get("display_name") != null) {
                            listData.getJSONObject("owner").get("display_name")
                        } else {
                            "N/A"
                        }
                        val listLink = listData.getJSONObject("external_urls").getString("spotify")

                        searchResult.appendln(
                            "" +
                                    "Playlist: $listName\n" +
                                    "Owner:    $listOwner\n" +
                                    "Link:     $listLink\n"
                        )
                    }
                }
            }
        }

        var gettingData = true
        while (gettingData) {
            println("Searching for \"$searchQuery\" on Spotify...")
            val searchData = searchData()
            //check http return code
            when (searchData.first) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        val resultData = JSONObject(searchData.second)
                        parseResults(resultData)
                        gettingData = false
                    } catch (e: JSONException) {
                        //JSON broken, try getting the data again
                        println("Failed JSON:\n${searchData.second}\n")
                        println("Failed to get data from JSON, trying again...")
                    }
                }

                HttpURLConnection.HTTP_UNAUTHORIZED -> updateToken() //token expired, update it.

                else -> println("HTTP ERROR! CODE: ${searchData.first}")

            }
        }
        return searchResult.toString().substringBeforeLast("\n")
    }

    fun getPlaylistTracks(playListLink: String): ArrayList<Track> {
        val trackItems = ArrayList<Track>()

        fun getPlaylistData(): Pair<Int, String> {
            //First get the playlist length
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/playlists/")
            urlBuilder.append(
                if (playListLink.contains("spotify:") && playListLink.contains("playlist:")) {
                    playListLink.substringAfterLast(":")
                } else {
                    playListLink.substringAfter("playlist/").substringBefore("?si=")
                }
            )
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            return sendHttpRequest(url, requestMethod, properties)
        }

        fun parsePlaylistData(playlistData: JSONObject) {
            //get playlist length
            var playlistLength = playlistData.getJSONObject("tracks").getInt("total")
            //Now get all tracks
            //spotify only shows 100 items per search, so with each 100 items, listOffset will be increased
            var listOffset = 0
            while (trackItems.size < playlistLength) {

                fun getItemData(): Pair<Int, String> {
                    val listUrlBuilder = StringBuilder()
                    listUrlBuilder.append("https://api.spotify.com/v1/playlists/")
                    listUrlBuilder.append(
                        if (playListLink.contains("spotify:") && playListLink.contains("playlist:")) {
                            playListLink.substringAfterLast(":")
                        } else {
                            playListLink.substringAfter("playlist/").substringBefore("?si=")
                        }
                    )
                    listUrlBuilder.append("/tracks?limit=100")
                    listUrlBuilder.append("&offset=$listOffset")
                    if (market.isNotEmpty()) {
                        listUrlBuilder.append("&market=$market")
                    }
                    val listUrl = URL(listUrlBuilder.toString())
                    val listRequestMethod = "GET"
                    val listProperties = arrayOf(
                        "Authorization: Bearer $accessToken",
                        "Content-Type: application/x-www-form-urlencoded",
                        "User-Agent: Mozilla/5.0"
                    )
                    return sendHttpRequest(listUrl, listRequestMethod, listProperties)
                }

                fun parseItems(items: JSONArray) {
                    for (item in items) {
                        item as JSONObject
                        try {
                            if (item.get("track") != null) {
                                if (item.getJSONObject("track").get("id") != null) {
                                    if (!item.getJSONObject("track").getBoolean("is_local")) {
                                        val albumName = if (item.getJSONObject("track").getJSONObject("album")
                                                .getString("album_type") == "single"
                                        ) {
                                            "${item.getJSONObject("track").getJSONObject("album")
                                                .getString("name")} (Single)"
                                        } else {
                                            item.getJSONObject("track").getJSONObject("album").getString("name")
                                        }
                                        val artistList = item.getJSONObject("track").getJSONArray("artists")
                                        val artistBuilder = StringBuilder()
                                        for (artistJSON in artistList) {
                                            artistJSON as JSONObject
                                            artistBuilder.append(artistJSON.getString("name") + ",")
                                        }
                                        val artist = artistBuilder.toString().substringBeforeLast(",")
                                        val title = item.getJSONObject("track").getString("name")
                                        val link =
                                            item.getJSONObject("track").getJSONObject("external_urls")
                                                .getString("spotify")
                                        val isPlayable = if (market.isNotEmpty()) {
                                            item.getJSONObject("track").getBoolean("is_playable")
                                        } else {
                                            true
                                        }
                                        trackItems.add(Track(albumName, artist, title, link, isPlayable))
                                    } else {
                                        playlistLength -= 1
                                    }
                                }
                            }
                        } catch (e: JSONException) {
                            playlistLength -= 1
                        }

                    }
                }

                val itemData = getItemData()
                var gettingData = true
                while (gettingData) {
                    when (itemData.first) {
                        HttpURLConnection.HTTP_OK -> {
                            try {
                                val item = JSONObject(itemData.second)
                                parseItems(item.getJSONArray("items"))
                                listOffset += 100
                                gettingData = false
                            } catch (e: JSONException) {
                                //JSON broken, try getting the data again
                                println("Failed JSON:\n${itemData.second}\n")
                                println("Failed to get data from JSON, trying again...")
                            }
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            //token expired, update it
                            updateToken()
                        }
                        else -> {
                            println("HTTP ERROR! CODE: ${itemData.first}")
                        }
                    }
                }
            }
        }

        var gettingData = true
        while (gettingData) {
            val playlistData = getPlaylistData()
            //check http return code
            when (playlistData.first) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        val playlist = JSONObject(playlistData.second)
                        parsePlaylistData(playlist)
                        gettingData = false
                    } catch (e: JSONException) {
                        //JSON broken, try getting the data again
                        println("Failed JSON:\n${playlistData.second}\n")
                        println("Failed to get data from JSON, trying again...")
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    //token expired, update
                    updateToken()
                }
                else -> println("HTTP ERROR! CODE: ${playlistData.first}")
            }
        }
        return trackItems
    }

    fun getAlbumTracks(albumLink: String): ArrayList<Track> {
        val trackItems = ArrayList<Track>()

        fun getAlbumData(): Pair<Int, String> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/albums/")
            urlBuilder.append(
                if (albumLink.contains("spotify:") && albumLink.contains("album:")) {
                    albumLink.substringAfterLast(":")
                } else {
                    albumLink.substringAfter("album/").substringBefore("?si=")
                }
            )
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            return sendHttpRequest(url, requestMethod, properties)
        }

        fun parseAlbumData(albumData: JSONObject) {
            val trackItemsLength = albumData.getJSONObject("tracks").getInt("total")
            val albumName = if (albumData.getString("album_type") == "single") {
                "${albumData.getString("name")} (Single)"
            } else {
                albumData.getString("name")
            }

            //Now get all tracks
            //spotify only shows 20 items per search, so with each 20 items, listOffset will be increased
            var listOffset = 0
            while (trackItems.size < trackItemsLength) {

                fun getAlbumTracks(): JSONObject {
                    val albumUrlBuilder = StringBuilder()
                    albumUrlBuilder.append("https://api.spotify.com/v1/albums/")
                    albumUrlBuilder.append(
                        if (albumLink.contains("spotify:") && albumLink.contains("album:")) {
                            albumLink.substringAfterLast(":")
                        } else {
                            albumLink.substringAfter("album/").substringBefore("?si=")
                        }
                    )
                    albumUrlBuilder.append("/tracks?limit=20")
                    albumUrlBuilder.append("&offset=$listOffset")
                    if (market.isNotEmpty()) {
                        albumUrlBuilder.append("&market=$market")
                    }
                    val albumUrl = URL(albumUrlBuilder.toString())
                    val albumRequestMethod = "GET"
                    val albumProperties = arrayOf(
                        "Authorization: Bearer $accessToken",
                        "Content-Type: application/x-www-form-urlencoded",
                        "User-Agent: Mozilla/5.0"
                    )
                    val albumRawResponse = sendHttpRequest(albumUrl, albumRequestMethod, albumProperties)
                    return if (albumRawResponse.second.isNotEmpty())
                        JSONObject(albumRawResponse.second)
                    else
                        JSONObject("{ \"error\": { \"status\": 401, \"message\": \"The access token expired\" } }")
                }

                fun parseItems(items: JSONArray) {
                    for (item in items) {
                        item as JSONObject
                        val artistList = item.getJSONArray("artists")
                        val artistBuilder = StringBuilder()
                        for (artistJSON in artistList) {
                            artistJSON as JSONObject
                            artistBuilder.append(artistJSON.getString("name") + ",")
                        }
                        val artist = artistBuilder.toString().substringBeforeLast(",")
                        val title = item.getString("name")
                        val link = item.getJSONObject("external_urls").getString("spotify")
                        val isPlayable = if (market.isNotEmpty()) {
                            item.getBoolean("is_playable")
                        } else {
                            true
                        }
                        trackItems.add(Track(albumName, artist, title, link, isPlayable))
                    }
                }

                val albumTracks = getAlbumTracks()
                //check token
                if (albumTracks.has("error") && albumTracks.getJSONObject("error").getInt("status") == 401) {
                    //token expired, update it
                    updateToken()
                } else {
                    parseItems(albumTracks.getJSONArray("items"))
                    listOffset += 20
                }
            }
        }

        var gettingData = true
        while (gettingData) {
            val albumData = getAlbumData()
            //check http return code
            when (albumData.first) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        val data = JSONObject(albumData.second)
                        parseAlbumData(data)
                        gettingData = false
                    } catch (e: JSONException) {
                        //JSON broken, try getting the data again
                        println("Failed JSON:\n${albumData.second}\n")
                        println("Failed to get data from JSON, trying again...")
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    //token expired, update it
                    updateToken()
                }

                else -> println("HTTP ERROR! CODE ${albumData.first}")
            }
        }
        return trackItems
    }

    fun getTrack(trackLink: String): Track {

        fun getTrackData(): Pair<Int, String> {
            val urlBuilder = StringBuilder()
            urlBuilder.append("https://api.spotify.com/v1/tracks/")
            urlBuilder.append(
                if (trackLink.contains("spotify:") && trackLink.contains(":track:")) {
                    trackLink.substringAfterLast(":")
                } else {
                    trackLink.substringAfter("track/").substringBefore("?si=")
                }
            )
            if (market.isNotEmpty()) {
                urlBuilder.append("?market=$market")
            }
            val url = URL(urlBuilder.toString())
            val requestMethod = "GET"
            val properties = arrayOf(
                "Authorization: Bearer $accessToken",
                "Content-Type: application/x-www-form-urlencoded",
                "User-Agent: Mozilla/5.0"
            )
            return sendHttpRequest(url, requestMethod, properties)
        }

        fun parseData(trackData: JSONObject): Track {
            val artistsBuilder = StringBuilder()
            trackData.getJSONArray("artists").forEach {
                it as JSONObject
                artistsBuilder.append("${it.getString("name")}, ")
            }
            val artist = artistsBuilder.toString().substringBeforeLast(",")
            val albumName = if (trackData.getJSONObject("album").getString("album_type") == "single") {
                "${trackData.getJSONObject("album").getString("name")} (Single)"
            } else {
                trackData.getJSONObject("album").getString("name")
            }
            val title = trackData.getString("name")
            val isPlayable = if (market.isNotEmpty()) {
                trackData.getBoolean("is_playable")
            } else {
                true
            }
            return Track(albumName, artist, title, trackLink, isPlayable)
        }

        var track: Track = Track.Empty
        var gettingData = true
        while (gettingData) {
            val trackData = getTrackData()
            when (trackData.first) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        track = parseData(JSONObject(trackData.second))
                        gettingData = false
                    } catch (e: JSONException) {
                        //JSON broken, try getting the data again
                        println("Failed JSON:\n${trackData.second}\n")
                        println("Failed to get data from JSON, trying again...")
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    updateToken()
                }
                else -> println("HTTP ERROR! CODE: ${trackData.first}")
            }
        }
        return track
    }
}
