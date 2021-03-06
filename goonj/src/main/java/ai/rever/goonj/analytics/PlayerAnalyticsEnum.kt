package ai.rever.goonj.analytics

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

enum class PlayerAnalyticsEnum{
    ON_PLAYER_STATE_CHANGED,
    ON_SEEK_PROCESSED,
    ON_PLAYER_ERROR,
    ON_SEEK_STARTED,
    ON_LOADING_CHANGED,
    ON_VOLUME_CHANGED,
    ON_LOAD_COMPLETED,
    ON_METADATA,
    ON_PLAYBACK_PARAMETERS_CHANGED,
    ON_TRACKS_CHANGED,
    ON_POSITION_DISCONTINUITY,
    ON_REPEAT_MODE_CHANGED,
    ON_SHUFFLE_MODE_ENABLED_CHANGED,
    ON_TIMELINE_CHANGED,
    REMOTE_LOG_STATUS,
    REMOTE_LOG_ERROR,
    SET_PLAYER_STATE_REMOTE
}

const val EVENT_TIME = "AnalyticsListener.EventTime"
const val ERROR = "ExoPlaybackException"
const val IS_LOADING = "IsLoading"
const val VOLUME = "Volume"
const val LOAD_EVENT_INFO = "MediaSourceEventListener.LoadEventInfo"
const val MEDIA_LOAD_EVENT = "MediaSourceEventListener.MediaLoadData"
const val METADATA = "Metadata"
const val PLAY_WHEN_READY = "PlayWhenReady"
const val PLAYBACK_STATE = "PlaybackState"
const val ON_PLAYBACK_PARAMETERS_CHANGED = "onPlaybackParametersChanged"
const val PLAYBACK_PARAMETERS = "PlaybackParameters"
const val TRACK_GROUPS = "TrackGroups"
const val TRACK_SELECTIONS ="TrackSelections"
const val REASON = "Reason"
const val REPEAT_MODE = "RepeatMode"
const val SHUFFLE_MODE_ENABLED = "ShuffleModeEnabled"
const val TIMELINE = "Timeline"
const val MANIFEST = "Manifest"
const val MESSAGE = "Message"
const val SESSION_ID = "SessionID"
const val SESSION_STATUS = "SessionStatus"
const val ITEM_ID = "ItemID"
const val ITEM_STATUS = "ItemStatus"
const val ERROR_REMOTE = "ErrorRemote"
const val ERROR_REMOTE_CODE = "ErrorRemoteCode"
const val IS_REMOTE_PLAYING = "IsRemotePlaying"

data class GoonjAnalyticsModel(
    val isRemote : Boolean,
    val type : PlayerAnalyticsEnum,
    val parameter: Map<String, Any?>
){
    override fun toString() : String{

        val playerType = if(isRemote){
            "REMOTE"
        } else {
            "LOCAL"
        }
        return "$playerType ${type.name}  $parameter"
    }

}

object GoonjAnalytics {

    private val analyticsSubjectBehaviour = BehaviorSubject.create<GoonjAnalyticsModel>()
    val analyticsObservable get() = (analyticsSubjectBehaviour as Observable<GoonjAnalyticsModel>)

    fun logEvent(isRemote: Boolean, behaviour: PlayerAnalyticsEnum, data: Map<String, Any?>) {
        analyticsSubjectBehaviour.onNext(
            GoonjAnalyticsModel(
                isRemote,
                behaviour,
                data
            )
        )
    }
}
