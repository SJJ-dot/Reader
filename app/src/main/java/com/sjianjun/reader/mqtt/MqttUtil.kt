package com.sjianjun.reader.mqtt

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.unzip
import com.sjianjun.reader.utils.zip
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


    private var mqttAndroidClient: MqttAndroidClient? = null

    fun connect(context: Context) {
        val serverURI = BuildConfig.MQTT_SERVER_URI
        val username = BuildConfig.MQTT_USERNAME
        val password = BuildConfig.MQTT_PASSWORD

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

        val mqttConnectOptions = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            // use explicit setters to avoid name collision with local 'password' variable
            userName = username
            setPassword(password.toCharArray())
        }





        try {
            //addToHistory("Connecting to " + serverUri);
            mqttAndroidClient?.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("Connected to: $serverURI")
                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.setBufferEnabled(true)
                    disconnectedBufferOptions.setBufferSize(100)
                    disconnectedBufferOptions.setPersistBuffer(false)
                    disconnectedBufferOptions.setDeleteOldestMessages(false)
                    mqttAndroidClient?.setBufferOpts(disconnectedBufferOptions)
                    subscribeToTopic()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to connect to: $serverURI, exception: ${exception?.message}")
                }
            })
        } catch (ex: MqttException) {
            ex.printStackTrace()
        }

        startHeartbeat()
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
                publish(TOPIC_ONLINE, System.currentTimeMillis().toString().toByteArray())
                delay(30000)
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
        when  {
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