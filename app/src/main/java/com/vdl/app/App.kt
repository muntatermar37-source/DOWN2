package com.vdl.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow

class App : Application(), ImageLoaderFactory {
    init { instance = this }
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                ready.value = true
                try { YoutubeDL.getInstance().updateYoutubeDL(this) } catch (_: Exception) { }
            } catch (e: Exception) {
                initError.value = e.message
            }
        }.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build()

    companion object {
        lateinit var instance: App
        val ready = MutableStateFlow(false)
        val initError = MutableStateFlow<String?>(null)
    }
}
