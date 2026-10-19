package com.sjianjun.reader.tts.v2

import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.tts.v2.load.BookChapterContentProvider
import com.sjianjun.reader.tts.v2.load.NovelLoadService
import com.sjianjun.reader.tts.v2.session.TtsPlayer
import com.sjianjun.reader.tts.v2.session.TtsSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import sjj.alog.Log

/**
 * 进程内的播放器持有者：交互层（阅读页/网页阅读）与通知层共用同一个 [TtsPlayer] 实例。
 *
 * 单例的原因：朗读是"跨页面存活"的会话，页面退出后仍要继续；通知层只是它的展示与控制端。
 * 引擎由配置决定（system/edge），切换引擎时重建播放器（会话会被结束）。
 *
 * 注意：**外部一律订阅 [state]，不要自己持有 `player().state`**——播放器会被重建
 * （切换引擎/测试注入），订阅旧实例会表现为"界面不刷新、按钮状态不切换"。
 */
object TtsPlayerHolder {

    private const val TAG = "TtsPlayerHolder"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 章节加载服务（进程级，缓存跨会话复用） */
    private val loader by lazy { NovelLoadService(BookChapterContentProvider(), scope) }

    @Volatile
    private var player: TtsPlayer? = null

    /** 当前播放器用的引擎类型 */
    @Volatile
    private var currentType: TtsEngineType? = null

    /** 会话状态的稳定入口（跟着播放器实例走，重建后自动重绑） */
    private val _state = MutableStateFlow(TtsSessionState())
    val state: StateFlow<TtsSessionState> get() = _state

    private var stateBinding: Job? = null

    /** 当前播放器（首次使用时按配置创建） */
    fun player(): TtsPlayer {
        player?.let { return it }
        return synchronized(this) {
            player ?: createPlayer(configuredEngine()).also { player = it }
        }
    }

    /** 当前配置的引擎类型 */
    fun configuredEngine(): TtsEngineType =
        if ((globalConfig.readerTtsEngine.value ?: "default") == "edge") TtsEngineType.EDGE else TtsEngineType.SYSTEM

    /**
     * 切换引擎：结束当前会话、重建播放器（引擎内部的队列/合成线程需要整体换掉）
     */
    fun switchEngine(type: TtsEngineType) {
        synchronized(this) {
            player?.stop()
            player = createPlayer(type)
        }
    }

    /**
     * 设置里的引擎变了：开始新的朗读前调用，保证用的是配置的引擎
     */
    fun ensureConfiguredEngine() {
        val configured = configuredEngine()
        synchronized(this) {
            if (currentType == configured) return
            Log.i("$TAG 引擎配置变化：$currentType -> $configured，重建播放器")
            player?.stop()
            player = createPlayer(configured)
        }
    }

    fun currentEngineType(): TtsEngineType? = currentType

    internal fun setPlayerForTest(newPlayer: TtsPlayer?) {
        synchronized(this) {
            player?.stop()
            player = newPlayer
            currentType = null
            bindState(newPlayer)
        }
    }

    private fun createPlayer(type: TtsEngineType): TtsPlayer {
        val engine = when (type) {
            TtsEngineType.SYSTEM -> SystemTtsEngine()
            TtsEngineType.EDGE -> EdgeTtsEngine()
        }
        currentType = type
        Log.i("$TAG 创建播放器，引擎=${type.label}")
        val created = TtsPlayer(engine, loader, scope, onBookEnd = ::markBookEnd)
        bindState(created)
        return created
    }

    /** 把状态入口重新绑到新播放器上（旧绑定先取消），保证外部订阅永远拿到最新实例的状态 */
    private fun bindState(target: TtsPlayer?) {
        stateBinding?.cancel()
        stateBinding = null
        if (target == null) {
            _state.value = TtsSessionState()
            return
        }
        _state.value = target.state.value
        stateBinding = scope.launch {
            target.state.collect { _state.value = it }
        }
    }

    /** 全书读完：把阅读记录标记为"已读完"（与旧实现一致） */
    private fun markBookEnd(bookId: String, bookTitle: String) {
        if (bookId.isBlank() || bookTitle.isBlank()) return
        scope.launch {
            runCatching {
                val dao = DbFactory.db.readingRecordDao()
                val record = dao.getReadingRecordSync(bookTitle) ?: return@launch
                record.isEnd = true
                record.updateTime = System.currentTimeMillis()
                dao.insertReadingRecord(record)
                Log.i("$TAG 标记已读完：$bookTitle")
            }.onFailure { Log.e("$TAG 标记已读完失败：$bookTitle", it) }
        }
    }
}
