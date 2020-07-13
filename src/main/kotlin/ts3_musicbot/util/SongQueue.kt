package ts3_musicbot.util

import ts3_musicbot.services.*
import java.time.LocalDate
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

class SongQueue(private val spotify: Spotify = Spotify(), private val spotifyPlayer: String = "spotify") :
    PlayStateListener {
    @Volatile
    private var songQueue = Collections.synchronizedList(ArrayList<String>())

    private val commandRunner = CommandRunner()
    private val youTube = YouTube()
    private val soundCloud = SoundCloud()
    private val playStateListener: PlayStateListener = this
    private lateinit var playStateListener2: PlayStateListener

    @Volatile
    private var currentSong = ""

    @Volatile
    private var shouldMonitorSp: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var shouldMonitorYt: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var shouldMonitorSc: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var songQueueActive: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var songPosition = 0

    private val spotifyListenerThread = Thread {
        Runnable {
            var songLength = 0 //Song length in seconds
            var adNotified = false
            while (songQueueActive.get()) {
                if (shouldMonitorSp.get()) {
                    val lengthMicroseconds = try {
                        commandRunner.runCommand(
                            "playerctl -p $spotifyPlayer metadata --format '{{ mpris:length }}'",
                            printOutput = false
                        ).toInt()
                    } catch (e: Exception) {
                        //song hasn't started
                        0
                    }
                    val current =
                        commandRunner.runCommand(
                            "playerctl -p $spotifyPlayer metadata --format '{{ xesam:url }}'",
                            printOutput = false
                        )
                    val playerStatus = commandRunner.runCommand("playerctl -p $spotifyPlayer status", printOutput = false)
                    if (playerStatus == "Playing") {
                        //song is playing

                        if (current.substringAfterLast(":").substringBefore("?")
                                .substringAfterLast("/") == currentSong.substringAfterLast(":").substringBefore("?")
                                .substringAfterLast("/")
                        ) {
                            val minutes = lengthMicroseconds / 1000000 / 60
                            val seconds = lengthMicroseconds / 1000000 % 60
                            songLength = minutes * 60 + seconds

                            //println("Position = $songPosition / $songLength")
                            if (songPosition < songLength) {
                                songPosition++
                                val delay: Long = 990
                                val delaySub: Float = songLength.toFloat() / 10
                                Thread.sleep(delay - delaySub.toLong())
                            }
                            adNotified = false
                        } else if (current.startsWith("https://open.spotify.com/ad/")) {
                            //ad playing, wait for it to finish.
                            if (!adNotified) {
                                adNotified = true
                                println("Ad playing.")
                                playStateListener.onAdPlaying()
                            }
                        } else {
                            //song has changed
                            songPosition = 0
                            //shouldMonitorSp = false
                            //playStateListener.onSongEnded("spotify", currentSong)
                        }
                    } else {
                        //Song is paused/stopped

                        if (current.substringAfterLast(":").substringBefore("?")
                                .substringAfterLast("/") == currentSong.substringAfterLast(":").substringBefore("?")
                                .substringAfterLast("/")
                        ) {
                            adNotified = false
                            if (songPosition >= songLength - 10 || playerStatus == "Stopped") {
                                //Song has ended
                                Thread.sleep(380)
                                songPosition = 0
                                //shouldMonitorSp = false
                                playStateListener.onSongEnded(spotifyPlayer, Link(currentSong))
                            } else {
                                //check if song position is 0
                                if (songPosition == 0) {
                                    //start playback

                                    //spotify broken?
                                    commandRunner.runCommand("playerctl -p $spotifyPlayer play")
                                    Thread.sleep(1000)
                                    if (commandRunner.runCommand(
                                            "playerctl -p $spotifyPlayer status",
                                            printOutput = false
                                        ) != "Playing"
                                    ) {
                                        commandRunner.runCommand("killall $spotifyPlayer && $spotifyPlayer &", printOutput = false)
                                        Thread.sleep(5000)

                                        commandRunner.runCommand(
                                            "playerctl -p $spotifyPlayer open spotify:track:${
                                            currentSong.substringAfter("spotify.com/track/")
                                                .substringBefore("?")}", ignoreOutput = false
                                        )
                                    }
                                } else {
                                    //Song is paused, wait for user to resume playback
                                    continue
                                }
                            }
                        } else {
                            //Song has changed

                            //reset songPosition and turn monitor off
                            songPosition = 0
                            //shouldMonitorSp = false
                            //break
                        }
                    }
                }
            }
        }.run()
    }

    /**
     * Adds track to queue
     * @param songLink song's link
     * @param position position in which the song should be added.
     */
    fun addToQueue(
        songLink: String, position: Int = if (synchronized(this) { songQueue }.isNotEmpty()) {
            -1
        } else {
            0
        }
    ) {
        synchronized(this) {
            if (position >= 0)
                songQueue.add(position, songLink)
            else
                songQueue.add(songLink)
        }
    }

    /**
     * Adds a list of links to the queue
     * @param songLinks list of links to add to the queue
     * @param position position in which the songs should be added
     */
    fun addAllToQueue(
        songLinks: List<String>, position: Int = if (synchronized(this) { songQueue }.isNotEmpty()) {
            -1
        } else {
            0
        }
    ) {
        synchronized(this) {
            if (position >= 0)
                songQueue.addAll(position, songLinks)
            else {
                songQueue.addAll(songLinks)
            }
        }
    }

    /**
     * Clear the queue
     */
    fun clearQueue() {
        synchronized(this) {
            songQueue.clear()
        }
    }

    /**
     * Get an ArrayList of links in the queue
     */
    fun getQueue(): ArrayList<String> = synchronized(this) { songQueue }.toMutableList() as ArrayList<String>

    /**
     * Get currently playing track
     * @return returns a Track object based on the current song's link
     */
    suspend fun nowPlaying(): Track {
        return when (currentSong.linkType) {
            spotifyPlayer -> {
                spotify.getTrack(Link(currentSong))
            }
            "youtube" -> {
                Track(
                    Album(
                        Name(""),
                        Artists(emptyList()),
                        ReleaseDate(LocalDate.now()),
                        TrackList(emptyList()),
                        Link("")
                    ),
                    Artists(emptyList()),
                    Name(youTube.getVideoTitle(Link(currentSong))),
                    Link(currentSong),
                    Playability(true)
                )
            }
            "soundcloud" -> {
                soundCloud.getTrack(Link(currentSong))
            }
            else -> {
                Track(
                    Album(
                        Name(""),
                        Artists(emptyList()),
                        ReleaseDate(LocalDate.now()),
                        TrackList(emptyList()),
                        Link("")
                    ),
                    Artists(emptyList()),
                    Name(""),
                    Link(""),
                    Playability(false)
                )
            }
        }
    }

    /**
     * Adds a getTrack function to ArrayList<String>.
     * It can be used to get a Track object straight from the songQueue list, for example:
     * val track = songQueue.getTrack("https://open.spotify.com/track/1WtBDRmbb0q9hzDk2H4pyH")
     * @param songLink link to track
     * @return returns a Track object based on the link
     */
    private suspend fun ArrayList<String>.getTrack(songLink: Link): Track {
        return if (any { it == songLink.link }) {
            return when (songLink.link.linkType) {
                "spotify" -> {
                    spotify.getTrack(songLink)
                }
                "youtube" -> {
                    Track(
                        Album(
                            Name(""),
                            Artists(emptyList()),
                            ReleaseDate(LocalDate.now()),
                            TrackList(emptyList()),
                            Link("")
                        ),
                        Artists(emptyList()),
                        Name(youTube.getVideoTitle(songLink)),
                        songLink,
                        Playability(true)
                    )
                }
                "soundclud" -> {
                    soundCloud.getTrack(Link(currentSong))
                }
                else -> {
                    Track(
                        Album(
                            Name(""),
                            Artists(emptyList()),
                            ReleaseDate(LocalDate.now()),
                            TrackList(emptyList()),
                            Link("")
                        ),
                        Artists(emptyList()),
                        Name(""),
                        Link(""),
                        Playability(false)
                    )
                }
            }
        } else {
            Track(
                Album(
                    Name(""),
                    Artists(emptyList()),
                    ReleaseDate(LocalDate.now()),
                    TrackList(emptyList()),
                    Link("")
                ),
                Artists(emptyList()),
                Name(""),
                Link(""),
                Playability(false)
            )
        }
    }

    /**
     * Adds a linkType function to String.
     * Checks the given String if it contains a supported link type and returns it's type.
     */
    private val String.linkType: String
        get() = if (contains("spotify".toRegex())) {
            spotifyPlayer
        } else if (contains("youtube.com".toRegex()) || contains("youtu.be".toRegex())) {
            "youtube"
        } else if (contains("soundcloud.com".toRegex())) {
            "soundcloud"
        } else {
            ""
        }

    /**
     * Shuffles the queue.
     */
    fun shuffleQueue() {
        synchronized(this) {
            songQueue.shuffle()
        }
    }

    /**
     * Skips the current song and starts playing the next one in the queue, if it isn't empty.
     */
    fun skipSong() {
        if (synchronized(this) { songQueue }.isNotEmpty()) {
            playNext()
        } else {
            val currentPlayers = commandRunner.runCommand("playerctl -l", printOutput = false).split("\n".toRegex())
            if (currentPlayers.size > 1) {
                for (player in currentPlayers) {
                    when (player) {
                        "mpv" -> commandRunner.runCommand("playerctl -p mpv stop", ignoreOutput = true)
                        spotifyPlayer -> commandRunner.runCommand("playerctl -p $spotifyPlayer next", ignoreOutput = true)
                    }
                }
            }
        }
    }

    /**
     * Moves a desired track to a new position
     * @param link link to move
     * @param newPosition new position of the track
     */
    fun moveTrack(link: String, newPosition: Int) {
        synchronized(this) {
            if (newPosition < songQueue.size && newPosition >= 0) {
                for (i in songQueue.indices) {
                    if (songQueue[i] == link) {
                        songQueue.removeAt(i)
                        songQueue.add(newPosition, link)
                        break
                    }
                }
            }
        }
    }

    /**
     * Resumes playback.
     */
    fun resume() {
        when (currentSong.linkType) {
            spotifyPlayer -> commandRunner.runCommand("playerctl -p $spotifyPlayer play", printOutput = false, printErrors = false)
            "youtube", "soundcloud" -> commandRunner.runCommand("playerctl -p mpv play", printOutput = false, printErrors = false)
        }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        when (currentSong.linkType) {
            spotifyPlayer -> commandRunner.runCommand("playerctl -p $spotifyPlayer pause", printOutput = false, printErrors = false)
            "youtube", "soundcloud" -> commandRunner.runCommand("playerctl -p mpv pause", printOutput = false, printErrors = false)
        }
    }

    /**
     * Stops the queue.
     */
    fun stopQueue() {
        songQueueActive.set(false)
        shouldMonitorSp.set(false)
        shouldMonitorYt.set(false)
        shouldMonitorSc.set(false)
        currentSong = ""
        commandRunner.runCommand("playerctl pause")
        commandRunner.runCommand("playerctl -p mpv stop")
        commandRunner.runCommand("killall playerctl")
    }

    /**
    @return returns true if the queue is active, false otherwise. Note that the queue can be active even though playback is paused.
     */
    fun queueActive(): Boolean {
        return songQueueActive.get()
    }


    //starts playing song queue
    fun playQueue(queueListener: PlayStateListener) {
        if (!queueActive()) {
            commandRunner.runCommand("killall playerctl", printOutput = false, ignoreOutput = true)
            commandRunner.runCommand("killall mpv", printOutput = false, ignoreOutput = true)
        }
        synchronized(this) {
            if (songQueue.size >= 1) {
                //queue is not empty, so start playing the list.
                playStateListener2 = queueListener
                playSong(songQueue[0])
            }
        }
    }

    private fun playSong(songLink: String) {
        if (!songQueueActive.get()) {
            songQueueActive.set(true)
        }
        if (songLink.startsWith("https://open.spotify.com")) {
            if (commandRunner.runCommand("playerctl -p mpv status", printOutput = false, printErrors = false) == "Playing")
                commandRunner.runCommand("playerctl -p mpv stop")
            currentSong = songLink
            synchronized(this) {
                songQueue.remove(currentSong)
            }
            startSpotifyMonitor()
            val spThread = Thread {
                Runnable {
                    commandRunner.runCommand(
                        "playerctl -p $spotifyPlayer open spotify:track:${songLink.substringAfter("spotify.com/track/")
                            .substringBefore(
                                "?"
                            )}", ignoreOutput = true
                    )
                }.run()
            }
            spThread.start()

            //get current url from spotify player
            var currentUrl = commandRunner.runCommand(
                "playerctl -p $spotifyPlayer metadata --format '{{ xesam:url }}'",
                printOutput = false,
                printErrors = false
            )

            var adNotified = false

            while (currentUrl.substringAfterLast(":").substringBefore("?")
                    .substringAfterLast("/") != currentSong.substringAfterLast(":").substringBefore("?")
                    .substringAfterLast("/")
            ) {
                //get player status
                val playerStatus =
                    commandRunner.runCommand("playerctl -p $spotifyPlayer status", printOutput = false, printErrors = false)
                //get current url from spotify player
                currentUrl = commandRunner.runCommand(
                    "playerctl -p $spotifyPlayer metadata --format '{{ xesam:url }}'",
                    printOutput = false,
                    printErrors = false
                )

                if (playerStatus == "No players found") {
                    //start spotify
                    val spotifyLauncherThread = Thread {
                        Runnable {
                            when (spotifyPlayer) {
                                "spotify" -> commandRunner.runCommand("$spotifyPlayer &", printOutput = false)
                                "ncspot" -> commandRunner.runCommand(
                                    "killall ncspot; \$TERMINAL -e \"ncspot\" &",
                                    printOutput = false,
                                    ignoreOutput = true
                                )
                            }
                        }.run()
                    }
                    spotifyLauncherThread.start()
                    while (commandRunner.runCommand(
                            "ps aux | grep -E \"[0-9]+:[0-9]+ (\\S+)?$spotifyPlayer$ | grep -v \"grep\"",
                            printOutput = false
                        ).isEmpty()
                    ) {
                        //do nothing
                    }
                    //wait a bit for spotify to start
                    Thread.sleep(5000)
                    val spThread2 = Thread {
                        Runnable {
                            commandRunner.runCommand(
                                "playerctl -p $spotifyPlayer open spotify:track:${songLink.substringAfter("spotify.com/track/")
                                    .substringBefore(
                                        "?"
                                    )} &", ignoreOutput = true
                            )
                        }.run()
                    }
                    spThread2.start()
                }
                if (currentUrl.startsWith("https://open.spotify.com/ad/")) {
                    if (!adNotified) {
                        adNotified = true
                        onAdPlaying()
                    }
                }
            }
            if (currentUrl.substringAfterLast(":").substringBefore("?")
                    .substringAfterLast("/") == currentSong.substringAfterLast(":").substringBefore("?")
                    .substringAfterLast("/")
            ) {
                onNewSongPlaying(spotifyPlayer, Link(currentSong))
                songPosition = 0
                shouldMonitorYt.set(false)
                shouldMonitorSc.set(false)
                shouldMonitorSp.set(true)
            }

        } else if (songLink.startsWith("https://youtu.be") || songLink.startsWith("https://youtube.com") || songLink.startsWith(
                "https://www.youtube.com"
            )
        ) {
            if (commandRunner.runCommand("playerctl -p $spotifyPlayer status") == "Playing")
                commandRunner.runCommand("playerctl -p $spotifyPlayer pause")
            currentSong = songLink
            synchronized(this) {
                songQueue.remove(currentSong)
            }
            startYouTubeMonitor()
            val ytThread = Thread {
                Runnable {
                    commandRunner.runCommand(
                        "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl-raw-options=cookies=youtube-dl.cookies,force-ipv4= --ytdl $songLink",
                        inheritIO = true,
                        ignoreOutput = true
                    )
                }.run()
            }
            ytThread.start()
            while (commandRunner.runCommand("playerctl -p mpv status", printOutput = false, printErrors = false) != "Playing") {
                //wait for song to start
            }
            onNewSongPlaying("mpv", Link(currentSong))
            shouldMonitorSp.set(false)
            shouldMonitorSc.set(false)
            shouldMonitorYt.set(true)
        } else if (songLink.startsWith("https://soundcloud.com")) {
            if (commandRunner.runCommand("playerctl -p $spotifyPlayer status") == "Playing")
                commandRunner.runCommand("playerctl -p $spotifyPlayer pause")
            currentSong = songLink
            synchronized(this) {
                songQueue.remove(currentSong)
            }
            startSoundCloudMonitor()
            val scThread = Thread {
                Runnable {
                    commandRunner.runCommand(
                        "mpv --terminal=no --no-video --input-ipc-server=/tmp/mpvsocket --ytdl-raw-options=cookies=youtube-dl.cookies --ytdl $songLink",
                        inheritIO = true,
                        ignoreOutput = true
                    )
                }.run()
            }
            scThread.start()
            while (commandRunner.runCommand("playerctl -p mpv status", printOutput = false, printErrors = false) != "Playing") {
                //wait for song to start
            }
            onNewSongPlaying("mpv", Link(currentSong))
            shouldMonitorSp.set(false)
            shouldMonitorYt.set(false)
            shouldMonitorSc.set(true)
        } else {
            //song not supported so skip it
            synchronized(this) {
                songQueue.remove(currentSong)
                playSong(songQueue[0])
            }
        }
    }

    private fun playNext() {
        //play next song in queue if not empty
        if (synchronized(this) { songQueue }.isNotEmpty()) {
            shouldMonitorYt.set(false)
            shouldMonitorSp.set(false)
            shouldMonitorSc.set(false)
            commandRunner.runCommand("playerctl -p $spotifyPlayer pause", printOutput = false)
            commandRunner.runCommand("playerctl -p mpv stop", printOutput = false, printErrors = false)
            synchronized(this) {
                playSong(songQueue[0])
            }
        } else {
            shouldMonitorSp.set(false)
            shouldMonitorYt.set(false)
            shouldMonitorSc.set(false)
            songQueueActive.set(false)
            currentSong = ""
        }
    }


    override fun onSongEnded(player: String, trackLink: Link) {
        //song ended, so play the next one in the queue
        playNext()
    }

    override fun onNewSongPlaying(player: String, trackLink: Link) {
        println("New song started.")
        playStateListener2.onNewSongPlaying(player, trackLink)
    }

    override fun onAdPlaying() {}

    private fun startSpotifyMonitor() {
        if (!spotifyListenerThread.isAlive)
            spotifyListenerThread.start()
    }

    private fun startYouTubeMonitor() {
        val youTubeListenerThread = Thread {
            Runnable {
                while (songQueueActive.get()) {
                    if (shouldMonitorYt.get()) {
                        if (commandRunner.runCommand(
                                "playerctl -p mpv status",
                                printOutput = false,
                                printErrors = false
                            ) == "Playing"
                        ) {
                            //val current = commandRunner.runCommand("playerctl -p mpv metadata --format '{{ title }}'")
                            //playStateListener.onNewSongPlaying("mpv", commandRunner.runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", printErrors = false))
                        } else {
                            val current = commandRunner.runCommand(
                                "playerctl -p mpv metadata --format '{{ title }}'",
                                printOutput = false,
                                printErrors = false
                            )
                            /*
                            if (current != commandRunner.runCommand("youtube-dl --geo-bypass -s -e \"$currentSong\"", printErrors = false) && !current.contains("v=${currentSong.substringAfter("v=")}") && current == "No players found"){
                                playStateListener.onSongEnded("mpv", currentSong)
                            }
                            */
                            if (!current.contains("v=") && !current.contains(
                                    commandRunner.runCommand(
                                        "youtube-dl --geo-bypass -s -e \"$currentSong\"",
                                        printOutput = false, printErrors = false
                                    )
                                ) && current == "No players found"
                            ) {
                                playStateListener.onSongEnded("mpv", Link(currentSong))
                                break
                            }
                        }
                    }
                    Thread.sleep(990)
                }
            }.run()
        }
        if (!youTubeListenerThread.isAlive) {
            youTubeListenerThread.start()
        }
    }

    private fun startSoundCloudMonitor() {
        val soundCloudListenerThread = Thread {
            Runnable {
                while (songQueueActive.get()) {
                    if (shouldMonitorSc.get()) {
                        if (commandRunner.runCommand(
                                "playerctl -p mpv status",
                                printOutput = false,
                                printErrors = false
                            ) == "Playing"
                        ) {
                            //song is playing, do nothing
                        } else {
                            val current = commandRunner.runCommand(
                                "playerctl -p mpv metadata --format '{{ title }}'",
                                printOutput = false,
                                printErrors = false
                            )
                            if (!current.contains("soundcloud.com") && !current.contains(
                                    commandRunner.runCommand(
                                        "youtube-dl -s -e \"$currentSong\"",
                                        printOutput = false, printErrors = false
                                    )
                                ) && current == "No players found"
                            ) {
                                playStateListener.onSongEnded("mpv", Link(currentSong))
                                break
                            }
                        }
                    }
                    Thread.sleep(990)
                }
            }.run()
        }
        if (!soundCloudListenerThread.isAlive) {
            soundCloudListenerThread.start()
        }
    }

}

interface PlayStateListener {
    fun onSongEnded(player: String, trackLink: Link)
    fun onNewSongPlaying(player: String, trackLink: Link)
    fun onAdPlaying()
}
