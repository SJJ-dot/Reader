package com.sjianjun.reader.mqtt

import android.annotation.SuppressLint
import android.content.Context
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import sjj.alog.Log
import java.util.UUID

object MqttUtil {

    private const val TOPIC_ONLINE = "reader/online"


    @SuppressLint("StaticFieldLeak")
    private var mqttAndroidClient: MqttAndroidClient? = null

    fun connect(context: Context) {
        val serverURI = BuildConfig.MQTT_SERVER_URI

        // use a stable clientId stored in SharedPreferences to allow session persistence
        val clientId = globalConfig.mqttClientId ?: UUID.randomUUID().toString().replace("-", "").also {
            globalConfig.mqttClientId = it
        }

        mqttAndroidClient = MqttAndroidClient(context.applicationContext, serverURI, clientId)
        mqttAndroidClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) {
                    Log.i("Reconnected to : " + serverURI)
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic()
                } else {
                    Log.i("Connected to: " + serverURI)
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.i("The Connection was lost.")
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String?, message: MqttMessage) {
                try {
                    handleIncomingMessage(topic ?: return, message)
                } catch (e: Exception) {
                    Log.e("Error handling incoming MQTT message: ${e.message}")
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })

        recursiveConnect()
        startHeartbeat()
    }

    private fun recursiveConnect() {
        try {
            if (mqttAndroidClient?.isConnected == true) {
                return
            }
            val mqttConnectOptions = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                // use explicit setters to avoid name collision with local 'password' variable
                userName = BuildConfig.MQTT_USERNAME
                password = BuildConfig.MQTT_PASSWORD.toCharArray()
                setWill(TOPIC_ONLINE + "/" + globalConfig.mqttClientId, ByteArray(0), 1, true)
            }
            mqttAndroidClient?.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("MQTT connected successfully")

                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.isBufferEnabled = true
                    disconnectedBufferOptions.setBufferSize(100)
                    disconnectedBufferOptions.isPersistBuffer = false
                    disconnectedBufferOptions.isDeleteOldestMessages = false
                    mqttAndroidClient?.setBufferOpts(disconnectedBufferOptions)
                    subscribeToTopic()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT connection failed: ${exception?.message}")
                    // Optionally implement retry logic here
                    GlobalScope.launch(Dispatchers.IO) {
                        delay(5000) // wait before retrying
                        recursiveConnect()
                    }
                }
            })
        } catch (ex: Exception) {
            ex.printStackTrace()
            GlobalScope.launch(Dispatchers.IO) {
                delay(5000) // wait before retrying
                recursiveConnect()
            }
        }
    }


    private fun subscribeToTopic() {
        subscribe("${TOPIC_ONLINE}/#")

    }


    private fun startHeartbeat() {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                while (true) {
                    //wait for connection
                    if (mqttAndroidClient?.isConnected == true) {
                        break
                    }
                    delay(1000)
                }
                publish(TOPIC_ONLINE, System.currentTimeMillis().toString().toByteArray(), qos = 1, retained = true)
                delay(29 * 60 * 1000)
            }
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        try {
            mqttAndroidClient?.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("Subscribed to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to subscribe $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }


    private fun handleIncomingMessage(topic: String, message: MqttMessage) {
        Log.i("Received message from $topic")
        when {
            topic.startsWith(TOPIC_ONLINE) -> {
                OnlineInfos.parseInfo(topic, String(message.payload).toLongOrNull() ?: return)
            }

            else -> {
            }
        }
    }

    fun publish(tpc: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false) {
        try {
            val topic = tpc + "/" + globalConfig.mqttClientId
            val message = MqttMessage()
            message.payload = payload
            message.qos = qos
            message.isRetained = retained
            mqttAndroidClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("published to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to publish to $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }


}