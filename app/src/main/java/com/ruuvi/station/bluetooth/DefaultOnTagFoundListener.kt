package com.ruuvi.station.bluetooth

import android.content.Context
import com.ruuvi.station.bluetooth.domain.IRuuviTag
import com.ruuvi.station.database.RuuviTagRepository
import com.ruuvi.station.gateway.Http
import com.ruuvi.station.model.TagSensorReading
import com.ruuvi.station.util.AlarmChecker
import com.ruuvi.station.util.Constants
import java.util.Calendar
import java.util.Date
import java.util.HashMap

class DefaultOnTagFoundListener(val context: Context) : RuuviRangeNotifier.OnTagsFoundListener {

    private var lastLogged: MutableMap<String, Long> = HashMap()

    override fun onFoundTags(allTags: List<IRuuviTag>) {
        val favoriteTags = ArrayList<IRuuviTag>()

        allTags.forEach {

            val tag = HumidityCalibration.apply(it)

            saveReading(tag)

            if (tag.favorite) {
                favoriteTags.add(tag)
            }
        }

        if (favoriteTags.size > 0 && RuuviRangeNotifier.gatewayOn) Http.post(favoriteTags, RuuviRangeNotifier.tagLocation, context)

        TagSensorReading.removeOlderThan(24)
    }

    private fun saveReading(ruuviTag: IRuuviTag) {
        var ruuviTag = ruuviTag
        val dbTag = RuuviTagRepository.get(ruuviTag.id)
        if (dbTag != null) {
            ruuviTag = dbTag.preserveData(ruuviTag)
            RuuviTagRepository.update(ruuviTag)
            if (!dbTag.favorite) return
        } else {
            ruuviTag.updateAt = Date()
            RuuviTagRepository.save(ruuviTag)
            return
        }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, -Constants.DATA_LOG_INTERVAL)
        val loggingThreshold = calendar.time.time
        for ((key, value) in lastLogged!!) {
            if (key == ruuviTag.id && value > loggingThreshold) {
                return
            }
        }
        ruuviTag.id?.let { id ->
            lastLogged[id] = Date().time
        }
        val reading = TagSensorReading(ruuviTag)
        reading.save()
        AlarmChecker.check(ruuviTag, context)
    }
}