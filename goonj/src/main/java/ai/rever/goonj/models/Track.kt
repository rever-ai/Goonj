package ai.rever.goonj.models

import ai.rever.goonj.R
import android.content.Context
import androidx.annotation.DrawableRes
import android.graphics.Bitmap
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.mediarouter.media.MediaItemStatus
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.android.exoplayer2.offline.Download
import kotlinx.android.parcel.Parcelize


val SAMPLES = arrayOf(
    Track(
        "https://storage.googleapis.com/automotive-media/Talkies.mp3",
        //"https://raw.githubusercontent.com/rever-ai/SampleMusic/master/David_H_Porter_-_Mozarts_Rondo_No_3_in_A_Minor_K_511.mp3",
        "audio_1",
        "Talkies",
        //"If it talks like a duck and walks like a duck.",
        "One",
        imageUrl = "https://img.discogs.com/Bss063QHQ7k0sRwejSQWTJ-iKGI=/fit-in/600x573/filters:strip_icc():format(jpeg):mode_rgb():quality(90)/discogs-images/R-900642-1182343816.jpeg.jpg"
    ), Track(
        "https://storage.googleapis.com/automotive-media/Jazz_In_Paris.mp3",
        //"https://raw.githubusercontent.com/rever-ai/SampleMusic/master/Menstruation_Sisters_-_14_-_top_gun.mp3",
        "audio_2",
        "Jazz in Paris",
        //"Jazz for the masses",
        "Two",
        imageUrl = "https://i.ebayimg.com/images/g/MMgAAOSwXi9b6BJ3/s-l640.jpg"
    ), Track(
        "https://storage.googleapis.com/automotive-media/The_Messenger.mp3",
        //"https://raw.githubusercontent.com/rever-ai/SampleMusic/master/Manueljgrotesque_-_24_-_grotesque25.mp3",
        "audio_3",
        "The messenger",
        //"Hipster guide to London",
        "Three",
        imageUrl = "https://www.mobygames.com/images/covers/l/507031-the-messenger-nintendo-switch-front-cover.jpg"
    ), Track(
        "https://raw.githubusercontent.com/rever-ai/SampleMusic/master/Hard_TON_-_07_-_Choc-ice_Dance.mp3",
        "audio_4",
        "Audio 4",
        //"Hipster guide to London",
        "Four",
        imageUrl = "https://www.mobygames.com/images/covers/l/507031-the-messenger-nintendo-switch-front-cover.jpg"
    ), Track(
        "https://raw.githubusercontent.com/rever-ai/SampleMusic/master/Big_Blood_-_01_-_Bah-num.mp3",
        "audio_5",
        "Audio 5",
        //"Hipster guide to London",
        "Five",
        imageUrl = "https://www.mobygames.com/images/covers/l/507031-the-messenger-nintendo-switch-front-cover.jpg"
    ), Track(
        "https://raw.githubusercontent.com/rever-ai/SampleMusic/master/Black_Ant_-_08_-_realest_year_9.mp3",
        "audio_6",
        "Audio 6",
        //"Hipster guide to London",
        "Six",
        imageUrl = "https://www.mobygames.com/images/covers/l/507031-the-messenger-nintendo-switch-front-cover.jpg"
    )
)

//@Entity(tableName = "download_table")
//class Track(
//    val url: String,
//    @PrimaryKey
//    val id: String,
//    val title: String,
//    val artistName: String,
//    val albumArtUrl : String? = ""
//) : Serializable {
//    var downloadedState = Download.STATE_QUEUED
//    var state = MediaItemStatus.PLAYBACK_STATE_PENDING
//    var index : Int = 0
//    var position: Long = 0
//    var duration: Long = 0
//    var remoteItemId: String? = null
//    var bitmapResource: Int = R.mipmap.ic_album_art
//
//    @Ignore
//    var bitmap: Bitmap? = null
//
//    override fun toString(): String {
//        return "$title Description: $artistName DURATION: $duration INDEX: $index state: $state"
//    }
//}


@Entity(tableName = "download_table")
@Parcelize
data class Track (var url: String = "",
                  @PrimaryKey
                  var id: String = "",
                  var title: String = "",
                  var artistName: String = "",
                  @Ignore
                  var extras: Bundle? = null,
                  var imageUrl: String? = null,
                  var downloadedState: Int = Download.STATE_QUEUED,
                  @Ignore
                  val currentData: CurrentTrackData = CurrentTrackData()
): Parcelable


@Parcelize
data class CurrentTrackData(var state: Int = MediaItemStatus.PLAYBACK_STATE_PENDING,
                            var index: Int = 0,
                            var position: Long = 0,
                            var duration: Long = 0,
                            var remoteItemId: String? = null): Parcelable



fun getMediaDescription(context: Context?, track: Track): MediaDescriptionCompat {
    val extras = Bundle()
    val mediaDescriptionBuilder =  MediaDescriptionCompat.Builder()
        .setMediaId(track.id)
        .setTitle(track.title)
        .setDescription(track.artistName)
        .setExtras(extras)

    val bitmap = getBitmap(context, R.mipmap.ic_album_art)
    extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
    extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)

    mediaDescriptionBuilder.setIconBitmap(bitmap)

//    appContext?.let {
//        if(track.currentData.bitmap != null){
//            mediaDescriptionBuilder.setIconBitmap(track.currentData.bitmap)
//        } else {
//            val bitmap = getBitmap(appContext, track.bitmapResource)
//            extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
//            extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
//
//            mediaDescriptionBuilder.setIconBitmap(bitmap)
//        }
//    }
    return mediaDescriptionBuilder.build()
}

fun getBitmap(context: Context?, @DrawableRes bitmapResource: Int = R.mipmap.ic_album_art): Bitmap? {
    return context?.let { ContextCompat.getDrawable(it, bitmapResource)?.toBitmap() }
}

