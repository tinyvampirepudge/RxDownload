package zlc.season.rxdownload3.core

import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.schedulers.Schedulers
import zlc.season.rxdownload3.core.DownloadConfig.maxRange
import zlc.season.rxdownload3.core.RangeTmpFile.Segment
import zlc.season.rxdownload3.helper.logd
import zlc.season.rxdownload3.http.HttpCore
import java.io.File


class RangeDownload(mission: RealMission) : DownloadType(mission) {
    private val targetFile = RangeTargetFile(mission)

    private val tmpFile = RangeTmpFile(mission)

    override fun initStatus() {
        val status = tmpFile.currentStatus()

        mission.status = when {
            isFinish() -> Succeed(status)
            else -> Suspend(status)
        }
    }

    override fun getFile(): File? {
        if (isFinish()) {
            return targetFile.realFile()
        }
        return null
    }

    private fun isFinish(): Boolean {
        return tmpFile.isFinish() && targetFile.isFinish()
    }

    override fun download(): Flowable<Status> {
        if (isFinish()) {
            return Flowable.empty()
        }

        val arrays = mutableListOf<Flowable<Status>>()

        if (targetFile.isShadowExists()) {
            tmpFile.checkFile()
        } else {
            targetFile.createShadowFile()
            tmpFile.reset()
        }

        tmpFile.getSegments()
                .filter { !it.isComplete() }
                .forEach { arrays.add(rangeDownload(it)) }

        return Flowable.mergeDelayError(arrays, maxRange)
                .doOnComplete { targetFile.rename() }
    }


    private fun rangeDownload(segment: Segment): Flowable<Status> {
        return Maybe.just(segment)
                .subscribeOn(Schedulers.io())
                .map { "bytes=${it.current}-${it.end}" }
                .doOnSuccess { logd("Range: $it") }
                .flatMap { HttpCore.download(mission, it) }
                .flatMapPublisher { targetFile.save(it, segment, tmpFile) }
    }
}


