package com.sjianjun.reader.mqtt

import android.annotation.SuppressLint
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import sjj.alog.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

val String.topic: String
    get() = this.replace("{clientId}", globalConfig.mqttClientId ?: "unknown")

object MqttUtil {
    private const val TOPIC_ONLINE = "reader/online2/login/{clientId}/server"

    //查询在线用户数
    const val TOPIC_ONLINE_QUERY_NUM_REQUEST = "reader/online2/num/{clientId}/server"
    const val TOPIC_ONLINE_QUERY_NUM_RESP = "reader/online2/num/server/{clientId}"
    const val TOPIC_FEEDBACK_REQUEST = "reader/feedback2/{clientId}/server"
    const val TOPIC_FEEDBACK_RESP = "reader/feedback2/server/{clientId}"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var onlineJob: Job? = null

    @SuppressLint("StaticFieldLeak")
    private var mqttClient: MqttAsyncClient? = null

    private val msgQueueCallback = ConcurrentHashMap<String, (MqttMessage) -> Unit>()

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
                    publishOnline()
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
                    Log.i("Received message from $topic")
                    msgQueueCallback[topic]?.invoke(message)
                } catch (e: Exception) {
                    Log.e("Error handling incoming MQTT message: ${e.message}")
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })

        recursiveConnect()
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
                val payload = mapOf(
                    "clientId" to globalConfig.mqttClientId,
                    "status" to "offline"
                )
                setWill(TOPIC_ONLINE.topic, gson.toJson(payload).toByteArray(), 1, true)
            }
            mqttClient?.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("MQTT connected successfully")
                    publishOnline()
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


    private fun publishOnline() {
        val payload = mapOf(
            "clientId" to globalConfig.mqttClientId,
            "status" to "online"
        )
        val payloadByteArray = gson.toJson(payload).toByteArray()
        onlineJob?.cancel()
        onlineJob = scope.launch {
            while (mqttClient?.isConnected == true && isActive) {
                val throwable = publish(TOPIC_ONLINE, payloadByteArray, qos = 1, retained = true)
                if (throwable == null) {
                    break
                } else {
                    Log.e("Failed to publish online status: ${throwable.message}")
                    delay(5000)
                }
            }
        }
    }

    suspend fun subscribe(topic: String, qos: Int = 1): Throwable? {
        try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot subscribe to $topic")
                return Exception("MQTT client is not connected")
            }
            val topic = topic.topic
            return suspendCancellableCoroutine { con ->
                mqttClient?.subscribe(topic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        if (con.isActive) {
                            con.resume(null)
                        }
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.i("Failed to subscribe $topic")
                        if (con.isActive) {
                            con.resume(exception ?: Exception("Unknown error"))
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("Error subscribing to $topic: ${e.message}")
            return e
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

    suspend fun publish(tpc: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false): Throwable? {
        try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot publish to $tpc")
                return Exception("MQTT client is not connected")
            }
            val tpc = tpc.topic
            Log.i("Publishing message to $tpc: ${String(payload)}")
            val message = MqttMessage()
            message.payload = payload
            message.qos = qos
            message.isRetained = retained
            return suspendCancellableCoroutine { cont ->
                mqttClient?.publish(tpc, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        if (cont.isActive) {
                            cont.resume(null)
                        }
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.i("Failed to publish to $tpc")
                        if (cont.isActive) {
                            cont.resume(exception ?: Exception("Unknown error"))
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("Error publishing to $tpc: ${e.message}")
            return e
        }
    }


    suspend fun request(requestTopic: String, responseTopic: String, payload: ByteArray = ByteArray(0), timeout: Long = 5000): ByteArray? {
        return try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot send request to $requestTopic")
                return null
            }
            val respTpc = responseTopic.topic

            return suspendCancellableCoroutine { cont ->
                // 保存 job 引用，便于取消
                var timeoutJob: Job? = null
                var subscriptionJob: Job? = null

                val clear = {
                    msgQueueCallback.remove(respTpc)
                    try {
                        // 取消定时任务与订阅任务
                        timeoutJob?.cancel()
                        subscriptionJob?.cancel()
                    } catch (t: Throwable) {
                        Log.e("cancel jobs error: ${t.message}")
                    }
                    try {
                        // 尝试退订（异步，不等待），防止抛
                        mqttClient?.unsubscribe(respTpc)
                    } catch (t: Throwable) {
                        Log.e("unsubscribe error: ${t.message}")
                    }
                }
                msgQueueCallback[respTpc] = { msg ->
                    clear()
                    if (cont.isActive) {
                        cont.resume(msg.payload)
                    }
                }

                // 启动超时 job
                timeoutJob = scope.launch {
                    try {
                        delay(timeout)
                        Log.i("Request to $requestTopic timed out")
                        clear()
                        if (cont.isActive) {
                            cont.resume(null)
                        }
                    } catch (ignore: Throwable) {
                        // coroutine cancelled
                    }
                }

                // 启动订阅 + 发布流程（在同一个 scope 下）
                subscriptionJob = scope.launch {
                    val subErr = subscribe(respTpc)
                    if (subErr != null) {
                        Log.e("Failed to subscribe to response topic $respTpc: ${subErr.message}")
                        clear()
                        if (cont.isActive) cont.resume(null)
                        return@launch
                    }
                    val pubErr = publish(requestTopic, payload)
                    if (pubErr != null) {
                        Log.e("Failed to publish request to $requestTopic: ${pubErr.message}")
                        clear()
                        if (cont.isActive) cont.resume(null)
                        return@launch
                    }
                }

                cont.invokeOnCancellation {
                    clear()
                }
            }
        } catch (e: Exception) {
            Log.e("Error sending request to $requestTopic: ${e.message}")
            null
        }
    }


}