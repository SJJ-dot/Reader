package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.GeoAddress
import com.sjianjun.reader.bean.GeoResponse
import com.sjianjun.reader.http.http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap

object GeoUtil {
    private val cache = ConcurrentHashMap<String, GeoAddress>()
    val geoAddress = MutableLiveData<Map<String, GeoAddress>>(cache)
    private val lockMap = ConcurrentHashMap<String, String>()
    private val geoScope = CoroutineScope(Dispatchers.IO)

    fun locationKey(latitude: Double, longitude: Double): String {
        return "%.6f,%.6f".format(latitude, longitude)
    }

    /**
     * Returns a human-readable address for the given coordinates.
     * Behavior:
     *  - If a cached result exists, return it immediately.
     *  - If a request for the same coordinates is in-flight, suspend until it completes.
     *    - If the in-flight request succeeded, return the cached result.
     *    - If the in-flight request failed, attempt a fresh request.
     */
    fun getLocationText(latitude: Double?, longitude: Double?): GeoAddress? {
        val latStr = latitude?.let { "%.6f".format(it) }
        val lonStr = longitude?.let { "%.6f".format(it) }
        if (latitude == null || longitude == null) {
            Log.i("Invalid coordinates: lat=$latStr, lon=$lonStr")
            return null
        }
        val locationKey = locationKey(latitude, longitude)
        val address = cache[locationKey]
        if (address != null) {
            return address
        }

        geoScope.launch {
            synchronized(lockMap.getOrPut(locationKey) { locationKey }) {
                try {
                    if (!cache.containsKey(locationKey)) {
                        val url = "http://api.tianditu.gov.cn/geocoder?postStr={'lon':${lonStr},'lat':${latStr},'ver':1}&type=geocode&tk=${BuildConfig.GEO_KEY}"
                        val resp = Gson().fromJson(http.get(url).body, GeoResponse::class.java)
                        Log.e("${locationKey}: $resp")
                        if (resp.status == "0") {
                            cache[locationKey] = resp.result!!
                            geoAddress.postValue(cache.toMap())
                        }
                    }
                } catch (e: Exception) {
                    toast("获取地址失败: ${e.message}")
                }
            }
        }
        return null

    }
}

