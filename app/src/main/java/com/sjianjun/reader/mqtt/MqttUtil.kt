package com.sjianjun.reader.mqtt

import android.annotation.SuppressLint
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
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
    const val TOPIC_FEEDBACK = "reader/feedback"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("StaticFieldLeak")
    private var mqttClient: MqttAsyncClient? = null

    fun connect() {
        val serverURI = BuildConfig.MQTT_SERVER_URI

        // use a stable clientId stored in SharedPreferences to allow session persistence
        val clientId = globalConfig.mqttClientId ?: UUID.randomUUID().toString().replace("-", "").also {
            globalConfig.mqttClientId = it
        }

        mqttClient = MqttAsyncClient(serverURI, clientId, MemoryPersistence())
        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) {
                    Log.i("Reconnected to : $serverURI")
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic()
                } else {
                    Log.i("Connected to: $serverURI")
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
            if (mqttClient?.isConnected == true) {
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
            mqttClient?.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("MQTT connected successfully")
                    // MemoryPersistence MqttAsyncClient doesn't use Android disconnected buffer options
                    subscribeToTopic()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT connection failed: ${exception?.message}")
                    // Optionally implement retry logic here
                    scope.launch {
                        delay(5000) // wait before retrying
                        recursiveConnect()
                    }
                }
            })
        } catch (ex: Exception) {
            ex.printStackTrace()
            scope.launch {
                delay(5000) // wait before retrying
                recursiveConnect()
            }
        }
    }


    private fun subscribeToTopic() {
        subscribe("${TOPIC_ONLINE}/#")

    }


    private fun startHeartbeat() {
        scope.launch {
            while (true) {
                while (true) {
                    //wait for connection
                    if (mqttClient?.isConnected == true) {
                        break
                    }
                    delay(1000)
                }
                publish(TOPIC_ONLINE + "/" + globalConfig.mqttClientId, System.currentTimeMillis().toString().toByteArray(), qos = 1, retained = true)
                delay(29 * 60 * 1000)
            }
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot subscribe to $topic")
                return
            }
            mqttClient?.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("Subscribed to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to subscribe $topic")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unsubscribe(topic: String) {
        try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot unsubscribe from $topic")
                return
            }
            mqttClient?.unsubscribe(topic, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("Unsubscribed from $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to unsubscribe $topic")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun handleIncomingMessage(topic: String, message: MqttMessage) {
        Log.i("Received message from $topic")
        when {
            topic.startsWith(TOPIC_ONLINE) -> {
                OnlineInfos.parseInfo(topic, String(message.payload).toLongOrNull() ?: return)
            }

            topic.startsWith(TOPIC_FEEDBACK) -> {
                Feedbacks.parseFeedback(topic, String(message.payload))
                Log.i("Received feedback message: ${String(message.payload)}")
            }

            else -> {
            }
        }
    }

    fun publish(tpc: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false, callback: (success: Boolean) -> Unit = {}) {
        try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot publish to $tpc")
                callback(false)
                return
            }
            val message = MqttMessage()
            message.payload = payload
            message.qos = qos
            message.isRetained = retained
            mqttClient?.publish(tpc, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("published to $tpc")
                    callback(true)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to publish to $tpc")
                    callback(false)
                }
            })
        } catch (e: MqttException) {
            Log.e("Error publishing to $tpc: ${e.message}")
            callback(false)
        }
    }


}