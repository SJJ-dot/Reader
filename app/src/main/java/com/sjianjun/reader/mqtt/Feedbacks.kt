package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_FEEDBACK
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import sjj.alog.Log
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
            val reply = feedback.replies.lastOrNull()
            if (feedback.reply?.isNotBlank() == true && feedback.reply != reply?.content) {
                feedback.replies.add(Reply(author = "", content = feedback.reply ?: "", timestamp = feedback.repliedAt ?: 0))
            }
            Log.i("Received feedback: $feedback")
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

    /**
     * Append a reply to a feedback and publish the updated feedback as a retained message.
     * author can be null (will be set to clientId or "system")
     */
    fun appendReply(feedback: Feedback, replyContent: String, author: String? = null) {
        val r = Reply().apply {
            this.author = author ?: globalConfig.mqttClientId ?: ""
            this.content = replyContent
            this.timestamp = System.currentTimeMillis()
        }
        // make a local copy
        val newFeedback = feedback.copy()
        newFeedback.replies.add(r)
        // maintain legacy fields for backward compatibility (last reply)
        newFeedback.reply = r.content
        newFeedback.repliedAt = r.timestamp

        MqttUtil.publish("$TOPIC_FEEDBACK/${newFeedback.clientId}/${newFeedback.id}", retained = true, payload = gson.toJson(newFeedback).toByteArray()) {
            toast(if (it) "发送成功" else "发送失败")
        }
    }

    // Delete a reply by index from a feedback, optimistic local update and publish retained
    fun deleteReply(feedback: Feedback, index: Int) {
        if (index < 0 || index >= feedback.replies.size) return
        val newFeedback = feedback.copy()
        newFeedback.replies.removeAt(index)
        if (newFeedback.replies.isNotEmpty()) {
            val last = newFeedback.replies.last()
            newFeedback.reply = last.content
            newFeedback.repliedAt = last.timestamp
        } else {
            newFeedback.reply = null
            newFeedback.repliedAt = null
        }

        MqttUtil.publish("$TOPIC_FEEDBACK/${newFeedback.clientId}/${newFeedback.id}", retained = true, payload = gson.toJson(newFeedback).toByteArray()) {
            toast(if (it) "删除回复成功" else "删除回复失败")
        }
    }

}
