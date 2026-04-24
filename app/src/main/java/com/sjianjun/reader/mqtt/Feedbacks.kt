package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_FEEDBACK_LIST
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_FEEDBACK_LIST_RESP
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_FEEDBACK_SET
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_FEEDBACK_SET_RESP
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import sjj.alog.Log

object Feedbacks {
    val feedbackList = MutableLiveData<List<Feedback>>()
    suspend fun request() {
        val payload = mapOf(
            "client_id" to globalConfig.mqttClientId,
            "limit" to 100,
            "offset" to 0
        )
        val response = MqttUtil.request(TOPIC_FEEDBACK_LIST, TOPIC_FEEDBACK_LIST_RESP, gson.toJson(payload).toByteArray())
        if (response != null) {
            //{"status": "ok", "items": items, "count": len(items), "total": total, "limit": limit, "offset": offset}
            val resp = gson.fromJson<FeedbackListResponse>(String(response))
            Log.i("Feedbacks request feedback list response: $resp")
            feedbackList.postValue(resp?.items ?: emptyList())
        }

    }


    suspend fun sendFeedback(content: String) {
        val feedback = Feedback().apply {
            this.client_id = globalConfig.mqttClientId
            this.content = content
        }
        val pub = MqttUtil.request(TOPIC_FEEDBACK_SET, TOPIC_FEEDBACK_SET_RESP, payload = gson.toJson(feedback).toByteArray())
        toast(if (pub != null) "反馈发送成功" else "反馈发送失败")
        request()
    }

    suspend fun deleteFeedback(feedback: Feedback) {
        val delete = feedback.copy(content = null, replies = mutableListOf())
        delete.client_id = globalConfig.mqttClientId
        val pub = MqttUtil.request(TOPIC_FEEDBACK_SET, TOPIC_FEEDBACK_SET_RESP, payload = gson.toJson(delete).toByteArray())
        toast(if (pub != null) "反馈删除成功" else "反馈删除失败")
        request()
    }

    suspend fun appendReply(feedback: Feedback, replyContent: String) {
        val reply = Feedback().apply {
            this.client_id = globalConfig.mqttClientId
            this.parent_id = feedback.id
            this.content = replyContent
        }
        val pub = MqttUtil.request(TOPIC_FEEDBACK_SET, TOPIC_FEEDBACK_SET_RESP, payload = gson.toJson(reply).toByteArray())
        toast(if (pub != null) "发送成功" else "发送失败")
        request()
    }

    suspend fun deleteReply(feedback: Feedback) {
        val reply = feedback.copy(content = null)
        val pub = MqttUtil.request(TOPIC_FEEDBACK_SET, TOPIC_FEEDBACK_SET_RESP, payload = gson.toJson(reply).toByteArray())
        toast(if (pub != null) "删除回复成功" else "删除回复失败")
        request()
    }

}
