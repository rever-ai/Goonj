package ai.rever.goonj.player.imp

import ai.rever.goonj.Goonj.appContext
import ai.rever.goonj.GoonjPlayerState
import ai.rever.goonj.analytics.*
import ai.rever.goonj.analytics.GoonjAnalytics.logEvent
import android.os.Bundle
import androidx.mediarouter.media.MediaItemStatus
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaSessionStatus
import androidx.mediarouter.media.RemotePlaybackClient
import ai.rever.goonj.manager.GoonjPlayerManager
import ai.rever.goonj.models.Track
import ai.rever.goonj.player.AudioPlayer
import android.os.Handler
import androidx.core.net.toUri
import androidx.mediarouter.media.MediaItemStatus.*
import androidx.mediarouter.media.MediaSessionStatus.*
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import io.reactivex.disposables.CompositeDisposable
import java.lang.ref.WeakReference

internal class RemoteAudioPlayer: AudioPlayer {

    private var player: RemotePlaybackClient? = null
    private var isHandlerRunning = false

    // TODO : Re-create RemoteAudioPlayer with playlist support, for autoplay

    override fun connect(route: MediaRouter.RouteInfo) {
        // TODO check for resume services

        player = RemotePlaybackClient(appContext, route)
        player?.setStatusCallback(statusCallback)
    }

    private val compositeDisposable = CompositeDisposable()

    override fun isDisposed() = compositeDisposable.isDisposed

    override fun dispose() {
        compositeDisposable.dispose()
        player?.release()
    }


    override fun enqueue(track: Track, index: Int) {
        GoonjPlayerManager.playerStateSubject.onNext(GoonjPlayerState.PLAYING)
        play(track)
    }

    private fun play(track: Track) {
        player?.play(track.url.toUri(), "audio/*",
            null, 0, null,
            ItemActionCallbackImp("resume") { itemId, itemStatus ->
                if(!isHandlerRunning) {
                    statusHandler()
                }

                track.state.remoteItemId = itemId

                if (track.state.position > 0) {
                    seekInternal(track)
                }

                if (GoonjPlayerManager.playerStateSubject.value == GoonjPlayerState.PLAYING) {
                    pause()
                }

                track.mediaLoadRequestData?.let {
                    setMediaLoadRequest(it)
                }

                GoonjPlayerManager.currentTrackSubject.onNext(track)
                setStatus(itemStatus, defaultState = GoonjPlayerState.PAUSED)
            })
    }

    private fun setMediaLoadRequest(mediaLoadRequestData: MediaLoadRequestData){

        appContext?.let{
            val castSession = CastContext.getSharedInstance(it).sessionManager.currentCastSession
            try {
                val remoteMediaClient = castSession.remoteMediaClient

                remoteMediaClient.load(mediaLoadRequestData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    override fun seekTo(positionMs: Long) {
        getStatus(true, positionMs)
    }

    fun getStatus(seek: Boolean, positionMs: Long = 0) {
        val track = GoonjPlayerManager.currentTrackSubject.value
        if (player?.hasSession() != true || track?.state?.remoteItemId == null) {
            // if trackList is not valid or track id not assigend yet.
            // just return, it's not fatal
            return
        }

        player?.getStatus(track.state.remoteItemId, null,
            object : ItemActionCallbackImp("getStatus", ::updateTrackPosition) {
                override fun onResult(
                    data: Bundle?,
                    sessionId: String?,
                    sessionStatus: MediaSessionStatus?,
                    itemId: String?,
                    itemStatus: MediaItemStatus?
                ) {
                    super.onResult(data, sessionId, sessionStatus, itemId, itemStatus)
                    if (seek) {
                        when {
                            (positionMs) < 0 ->
                                track.state.position = 0
                            (positionMs) < track.state.duration ->
                                track.state.position = positionMs
                            else -> track.state.position = track.state.duration - 1
                        }
                        seekInternal(track)
                    }

                    setStatus(itemStatus)
                }
            }
        )
    }

    override fun suspend() {
        pause()
    }

    override fun unsuspend() {
        GoonjPlayerManager.currentTrackSubject.value?.let {
            play(it)
        }
    }

    override fun pause() {
        if (player?.hasSession() != true) {
            // ignore if no trackList
            return
        }

        player?.pause(null, SessionActionCallbackImp("pause") { _, _ ->
            setStatus(defaultState = GoonjPlayerState.PAUSED)
        })
    }

    override fun resume() {
        if (player?.hasSession() != true) {
            // ignore if no trackList
            return
        }

        player?.resume(null, SessionActionCallbackImp("resume") {  _, _ ->
            if(!isHandlerRunning) {
                statusHandler()
            }
            setStatus(defaultState = GoonjPlayerState.PLAYING)
        })
    }

    override fun stop() {
        if (player?.hasSession() != true) {
            // ignore if no trackList
            return
        }

        player?.stop(null, SessionActionCallbackImp("stop") { _, _ ->
            setStatus(defaultState = GoonjPlayerState.CANCELED)
        })

    }

    private fun seekInternal(item: Track) {
        if (player?.hasSession() != true) {
            // ignore if no trackList
            return
        }

        player?.seek(item.state.remoteItemId, item.state.position,
            null, ItemActionCallbackImp("seekTo", ::updateTrackPosition))

    }

    private fun setStatus(itemStatus: MediaItemStatus? = null, defaultState: GoonjPlayerState = GoonjPlayerState.IDLE) {
        val map = mutableMapOf(IS_REMOTE_PLAYING to itemStatus)
        logEvent(
            true,
            PlayerAnalyticsEnum.SET_PLAYER_STATE_REMOTE,
            map
        )

        val track = GoonjPlayerManager.currentTrackSubject.value?: return
        if (itemStatus?.apply {
                GoonjPlayerManager.playerStateSubject.onNext(
                    when (playbackState) {
                    PLAYBACK_STATE_BUFFERING -> GoonjPlayerState.BUFFERING
                    PLAYBACK_STATE_PENDING -> GoonjPlayerState.IDLE
                    PLAYBACK_STATE_CANCELED -> GoonjPlayerState.CANCELED
                    PLAYBACK_STATE_FINISHED -> GoonjPlayerState.ENDED
                    PLAYBACK_STATE_PLAYING -> GoonjPlayerState.PLAYING
                    PLAYBACK_STATE_ERROR -> GoonjPlayerState.ERROR
                    PLAYBACK_STATE_PAUSED -> GoonjPlayerState.PAUSED
                    PLAYBACK_STATE_INVALIDATED -> GoonjPlayerState.INVALIDATE
                    else -> GoonjPlayerState.IDLE }
                )
            if (contentPosition > 0) {
                track.state.position = contentPosition
            }
            if (contentDuration > 0) {
                track.state.duration = contentDuration
            }

        } == null) {
            GoonjPlayerManager.playerStateSubject.onNext(defaultState)
        }
    }

    private fun statusHandler(){
        val handler = Handler()
        handler.postDelayed(object: Runnable{
            override fun run() {
                getStatus(false)
                if(GoonjPlayerManager.playerStateSubject.value == GoonjPlayerState.PLAYING) {
                    isHandlerRunning  = true
                    handler.postDelayed(this, 1000)
                } else {
                    isHandlerRunning = false
                }
            }

        },1000)
    }

    private fun updateTrackPosition(itemId: String?, itemStatus: MediaItemStatus?){
        GoonjPlayerManager.currentTrackSubject.value?.let { track ->
            if (track.id == itemId) {
                itemStatus?.contentPosition?.let {
                    track.state.position = it
                }
            }
        }
    }

    private val statusCallback get() = StatusCallback(
        this
    )

    /**
     * Making static inner class insure callback does not memory leak, yet access private method
     */
    class StatusCallback private constructor(): RemotePlaybackClient.StatusCallback() {

        private lateinit var weakPlayer: WeakReference<RemoteAudioPlayer>
        private val player get() = weakPlayer.get()

        constructor(remoteAudioPlayer: RemoteAudioPlayer): this() {
            weakPlayer = WeakReference(remoteAudioPlayer)
        }

        override fun onItemStatusChanged(
            data: Bundle?,
            sessionId: String?,
            sessionStatus: MediaSessionStatus?,
            itemId: String?,
            itemStatus: MediaItemStatus
        ) {
            player?.setStatus(itemStatus)
        }


        override fun onSessionStatusChanged(data: Bundle?, sessionId: String?,
                                            sessionStatus: MediaSessionStatus) {
            when (sessionStatus.sessionState) {
                SESSION_STATE_ACTIVE -> {

                }
                SESSION_STATE_ENDED -> {

                }
                SESSION_STATE_INVALIDATED -> {

                }
            }
        }
    }
}

