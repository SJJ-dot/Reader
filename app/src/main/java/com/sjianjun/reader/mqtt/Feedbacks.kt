package com.sjianjun.reader.mqtt

import android.adservices.topics.Topic
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_FEEDBACK
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Feedbacks {
    val feedbackMap = MutableLiveData(ConcurrentHashMap<String, Feedback>())
    fun subscribe() {
        MqttUtil.subscribe("$TOPIC_FEEDBACK/#")
    }

    fun unsubscribe() {
        MqttUtil.unsubscribe("$TOPIC_FEEDBACK/#")
    }

    fun parseFeedback(topic: String, payload: String) {
        if (payload.isBlank()) {
            val id = topic.substringAfterLast("/")
            feedbackMap.value?.remove(id)
            feedbackMap.postValue(feedbackMap.value)
            return
        }
        val feedback = gson.fromJson<Feedback>(payload)
        if (feedback != null) {
            feedbackMap.value?.put(feedback.id, feedback)
            feedbackMap.postValue(feedbackMap.value)
        }
    }

    fun sendFeedback(content: String) {
        val feedback = Feedback().apply {
            this.content = content
            this.clientId = globalConfig.mqttClientId
        }
        MqttUtil.publish("$TOPIC_FEEDBACK/${feedback.clientId}/${feedback.id}", retained = true, payload = gson.toJson(feedback).toByteArray()) {
            toast(if (it) "反馈发送成功" else "反馈发送失败")
        }
    }

    fun deleteFeedback(feedback: Feedback) {
        MqttUtil.publish("$TOPIC_FEEDBACK/${feedback.clientId}/${feedback.id}", retained = true, payload = ByteArray(0)) {
            toast(if (it) "反馈删除成功" else "反馈删除失败")
        }
    }

    fun replyFeedback(feedback: Feedback) {
        MqttUtil.publish("$TOPIC_FEEDBACK/${feedback.clientId}/${feedback.id}", retained = true, payload = gson.toJson(feedback).toByteArray()) {
            toast(if (it) "发送成功" else "发送失败")
        }
    }
}

class Feedback {
    var id = UUID.randomUUID().toString().replace("-", "")
    var clientId: String? = null
    var content: String? = null
    var timestamp: Long = System.currentTimeMillis()
    var reply: String? = null
    var repliedAt: Long? = null

    override fun toString(): String {
        return "Feedback(id='$id', content=$content, timestamp=$timestamp, reply=$reply, repliedAt=$repliedAt)"
    }
}