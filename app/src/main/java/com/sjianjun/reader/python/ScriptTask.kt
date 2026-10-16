package com.sjianjun.reader.python

import com.sjianjun.reader.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 脚本任务被取消时抛给脚本的异常。
 *
 * 书源脚本里的 except Exception 也可能把它吞掉，所以它只是"让脚本尽快退出"的一环：
 * 脚本若继续重试网络，Python 侧会直接抛出抓不住的 ScriptAborted（BaseException 子类）拦下。
 */
class ScriptTaskCancelledException(message: String = "脚本任务已取消") : RuntimeException(message)


/**
 * 在脚本任务上下文中执行一次阻塞的脚本调用。
 *
 * @param taskKey 可被外部取消的任务标识，为 null 时自动生成（不会被外部取消）
 * @param script 书源脚本内容，取消时用于丢弃脚本命名空间缓存
 */
object ScriptTaskRunner {
    fun <T> run(taskKey: String?, script: String, block: () -> T): T =
        ScriptTaskRegistry.withTask(taskKey) { task ->
            ScriptTaskBridge.begin(task.key, script)
            try {
                block()
            } finally {
                ScriptTaskBridge.end(task.key)
            }
        }
}


/**
 * 脚本任务注册表：把"取消某个脚本"落到具体的资源上。
 *
 * Java 侧的阻塞桥（WebView 等待、okhttp 阻塞请求）在阻塞期间通过 [current] 登记取消动作；
 * Python 侧的 socket、脚本自建线程、脚本命名空间由 [ScriptTaskBridge.abort] 处理。
 */
internal object ScriptTaskRegistry {

    private val seq = AtomicLong()
    private val tasks = ConcurrentHashMap<String, MutableList<Task>>()
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

    fun <T> withTask(key: String?, block: (Task) -> T): T {
        val taskKey = key ?: "task-${seq.incrementAndGet()}"
        val task = Task(taskKey)
        // 同一个 key 上可能同时有多次加载在跑（例如反复加载同一本书）。
        // 不能直接覆盖：被覆盖的那个 Task 的取消动作会连同 Task 一起丢掉，卡住的调用将永远等不到取消。
        tasks.compute(taskKey) { _, list ->
            (list ?: CopyOnWriteArrayList<Task>()).apply { add(task) }
        }
        val previous = threadTask.get()
        threadTask.set(task)
        try {
            return block(task)
        } finally {
            threadTask.set(previous)
            tasks.compute(taskKey) { _, list ->
                list?.apply { remove(task) }?.takeIf { it.isNotEmpty() }
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

    /** 取消脚本任务：先让 Python 侧摘下任务状态并注入取消异常，再执行 Java 侧登记的取消动作 */
    fun cancel(key: String) {
        val list = tasks[key]?.toList() ?: return
        // 顺序不能反：取消动作（job.cancel() 之类）会把脚本线程从阻塞里唤醒，
        // 一旦脚本线程先醒，它可能抢先退到 clear_current_task 把任务状态摘掉，
        // 后到的 abort 就找不到登记过的线程，注入落空，脚本自建线程和连接会一直留在进程里。
        // 放在同一个 IO 协程里顺序执行：既保证先摘状态，又不会让调用方（可能在主线程）等 GIL。
        App.app.appScope.launch(Dispatchers.IO) {
            ScriptTaskBridge.abort(key)
            list.forEach { task ->
                task.takeCancelActions().forEach { action -> runCatching { action() } }
            }
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
