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
import kotlinx.coroutines.withTimeoutOrNull
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
    const val TOPIC_FEEDBACK_LIST = "reader/feedback2/list/{clientId}/server"
    const val TOPIC_FEEDBACK_LIST_RESP = "reader/feedback2/list/server/{clientId}"
    const val TOPIC_FEEDBACK_SET = "reader/feedback2/set/{clientId}/server"
    const val TOPIC_FEEDBACK_SET_RESP = "reader/feedback2/set/server/{clientId}"

    const val TOPIC_ONLINE_LEADERBOARD_REQUEST = "reader/online2/leaderboard/{clientId}/server"
    const val TOPIC_ONLINE_LEADERBOARD_RESP = "reader/online2/leaderboard/server/{clientId}"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var onlineJob: Job? = null
    private var reconnectJob: Job? = null
    private val connectLock = Any()

    @SuppressLint("StaticFieldLeak")
    private var mqttClient: MqttAsyncClient? = null

    @Volatile
    private var isConnecting = false

    private val msgQueueCallback = ConcurrentHashMap<String, (MqttMessage) -> Unit>()
    private val subscribedTopics = ConcurrentHashMap<String, Int>()

    private val mqttCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            if (reconnect) {
                Log.i("Reconnected to : $serverURI")
            } else {
                Log.i("Connected to: $serverURI")
            }
            publishOnline()
            restoreSubscriptions()
        }

        override fun connectionLost(cause: Throwable?) {
            isConnecting = false
            Log.i("The Connection was lost. cause=${cause?.message}")
            scheduleReconnect(forceRecreate = true, delayMillis = 1500)
        }

        @Throws(Exception::class)
        override fun messageArrived(topic: String?, message: MqttMessage) {
            try {
                msgQueueCallback[topic]?.invoke(message)
            } catch (e: Exception) {
                Log.e("Error handling incoming MQTT message: ${e.message}")
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
        }
    }

    fun connect() {
        ensureClient(forceRecreate = mqttClient == null)
        scheduleReconnect()
    }

    private fun ensureClient(forceRecreate: Boolean = false): MqttAsyncClient? {
        synchronized(connectLock) {
            val current = mqttClient
            if (!forceRecreate && current != null) {
                return current
            }

            try {
                current?.setCallback(null)
                current?.close()
            } catch (e: Exception) {
                Log.e("Error closing old MQTT client: ${e.message}")
            }

            val serverURI = BuildConfig.MQTT_SERVER_URI
            val clientId = globalConfig.mqttClientId ?: UUID.randomUUID().toString().replace("-", "").also {
                globalConfig.mqttClientId = it
            }
            return MqttAsyncClient(serverURI, clientId, MemoryPersistence()).also { client ->
                client.setCallback(mqttCallback)
                mqttClient = client
            }
        }
    }

    private fun buildConnectOptions(): MqttConnectOptions {
        return MqttConnectOptions().apply {
            isAutomaticReconnect = false
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            userName = BuildConfig.MQTT_USERNAME
            password = BuildConfig.MQTT_PASSWORD.toCharArray()
            val payload = mapOf(
                "clientId" to globalConfig.mqttClientId,
                "status" to "offline"
            )
            setWill(TOPIC_ONLINE.topic, gson.toJson(payload).toByteArray(), 1, true)
        }
    }

    private fun scheduleReconnect(forceRecreate: Boolean = false, delayMillis: Long = 0L) {
        synchronized(connectLock) {
            if (mqttClient?.isConnected == true) {
                return
            }
            if (reconnectJob?.isActive == true) {
                return
            }
            reconnectJob = scope.launch {
                var recreate = forceRecreate
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
                while (isActive && mqttClient?.isConnected != true) {
                    val error = connectOnce(recreate)
                    if (error == null && mqttClient?.isConnected == true) {
                        break
                    }
                    Log.e("MQTT reconnect failed: ${error?.message}")
                    recreate = true
                    delay(5000)
                }
            }
        }
    }

    private suspend fun connectOnce(forceRecreate: Boolean = false): Throwable? {
        val client = ensureClient(forceRecreate) ?: return Exception("MQTT client create failed")
        if (client.isConnected) {
            return null
        }
        if (isConnecting) {
            return waitUntilConnected().let {
                if (it) null else Exception("MQTT client is still connecting")
            }
        }
        isConnecting = true
        return try {
            suspendCancellableCoroutine { cont ->
                try {
                    client.connect(buildConnectOptions(), null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i("MQTT connected successfully")
                            if (cont.isActive) {
                                cont.resume(null)
                            }
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e("MQTT connection failed: ${exception?.message}")
                            if (cont.isActive) {
                                cont.resume(exception ?: Exception("MQTT connect failed"))
                            }
                        }
                    })
                } catch (e: Exception) {
                    if (cont.isActive) {
                        cont.resume(e)
                    }
                }
            }
        } finally {
            isConnecting = false
        }
    }

    private suspend fun waitUntilConnected(timeout: Long = 12_000L): Boolean {
        scheduleReconnect()
        return withTimeoutOrNull(timeout) {
            while (mqttClient?.isConnected != true) {
                delay(200)
            }
            true
        } == true
    }

    private fun restoreSubscriptions() {
        val client = mqttClient ?: return
        if (!client.isConnected) {
            return
        }
        val topics = subscribedTopics.entries.toList()
        if (topics.isEmpty()) {
            return
        }
        for ((topic, qos) in topics) {
            try {
                client.subscribe(topic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("Resubscribed to $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("Failed to resubscribe $topic: ${exception?.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("Error resubscribing to $topic: ${e.message}")
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
            val normalizedTopic = topic.topic
            if (!waitUntilConnected()) {
                Log.i("MQTT client is not connected. Cannot subscribe to $topic")
                return Exception("MQTT client is not connected")
            }
            return suspendCancellableCoroutine { con ->
                mqttClient?.subscribe(normalizedTopic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        subscribedTopics[normalizedTopic] = qos
                        if (con.isActive) {
                            con.resume(null)
                        }
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.i("Failed to subscribe $normalizedTopic")
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
        val normalizedTopic = topic.topic
        subscribedTopics.remove(normalizedTopic)
        try {
            if (mqttClient?.isConnected != true) {
                Log.i("MQTT client is not connected. Cannot unsubscribe from $normalizedTopic")
                return
            }
            mqttClient?.unsubscribe(normalizedTopic, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("Unsubscribed from $normalizedTopic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("Failed to unsubscribe $normalizedTopic")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun publish(tpc: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false): Throwable? {
        try {
            if (!waitUntilConnected()) {
                Log.i("MQTT client is not connected. Cannot publish to $tpc")
                return Exception("MQTT client is not connected")
            }
            val tpc = tpc.topic
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
            if (!waitUntilConnected()) {
                Log.i("MQTT client is not connected. Cannot send request to ${requestTopic.topic}")
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
                        unsubscribe(respTpc)
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
                        Log.i("Request to ${requestTopic.topic} timed out")
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