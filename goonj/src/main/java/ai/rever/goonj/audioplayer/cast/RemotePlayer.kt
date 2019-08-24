package ai.rever.goonj.audioplayer.cast

import ai.rever.goonj.BuildConfig
import ai.rever.goonj.audioplayer.analytics.*
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.mediarouter.media.MediaItemStatus
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaSessionStatus
import androidx.mediarouter.media.RemotePlaybackClient
import ai.rever.goonj.audioplayer.interfaces.AudioPlayer
import ai.rever.goonj.audioplayer.models.Samples
import ai.rever.goonj.audioplayer.util.SingletonHolder
import ai.rever.goonj.audioplayer.util.mCURRENT_PLAYING_ITEM
import ai.rever.goonj.audioplayer.util.mIsPlaying
import android.net.Uri
import androidx.core.net.toUri
import com.google.android.gms.cast.MediaMetadata
import java.util.ArrayList
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import java.lang.ref.WeakReference

class RemotePlayer constructor (var contextWeakReference: WeakReference<Context>) : AudioPlayer() {

    companion object : SingletonHolder<RemotePlayer, WeakReference<Context>>(::RemotePlayer)

    private var TAG = "REMOTE_PLAYER"
    private var DEBUG = BuildConfig.DEBUG
    private var mRoute: MediaRouter.RouteInfo? = null
    private var mEnqueuePending: Boolean = false
    private val mTempQueue = ArrayList<Samples.Track>()

    private var mClient: RemotePlaybackClient? = null
    private val mContext: Context? get() = contextWeakReference.get()

    override fun isRemotePlayback(): Boolean {
        return true
    }

    override fun isQueuingSupported(): Boolean {
        return mClient?.isQueuingSupported ?: false
    }

    override fun connect(route: MediaRouter.RouteInfo?) {
        mRoute = route
        mClient = RemotePlaybackClient(mContext, route)
//        mClient?.setStatusCallback(mStatusCallback)

        if (DEBUG) {
            Log.d(
                TAG, "connected to: " + route
                        + ", isRemotePlaybackSupported: " + mClient?.isRemotePlaybackSupported
                        + ", isQueuingSupported: " + mClient?.isQueuingSupported
            )
        }
    }

    override fun release() {
        mClient?.release()

        if (DEBUG) {
            Log.d(TAG, "released.")
        }
    }

    override fun startNewSession() {
        Log.d(TAG,"Start new session")
    }

    override fun play(item: Samples.Track) {
        if (DEBUG) {
            Log.d(TAG, "play: item=$item")
        }

        mCURRENT_PLAYING_ITEM.value = item
        mClient?.setStatusCallback(mStatusCallback)
        mClient?.play(item.url.toUri(), "audio/mp3", null, 0, null, object : RemotePlaybackClient.ItemActionCallback() {
            override fun onResult(
                data: Bundle?,
                sessionId: String?,
                sessionStatus: MediaSessionStatus?,
                itemId: String?,
                itemStatus: MediaItemStatus?
            ) {
                logStatus("play: succeeded", sessionId, sessionStatus, itemId, itemStatus)
                item.remoteItemId = itemId
                if (item.position > 0) {
                    seekInternal(item)
                }
                if (item.state == MediaItemStatus.PLAYBACK_STATE_PAUSED) {
                    pause()
                }

                setMediaMetadata(item)
                mCallback.onPlaylistChanged()
                mIsPlaying.value = true
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("play: failed", error , code)
            }
        })
    }

    private fun setMediaMetadata(item: Samples.Track){
        val musicMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)

        musicMetadata.putString(MediaMetadata.KEY_TITLE, item.title)
        musicMetadata.putString(MediaMetadata.KEY_ARTIST, item.artist)
        musicMetadata.addImage(WebImage(Uri.parse(item.albumArtUrl)))

        mContext?.let{
            val castSession = CastContext.getSharedInstance(it).sessionManager.currentCastSession
            try {
                val mediaInfo = MediaInfo.Builder(item.url)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("audio/*")
                    .setMetadata(musicMetadata)
                    .build()
                val remoteMediaClient = castSession.remoteMediaClient

                val mediaLoadRequestData = MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()

                remoteMediaClient.load(mediaLoadRequestData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    override fun seekTo(positionMs: Long) {
        mCURRENT_PLAYING_ITEM.value?.let {
            getStatus(it, true, positionMs)
        }
    }

    override fun getStatus(item: Samples.Track, seek: Boolean, positionMs: Long) {

        if (mClient?.hasSession() != true || item.remoteItemId == null) {
            // if session is not valid or item id not assigend yet.
            // just return, it's not fatal
            return
        }

        if (DEBUG) {
            Log.d(TAG, "getStatus: item=$item, seek=$seek")
        }
        mClient?.getStatus(item.remoteItemId, null, object : RemotePlaybackClient.ItemActionCallback() {
            override fun onResult(
                data: Bundle?,
                sessionId: String?,
                sessionStatus: MediaSessionStatus?,
                itemId: String?,
                itemStatus: MediaItemStatus?
            ) {
                logStatus("getStatus: succeeded", sessionId, sessionStatus, itemId, itemStatus)
                val state = itemStatus?.playbackState
                if (state == MediaItemStatus.PLAYBACK_STATE_PLAYING
                    || state == MediaItemStatus.PLAYBACK_STATE_PAUSED
                    || state == MediaItemStatus.PLAYBACK_STATE_PENDING
                ) {
                    item.state = state
                    item.position = itemStatus.contentPosition
                    item.duration = itemStatus.contentDuration
                    item.timestamp = itemStatus.timestamp
                }
                if (seek) {
                    if((item.position + positionMs) < 0 ) {
                        item.position = 0
                    }
                    else if((item.position + positionMs) < item.duration ) {
                        item.position = item.position + positionMs
                    } else {
                        item.position = item.duration - 1000
                    }
                    seekInternal(item)
                }
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("getStatus: failed", error, code)
//                if (seek) {
//                    mCallback.onPlaylistReady()
//                }
            }
        })
    }

    override fun pause() {
        if (mClient?.hasSession() != true) {
            // ignore if no session
            return
        }
        if (DEBUG) {
            Log.d(TAG, "pause")
        }


        mClient?.pause(null, object : RemotePlaybackClient.SessionActionCallback() {
            override fun onResult(data: Bundle?, sessionId: String?, sessionStatus: MediaSessionStatus?) {

                logStatus("pause: succeeded", sessionId, sessionStatus, null, null)
                mCallback.onPlaylistChanged()

                mIsPlaying.value = false

            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("pause: failed", error, code)
            }
        })
    }

    override fun resume() {
        // Todo: find following code meaning
        if (mClient?.hasSession() != true) {
            // ignore if no session
            return
        }
        if (DEBUG) {
            Log.d(TAG, "resume")
        }
        mClient?.resume(null, object : RemotePlaybackClient.SessionActionCallback() {
            override fun onResult(data: Bundle?, sessionId: String?, sessionStatus: MediaSessionStatus?) {
                logStatus("resume: succeeded", sessionId, sessionStatus, null, null)
                mCallback.onPlaylistChanged()

                mIsPlaying.value = true
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("resume: failed", error, code)
            }
        })
    }

    override fun stop() {
        if (mClient?.hasSession() != true) {
            // ignore if no session
            return
        }
        if (DEBUG) {
            Log.d(TAG, "stop")
        }
        mClient?.stop(null, object : RemotePlaybackClient.SessionActionCallback() {
            override fun onResult(data: Bundle?, sessionId: String?, sessionStatus: MediaSessionStatus?) {
                logStatus("stop: succeeded", sessionId, sessionStatus, null, null)
                if (mClient?.isSessionManagementSupported == true) {
                    endSession()
                }
                mCallback.onPlaylistChanged()
                mIsPlaying.value = false
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("stop: failed", error, code)
            }
        })
    }

    override fun enqueue(item: Samples.Track) {
        throwIfQueuingUnsupported()

        if (mClient?.hasSession() != true && !mEnqueuePending) {
            mEnqueuePending = true
            if (mClient?.isSessionManagementSupported == true) {
                startSession(item)
            } else {
                enqueueInternal(item)
            }
        } else if (mEnqueuePending) {
            mTempQueue.add(item)
        } else {
            enqueueInternal(item)
        }
    }

    override fun remove(iid: String): Samples.Track? {
        throwIfNoSession()
        throwIfQueuingUnsupported()

        if (DEBUG) {
            Log.d(TAG, "remove: itemId=$iid")
        }
        mClient?.remove(iid, null, object : RemotePlaybackClient.ItemActionCallback() {
            override fun onResult(
                data: Bundle?,
                sessionId: String?,
                sessionStatus: MediaSessionStatus?,
                itemId: String?,
                itemStatus: MediaItemStatus?
            ) {
                logStatus("remove: succeeded", sessionId, sessionStatus, itemId, itemStatus)
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("remove: failed", error, code)
            }
        })

        return null
    }

    override fun setPlaylist(playlist: List<Samples.Track>) {

    }

    override fun setVolume(volume: Float) {
        Log.d(TAG,"Volume")
    }

    override fun getTrackPosition(): Long? {
        return null
    }

    private fun enqueueInternal(item: Samples.Track) {
        throwIfQueuingUnsupported()

        if (DEBUG) {
            Log.d(TAG, "enqueue: item=$item")
        }
        mClient?.enqueue(item.url.toUri(), "audio/*", null, 0, null, object : RemotePlaybackClient.ItemActionCallback() {
            override fun onResult(
                data: Bundle?,
                sessionId: String?,
                sessionStatus: MediaSessionStatus?,
                itemId: String?,
                itemStatus: MediaItemStatus?
            ) {
                logStatus("enqueue: succeeded", sessionId, sessionStatus, itemId, itemStatus)
                item.remoteItemId = itemId
                if (item.position > 0) {
                    seekInternal(item)
                }
                if (item.state == MediaItemStatus.PLAYBACK_STATE_PAUSED) {
                    pause()
                }
                if (mEnqueuePending) {
                    mEnqueuePending = false
                    for (temp in mTempQueue) {
                        enqueueInternal(temp)
                    }
                    mTempQueue.clear()
                }
                mCallback.onPlaylistChanged()
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("enqueue: failed", error, code)
                mCallback.onPlaylistChanged()
            }
        })
    }

    private fun seekInternal(item: Samples.Track) {
        throwIfNoSession()

        if (DEBUG) {
            Log.d(TAG, "seek: item=$item")
        }
        mClient?.seek(item.remoteItemId, item.position, null, object : RemotePlaybackClient.ItemActionCallback() {
            override fun onResult(
                data: Bundle?,
                sessionId: String?,
                sessionStatus: MediaSessionStatus?,
                itemId: String?,
                itemStatus: MediaItemStatus?
            ) {
                logStatus("seek: succeeded", sessionId, sessionStatus, itemId, itemStatus)
                mCallback.onPlaylistChanged()
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("seek: failed", error, code)
            }
        })
    }

    private fun startSession(item: Samples.Track) {
        mClient?.startSession(null, object : RemotePlaybackClient.SessionActionCallback() {
            override fun onResult(data: Bundle?, sessionId: String?, sessionStatus: MediaSessionStatus?) {
                logStatus("startSession: succeeded", sessionId, sessionStatus, null, null)
                enqueueInternal(item)
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
                logError("endSession: failed", error, code)
            }
        })
    }

    private fun endSession() {
        mClient?.endSession(null, object : RemotePlaybackClient.SessionActionCallback() {
            override fun onResult(data: Bundle?, sessionId: String?, sessionStatus: MediaSessionStatus?) {
                logStatus("endSession: succeeded", sessionId, sessionStatus, null, null)
            }

            override fun onError(error: String?, code: Int, data: Bundle?) {
               logError("endSession: failed", error, code)
            }
        })
    }

    private fun logStatus(
        message: String,
        sessionId: String?, sessionStatus: MediaSessionStatus?,
        itemId: String?, itemStatus: MediaItemStatus?
    ) {
        if (DEBUG) {
            var result = ""
            if (sessionId != null && sessionStatus != null) {
                result += "sessionId=$sessionId, sessionStatus=$sessionStatus"
            }
            if ((itemId != null) and (itemStatus != null)) {
                result += ((if (result.isEmpty()) "" else ", ")
                        + "itemId=" + itemId + ", itemStatus=" + itemStatus)
            }
            Log.d(TAG, "$message: $result")
        }

        val map = mutableMapOf(MESSAGE to message, SESSION_ID to sessionId, SESSION_STATUS to sessionStatus,
            ITEM_ID to itemId, ITEM_STATUS to itemStatus)
        logEventBehaviour(true, PlayerAnalyticsEnum.REMOTE_LOG_STATUS, map)
    }

    private fun logError(message: String?, error: String?, code: Int) {
        val map = mutableMapOf(MESSAGE to message, ERROR_REMOTE to error, ERROR_REMOTE_CODE to code)
        logEventBehaviour(true, PlayerAnalyticsEnum.REMOTE_LOG_ERROR, map)
    }

    private fun throwIfNoSession() {
        if (mClient?.hasSession() != true) {
            throw IllegalStateException("Session is invalid")
        }
    }

    private fun throwIfQueuingUnsupported() {
        if (!isQueuingSupported()) {
            throw UnsupportedOperationException("Queuing is unsupported")
        }
    }

    private val mStatusCallback get() = object : RemotePlaybackClient.StatusCallback() {
        override fun onItemStatusChanged(
            data: Bundle?,
            sessionId: String?,
            sessionStatus: MediaSessionStatus?,
            itemId: String?,
            itemStatus: MediaItemStatus?
        ) {
            if (itemStatus?.playbackState == MediaItemStatus.PLAYBACK_STATE_FINISHED) {
                mCallback.onCompletion()
            } else if (itemStatus?.playbackState == MediaItemStatus.PLAYBACK_STATE_ERROR) {
                mCallback.onError()
            }

            if(itemStatus?.playbackState == MediaItemStatus.PLAYBACK_STATE_PAUSED){
                setPlayerStateRemote(false)
            } else if(itemStatus?.playbackState == MediaItemStatus.PLAYBACK_STATE_PLAYING){
                setPlayerStateRemote(true)
            }
        }

        override fun onSessionStatusChanged(data: Bundle?, sessionId: String?, sessionStatus: MediaSessionStatus?) {
            logStatus("onSessionStatusChanged", sessionId, sessionStatus, null, null)
            mCallback.onPlaylistChanged()
        }

        override fun onSessionChanged(sessionId: String?) {
           if (DEBUG) {
                Log.d(TAG, "onSessionChanged: sessionId=$sessionId")
           }
        }
    }

    private fun setPlayerStateRemote(isRemotePlaying : Boolean){
        val map = mutableMapOf(IS_REMOTE_PLAYING to isRemotePlaying)
        logEventBehaviour(true, PlayerAnalyticsEnum.SET_PLAYER_STATE_REMOTE, map)
        mIsPlaying.value = isRemotePlaying
    }

}