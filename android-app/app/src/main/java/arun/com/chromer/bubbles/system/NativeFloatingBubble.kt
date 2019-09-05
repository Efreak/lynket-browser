package arun.com.chromer.bubbles.system

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import arun.com.chromer.bubbles.FloatingBubble
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import com.jakewharton.rxrelay2.PublishRelay
import dev.arunkumar.android.rxschedulers.SchedulerProvider
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.BackpressureStrategy
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

data class BubbleLoadData(
        val website: Website,
        val fromMinimize: Boolean,
        val fromAmp: Boolean,
        val incognito: Boolean,
        val contextRef: WeakReference<Context?> = WeakReference(null),
        val icon: Bitmap? = null,
        val color: Int = Constants.NO_COLOR
)

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("CheckResult")
@Singleton
class NativeFloatingBubble
@Inject
constructor(
        private val application: Application,
        private val schedulerProvider: SchedulerProvider,
        private val websiteRepository: WebsiteRepository,
        private val bubbleNotificationUtil: BubbleNotificationUtil
) : FloatingBubble {

    private val loadQueue = PublishRelay.create<BubbleLoadData>()

    init {
        loadQueue.toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(schedulerProvider.pool)
                .flatMapSingle(bubbleNotificationUtil::showBubbles)
                .flatMap { bubbleData ->
                    RxJavaInterop.toV2Flowable(websiteRepository.getWebsite(bubbleData.website.url))
                            .subscribeOn(schedulerProvider.io)
                            .map { website ->
                                val iconColorPair = websiteRepository.getWebsiteIconWithPlaceholderAndColor(website)
                                bubbleData.copy(
                                        website = website,
                                        icon = iconColorPair.first,
                                        color = iconColorPair.second
                                )
                            }.onErrorReturnItem(bubbleData)
                }
                .observeOn(schedulerProvider.pool)
                .flatMapSingle(bubbleNotificationUtil::showBubbles)
                .doOnError(Timber::e)
                .subscribe()
    }

    override fun openBubble(
            url: String,
            fromMinimize: Boolean,
            fromAmp: Boolean,
            incognito: Boolean,
            context: Context?
    ) = loadQueue.accept(BubbleLoadData(
            Website(url),
            fromMinimize,
            fromAmp,
            incognito,
            WeakReference(context)
    ))
}