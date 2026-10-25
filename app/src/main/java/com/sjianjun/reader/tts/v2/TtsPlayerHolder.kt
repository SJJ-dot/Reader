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

    /**
     * 章节加载服务（进程级单例，跨会话/跨引擎复用）。
     *
     * 注意：朗读用的**段落缓存（最近 8 章 LRU）在 [NovelLoadService] 内部**，
     * 不是存在 holder 里；holder 只是持有这个实例。
     */
    private val loader by lazy { NovelLoadService(BookChapterContentProvider(), scope) }

    @Volatile
    private var player: TtsPlayer? = null

    /** 当前播放器用的引擎类型 */
    @Volatile
    private var currentType: TtsEngineType? = null

    /**
     * 当前播放器创建时使用的 Edge 音色。
     * Edge 的音色在合成会话建立时就固定了（引擎实例里的 voice 不会热更新），
     * 所以音色变化必须重建播放器，否则表现为「切换音色无效，要重启 App」。
     */
    @Volatile
    private var currentVoice: String? = null

    /** 已经下发给引擎的语速（用于判断配置里的语速是否需要重新同步） */
    @Volatile
    private var currentRate: Int = -1

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
     * 设置变了：开始新的朗读前调用，保证用的是配置的引擎 / 音色 / 语速。
     *
     * - 引擎、音色：引擎实例内部固定，必须重建播放器；
     * - 语速：引擎内部字段，直接 setRate 同步即可（未播放时不会打断朗读）。
     */
    fun ensureConfiguredEngine() {
        val configured = configuredEngine()
        val voice = configuredVoice(configured)
        val rate = configuredRate()
        synchronized(this) {
            if (currentType != configured || currentVoice != voice) {
                Log.i("$TAG 引擎/音色配置变化：$currentType($currentVoice) -> $configured($voice)，重建播放器")
                player?.stop()
                player = createPlayer(configured)
            }
            if (currentRate != rate) {
                Log.i("$TAG 同步语速 $currentRate -> $rate")
                currentRate = rate
                player().setRate(rate)
            }
        }
    }

    /** 当前配置对应的音色（仅 Edge 有意义，其它引擎返回空串） */
    private fun configuredVoice(type: TtsEngineType): String =
        if (type == TtsEngineType.EDGE) (globalConfig.readerTtsVoice.value ?: "").trim() else ""

    /** 当前配置对应的语速（0~100） */
    private fun configuredRate(): Int = (globalConfig.readerTtsRate.value ?: 50).coerceIn(0, 100)

    fun currentEngineType(): TtsEngineType? = currentType

    /**
     * 作废某一章的朗读段落缓存（正文被强制刷新后调用，避免朗读读到旧正文）。
     */
    fun invalidateChapter(bookId: String, chapterIndex: Int) {
        if (bookId.isBlank() || chapterIndex < 0) return
        loader.invalidate(bookId, chapterIndex)
    }

    /**
     * 作废整本书的朗读段落缓存（目录整本重载后调用：章节链接可能变化）。
     */
    fun invalidateBook(bookId: String) {
        if (bookId.isBlank()) return
        loader.invalidateBook(bookId)
    }

    internal fun setPlayerForTest(newPlayer: TtsPlayer?) {
        synchronized(this) {
            player?.stop()
            player = newPlayer
            currentType = null
            currentVoice = null
            currentRate = -1
            bindState(newPlayer)
        }
    }

    private fun createPlayer(type: TtsEngineType): TtsPlayer {
        val engine = when (type) {
            TtsEngineType.SYSTEM -> SystemTtsEngine()
            TtsEngineType.EDGE -> EdgeTtsEngine()
        }
        currentType = type
        currentVoice = configuredVoice(type)
        currentRate = configuredRate()
        Log.i("$TAG 创建播放器，引擎=${type.label} voice=$currentVoice rate=$currentRate")
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
