package com.sjianjun.reader.module.watermarkcamera

import android.location.Location
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tencent.mmkv.MMKV
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

class WatermarkCameraViewModel : ViewModel() {

    // 时间模式
    enum class TimeMode { AUTO, MANUAL, RANGE }

    private val prefs = MMKV.mmkvWithID("watermark_camera_settings")

    // 水印数据
    val dateText = MutableLiveData<String>()
    val dayOfWeekText = MutableLiveData<String>()
    val timeText = MutableLiveData<String>()
    val gpsText = MutableLiveData<String>()
    val addressText = MutableLiveData<String>()

    // GPS模式标签（显示"手动"或"自动"）
    val gpsModeLabelText = MutableLiveData<String>()

    // 当前日期
    var currentDate: LocalDate = LocalDate.now()
        private set

    // 是否手动编辑过
    var isDateManual = false
    var isGpsManual: Boolean
        get() = prefs.decodeBool("isGpsManual", false)
        set(value) {
            prefs.encode("isGpsManual", value)
            gpsModeLabelText.value = if (value) "手动" else "自动"
        }
    var isAddressManual = false

    // 日期自增
    var dateAutoIncrement: Boolean
        get() = prefs.decodeBool("dateAutoIncrement", false)
        set(value) { prefs.encode("dateAutoIncrement", value) }

    // 时间模式
    var timeMode: TimeMode
        get() = TimeMode.entries.getOrNull(prefs.decodeInt("timeMode", 0)) ?: TimeMode.AUTO
        set(value) { prefs.encode("timeMode", value.ordinal) }

    // 手动时间
    var manualHour: Int
        get() = prefs.decodeInt("manualHour", 0)
        set(value) { prefs.encode("manualHour", value) }
    var manualMinute: Int
        get() = prefs.decodeInt("manualMinute", 0)
        set(value) { prefs.encode("manualMinute", value) }

    // 范围时间
    var rangeStartHour: Int
        get() = prefs.decodeInt("rangeStartHour", 5)
        set(value) { prefs.encode("rangeStartHour", value) }
    var rangeStartMinute: Int
        get() = prefs.decodeInt("rangeStartMinute", 30)
        set(value) { prefs.encode("rangeStartMinute", value) }
    var rangeEndHour: Int
        get() = prefs.decodeInt("rangeEndHour", 8)
        set(value) { prefs.encode("rangeEndHour", value) }
    var rangeEndMinute: Int
        get() = prefs.decodeInt("rangeEndMinute", 0)
        set(value) { prefs.encode("rangeEndMinute", value) }

    // 经纬度（自动模式下不持久化，手动模式下持久化）
    var latitude = 0.0
    var longitude = 0.0

    // 拍照计数
    var photoCount = 0

    init {
        // 恢复保存的地址
        val savedAddress = prefs.decodeString("address", "") ?: ""
        if (savedAddress.isNotEmpty()) {
            addressText.value = savedAddress
        }

        // 恢复GPS模式标签
        gpsModeLabelText.value = if (isGpsManual) "手动" else "自动"

        // 手动模式下恢复保存的GPS文本和经纬度
        if (isGpsManual) {
            val savedGps = prefs.decodeString("gpsText", "") ?: ""
            if (savedGps.isNotEmpty()) {
                gpsText.value = savedGps
            }
            latitude = prefs.decodeDouble("latitude", 0.0)
            longitude = prefs.decodeDouble("longitude", 0.0)
        }

        refreshDate(LocalDate.now())
        refreshTime()
    }

    /** 刷新日期 */
    fun refreshDate(date: LocalDate) {
        currentDate = date
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
        dateText.value = dateStr
        dayOfWeekText.value = dayOfWeek
    }

    /** 刷新时间（根据当前时间模式） */
    fun refreshTime() {
        val timeStr = when (timeMode) {
            TimeMode.AUTO -> {
                val now = LocalTime.now()
                String.format(Locale.US, "%02d:%02d", now.hour, now.minute)
            }
            TimeMode.MANUAL -> {
                String.format(Locale.US, "%02d:%02d", manualHour, manualMinute)
            }
            TimeMode.RANGE -> {
                generateRandomTimeInRange()
            }
        }
        timeText.value = timeStr
    }

    /** 在范围内生成随机时间 */
    private fun generateRandomTimeInRange(): String {
        val startMinutes = rangeStartHour * 60 + rangeStartMinute
        val endMinutes = rangeEndHour * 60 + rangeEndMinute
        if (endMinutes <= startMinutes) {
            return String.format(Locale.US, "%02d:%02d", rangeStartHour, rangeStartMinute)
        }
        val randomMinutes = Random.nextInt(startMinutes, endMinutes + 1)
        val h = randomMinutes / 60
        val m = randomMinutes % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    /** 更新 GPS 信息（来自系统定位） */
    fun updateLocation(location: Location) {
        // 手动模式下不更新经纬度，保持手动设置的值
        if (isGpsManual) return
        latitude = location.latitude
        longitude = location.longitude
        // 自动模式：更新显示文本，不持久化
        gpsText.value = formatGpsText(latitude, longitude)
    }

    /** 格式化经纬度文本 */
    private fun formatGpsText(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%s%.4f° %s%.4f°",
            latDir, Math.abs(lat),
            lngDir, Math.abs(lng)
        )
    }

    /** 更新地址信息 */
    fun updateAddress(address: String) {
        if (!isAddressManual) {
            addressText.value = address
            prefs.encode("address", address)
        }
    }

    /** 保存地址（外部调用） */
    fun saveAddress(address: String) {
        addressText.value = address
        prefs.encode("address", address)
    }

    /** 手动编辑GPS文本并持久化保存 */
    fun saveGpsText(gps: String) {
        isGpsManual = true
        gpsText.value = gps
        prefs.encode("gpsText", gps)
        // 从文本中解析经纬度，格式如: N30.5700° E104.0700°
        parseGpsText(gps)
        prefs.encode("latitude", latitude)
        prefs.encode("longitude", longitude)
    }

    /** 从GPS文本中解析经纬度值 */
    private fun parseGpsText(gps: String) {
        try {
            // 匹配格式: N30.5700° E104.0700° 或 S30.5700° W104.0700°
            val regex = Regex("""([NS])(\d+\.?\d*)°?\s*([EW])(\d+\.?\d*)°?""")
            val match = regex.find(gps) ?: return
            val latDir = match.groupValues[1]
            val latVal = match.groupValues[2].toDoubleOrNull() ?: return
            val lngDir = match.groupValues[3]
            val lngVal = match.groupValues[4].toDoubleOrNull() ?: return
            latitude = if (latDir == "S") -latVal else latVal
            longitude = if (lngDir == "W") -lngVal else lngVal
        } catch (_: Exception) {
        }
    }

    /** 切换到自动GPS模式，清除持久化数据 */
    fun resetToAutoGps() {
        isGpsManual = false
        // 清除持久化的GPS数据
        prefs.remove("gpsText")
        prefs.remove("latitude")
        prefs.remove("longitude")
        // 清空经纬度和显示
        clearGpsDisplay()
    }

    /** 清除GPS显示和内存中的经纬度，用于自动定位前重置状态 */
    fun clearGpsDisplay() {
        latitude = 0.0
        longitude = 0.0
        gpsText.value = "定位中..."
    }

    /** 拍照后处理日期自增 */
    fun onPhotoTaken() {
        photoCount++
        if (dateAutoIncrement) {
            currentDate = currentDate.plusDays(1)
            refreshDate(currentDate)
        }
        // 范围模式下每次拍照刷新时间
        if (timeMode == TimeMode.RANGE) {
            refreshTime()
        }
    }

    /** 获取当前水印信息用于绘制（经纬度不在水印中显示，仅保存到EXIF） */
    fun getWatermarkLines(): List<String> {
        val lines = mutableListOf<String>()
        val time = timeText.value ?: ""
        val date = dateText.value ?: ""
        val dayOfWeek = dayOfWeekText.value ?: ""
        if (time.isNotEmpty() || date.isNotEmpty()) {
            // 时间和日期组合为一行，用于drawWatermark
            val datePart = if (date.isNotEmpty() && dayOfWeek.isNotEmpty()) "$date $dayOfWeek" else date
            lines.add("$time|$datePart")
        }
        val addr = addressText.value ?: ""
        if (addr.isNotEmpty()) {
            lines.add(addr)
        }
        return lines
    }
}
