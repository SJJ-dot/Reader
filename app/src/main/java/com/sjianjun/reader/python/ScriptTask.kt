package com.sjianjun.reader.python

import com.sjianjun.reader.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 脚本任务被取消时抛给脚本的异常。
 *
 * 书源脚本里的 except Exception 也可能把它吞掉，所以它只是"让脚本尽快退出"的一环：
 * 脚本若继续重试网络，Python 侧会直接抛出抓不住的 ScriptAborted（BaseException 子类）拦下。
 */
class ScriptTaskCancelledException(message: String = "脚本任务已取消") : RuntimeException(message)


/**
 * 在脚本任务上下文中执行一次阻塞的脚本调用，并把这次调用绑到发起它的协程上：
 *
 * - **协程取消 ⇒ 脚本调用取消**：页面销毁、发起方主动取消时，不必再等阻塞中的脚本自己超时返回；
 * - **同一目标的新调用 ⇒ 旧调用立即取消**：[taskKey] 是"同一目标"的标识
 *   （书籍 URL、书源、章节 URL……），
 *   新调用开始时旧调用会被取消（关连接、中断脚本线程），做到"重复请求只保留最新一次"。
 *
 * @param taskKey 目标标识，为 null 时只绑定协程、不参与同目标去重
 * @param script 书源脚本内容，取消时用于丢弃脚本命名空间缓存
 */
object ScriptTaskRunner {

    suspend fun <T> run(taskKey: String?, script: String, block: () -> T): T =
        ScriptTaskRegistry.withTask(taskKey) { task ->
            ScriptTaskBridge.begin(task.key, script)
            try {
                runWithCoroutineCancellation(task.key, block)
            } finally {
                ScriptTaskBridge.end(task.key)
            }
        }

    /**
     * 脚本调用本身就是阻塞的，这里保持"在调用方线程上同步执行"，
     * 只是在阻塞前把取消回调挂到协程上：协程一被取消就取消本次脚本任务。
     */
    private suspend fun <T> runWithCoroutineCancellation(taskKey: String, block: () -> T): T =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { ScriptTaskRegistry.cancel(taskKey) }
            val value = try {
                block()
            } catch (t: Throwable) {
                cont.resumeWithException(t)
                return@suspendCancellableCoroutine
            }
            cont.resume(value)
        }

    /**
     * 是否属于"本次脚本调用被取消"（页面退出、被同一目标上更新的调用取代、主动取消）。
     * 这类异常不应被当成加载失败记录错误。
     */
    fun isCancelled(t: Throwable): Boolean {
        var e: Throwable? = t
        while (e != null) {
            if (e is ScriptTaskCancelledException) return true
            val text = e.toString() + " " + (e.message ?: "")
            if (text.contains("ScriptAborted") || text.contains("ScriptTaskCancelled")) return true
            e = e.cause
        }
        return false
    }
}


/**
 * 同一目标上的调用代际，配合 [ScriptTaskRunner.run] 使用：
 *
 * - [begin]：开始一次调用，并把该目标上**上一次调用的脚本任务立即取消**
 *   （有些调用（如章节正文）在真正发起脚本请求前要先排队等锁，所以要在调用开始时就取消旧的，
 *   不能等新调用的脚本请求发起时才取消）；
 * - [isLatest]：本次调用的结果是否仍然有效——新调用开始后旧调用立即失效，
 *   即使旧调用吞掉了取消异常、侥幸返回，也不能再写数据、写状态；
 * - [end]：调用结束，只有仍是最新一次时才清理记录。
 *
 * 只有需要"同一目标只保留最新一次"的调用才传标识（见 [detailsKey]/[searchKey]/[contentKey]），
 * 传 null 时只随协程取消，不做去重。
 */
object ScriptCallTargets {

    private const val NO_TARGET = 0L

    private val generations = ConcurrentHashMap<String, Long>()
    private val seq = AtomicLong()

    /** 加载详情：书籍 URL */
    fun detailsKey(bookUrl: String) = bookUrl.takeIf { it.isNotBlank() }

    /** 搜索：书源（同一书源只保留最后一次搜索，不区分关键词） */
    fun searchKey(sourceId: String) = sourceId.takeIf { it.isNotBlank() }

    /** 加载章节内容：章节/分页 URL */
    fun contentKey(chapterUrl: String) = chapterUrl.takeIf { it.isNotBlank() }

    /** 只取消该目标上当前那次调用（章节正文要先让出章节锁时用） */
    fun cancelCurrent(target: String?) {
        if (target != null) ScriptTaskRegistry.cancelTarget(target)
    }

    fun begin(target: String?): Long {
        if (target == null) return NO_TARGET
        val generation = seq.incrementAndGet()
        generations[target] = generation
        // 同一目标上新的调用：取消上一次还在跑的脚本调用
        ScriptTaskRegistry.cancelTarget(target)
        return generation
    }

    fun isLatest(target: String?, generation: Long): Boolean =
        target == null || generations[target] == generation

    fun end(target: String?, generation: Long) {
        if (target == null) return
        generations.remove(target, generation)
    }
}


/**
 * 脚本任务注册表：把"取消某一次脚本调用"落到具体的资源上。
 *
 * Java 侧的阻塞桥（WebView 等待、okhttp 阻塞请求）在阻塞期间通过 [current] 登记取消动作；
 * Python 侧的 socket、脚本自建线程、脚本命名空间由 [ScriptTaskBridge.abort] 处理。
 *
 * 每次调用都有自己的任务标识（不按书、不共享），
 * [targets] 记录"某个目标上当前是哪一次调用"，新的调用开始时取消旧的调用，互不影响。
 */
internal object ScriptTaskRegistry {

    private val seq = AtomicLong()
    /** 调用标识 -> 任务（用于协程取消时找到对应的取消动作） */
    private val tasks = ConcurrentHashMap<String, Task>()
    /** 目标 -> 该目标上最新一次调用 */
    private val targets = ConcurrentHashMap<String, Task>()
    private val threadTask = ThreadLocal<Task?>()

    class Task internal constructor(val key: String) {
        @Volatile
        var cancelled = false
            private set
        private val actions = CopyOnWriteArrayList<() -> Unit>()

        /**
         * 登记"取消时要执行"的动作（例如取消一次 WebView 等待、一次 okhttp 阻塞请求）。
         *
         * 注意：动作可能在后台线程执行（见 [cancel]），实现必须是线程安全的。
         */
        fun addCancelAction(action: () -> Unit) {
            if (cancelled) {
                runCatching { action() }
            } else {
                actions.add(action)
            }
        }

        fun removeCancelAction(action: () -> Unit) {
            actions.remove(action)
        }

        internal fun takeCancelActions(): List<() -> Unit> {
            cancelled = true
            return actions.toList()
        }
    }

    suspend fun <T> withTask(target: String?, block: suspend (Task) -> T): T {
        val task = Task("task-${seq.incrementAndGet()}")
        if (target != null) {
            // 同一目标上新的调用：先取消上一次调用（它有自己的任务标识，不会误伤本次调用）
            val previous = targets.put(target, task)
            if (previous != null) {
                Log.i("同一目标的新调用，取消上一次：$target")
                cancel(previous.key)
            }
        }
        tasks[task.key] = task
        val previous = threadTask.get()
        threadTask.set(task)
        try {
            return block(task)
        } finally {
            threadTask.set(previous)
            tasks.remove(task.key, task)
            if (target != null) {
                targets.remove(target, task)
            }
        }
    }

    /** 当前线程所属的脚本任务，阻塞桥用它登记取消动作；不在脚本任务里时为 null */
    fun current(): Task? = threadTask.get()

    /** 当前脚本任务是否已被取消 */
    fun isCurrentCancelled(): Boolean = threadTask.get()?.cancelled == true

    /**
     * 调用前拦截：当前脚本任务已取消时直接抛取消异常。
     *
     * 用于"一旦开始就有副作用"的调用（新建 WebView、弹出验证页）：任务已取消时不再开始，
     * 避免白白创建 WebView、白白弹出验证页。
     */
    fun throwIfCurrentCancelled() {
        if (isCurrentCancelled()) throw ScriptTaskCancelledException()
    }

    /** 取消某目标上当前这次调用（用于"新的调用开始时取代旧的"） */
    fun cancelTarget(target: String) {
        targets.remove(target)?.let { cancel(it.key) }
    }

    /**
     * 取消一次脚本调用：先让 Python 侧摘下任务状态并注入取消异常，再执行 Java 侧登记的取消动作。
     *
     * 顺序不能反：取消动作（job.cancel() 之类）会把脚本线程从阻塞里唤醒，
     * 一旦脚本线程先醒，它可能抢先退到 clear_current_task 把任务状态摘掉，
     * 后到的 abort 就找不到登记过的线程，注入落空，脚本自建线程和连接会一直留在进程里。
     * 放在同一个 IO 协程里顺序执行：既保证先摘状态，又不会让调用方（可能在主线程）等 GIL。
     */
    fun cancel(key: String) {
        val task = tasks[key] ?: return
        App.app.appScope.launch(Dispatchers.IO) {
            ScriptTaskBridge.abort(task.key)
            task.takeCancelActions().forEach { action -> runCatching { action() } }
        }
    }
}


/** Java 侧与 Python 脚本环境的任务桥 */
internal object ScriptTaskBridge {

    fun begin(taskKey: String, script: String) {
        runCatching { execModule.callAttr("set_current_task", taskKey, script) }
            .onFailure { Log.e("登记脚本任务失败：$taskKey", it) }
    }

    fun end(taskKey: String) {
        runCatching { execModule.callAttr("clear_current_task", taskKey) }
            .onFailure { Log.e("清理脚本任务失败：$taskKey", it) }
    }

    /**
     * 取消脚本任务：关闭脚本登记的网络连接、向脚本执行线程注入取消异常、丢弃脚本命名空间缓存。
     *
     * 必须在后台线程调用（[ScriptTaskRegistry.cancel] 已保证）：脚本线程可能正持有 GIL
     * （阻塞在不可打断的 C 调用里），在调用方线程（可能是主线程）同步执行会把界面一起卡住。
     */
    fun abort(taskKey: String) {
        runCatching { execModule.callAttr("abort", taskKey) }
            .onFailure { Log.e("取消脚本任务失败：$taskKey", it) }
    }
}
