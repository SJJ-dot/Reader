package com.sjianjun.reader.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import sjj.alog.Log

enum class MdnsStatus {
    REGISTERED,
    UNREGISTERED,
    REGISTRATION_FAILED,
    UNREGISTRATION_FAILED,
    REGISTERING,
}

object MdnsHelper {
    private val nsdManager = App.app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceName = "ReaderService"
    private val serviceType = "_http._tcp."
    private var registrationListener: NsdManager.RegistrationListener? = null

    val status = MutableLiveData<MdnsStatus>(MdnsStatus.UNREGISTERED)

    fun registerService(port: Int) {
        if (status.value == MdnsStatus.REGISTERED) {
            Log.w("Service is already registered")
            return
        }
        if (status.value == MdnsStatus.REGISTERING) {
            Log.w("Service is already registering")
            return
        }
        status.value = MdnsStatus.REGISTERING
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@MdnsHelper.serviceName
            serviceType = this@MdnsHelper.serviceType
            setPort(port)
        }
        stopService()
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i("Service registered: ${serviceInfo.serviceName}")
                status.postValue(MdnsStatus.REGISTERED)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("Service registration failed: $errorCode")
                status.postValue(MdnsStatus.REGISTRATION_FAILED)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i("Service unregistered: ${serviceInfo.serviceName}")
                status.postValue(MdnsStatus.UNREGISTERED)
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("Service unregistration failed: $errorCode")
                status.postValue(MdnsStatus.UNREGISTRATION_FAILED)
            }
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun stopService() {
        registrationListener?.let { nsdManager.unregisterService(it) }
        registrationListener = null
    }
}