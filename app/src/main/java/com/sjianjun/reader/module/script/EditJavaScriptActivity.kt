package com.sjianjun.reader.module.script

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BOOK_SOURCE_ID
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.databinding.ActivityEditJavaScriptBinding
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import org.json.JSONObject
import java.util.UUID

class EditJavaScriptActivity : BaseActivity() {
    private val viewModel by viewModels<EditJavaScriptViewModel>()
    private var bookSource: BookSource? = null
    private var binding: ActivityEditJavaScriptBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEditJavaScriptBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val offset = (imeInsets.bottom - systemBars.bottom).coerceAtLeast(0)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom + offset)
            insets
        }
        // 初始化设置面板和数据
        val languageList = listOf(BookSource.Language.py.name + "-修仙书源", BookSource.Language.yuedu.name + "-阅读书源")
        binding!!.bookSourceLanguage.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languageList
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        launch {
            val sourceId = intent.getStringExtra(BOOK_SOURCE_ID)
            if (!sourceId.isNullOrBlank()) {
                bookSource = viewModel.getBookSource(sourceId)
            } else {
                bookSource = BookSource().apply {
                    id = UUID.randomUUID().toString()
                    name = "新建书源"
                    group = "自建书源"
                    version = 1
                    requestDelay = 1
                    comment = ""
                    lauanage = BookSource.Language.py
                    js = """
# def verify(query):
#     ""${'"'}
#     可选，部分书籍不支持搜索功能时提供，避免验证书源失败
#     验证书源是否可用，返回0、1、2 分别代表：验证通过，验证失败，不支持验证
#     ""${'"'}
#     pass

def getSiteUrl():
    ""${'"'}
    可选，如果不包含该函数，书源无法使用链接直接导入，也无法在书城进行导入
    :return: 书源网址，例如：https://www.69shuba.com，用于直接导入书籍详情页链接
    ""${'"'}
    pass

def search(query):
    ""${'"'}
    :param query: 搜索关键词
    :return: 书籍列表，包含以下字段：
        - bookTitle: 书籍标题
        - bookUrl: 书籍详情链接
        - bookAuthor: 书籍作者(可选)
        - bookCover: 书籍封面链接(可选)
    ""${'"'}
    pass


def getDetails(url):
    ""${'"'}
    :param url: 书籍详情链接
    :return: 书籍详情字典，包含以下字段：
        - url: 书籍详情链接
        - title: 书籍标题
        - author: 书籍作者
        - cover: 书籍封面链接
        - intro: 书籍简介
        - chapterList: 章节列表，包含以下字段：
            - title: 章节标题
            - url: 章节链接
    ""${'"'}
    pass


def getChapterContent(url):
    ""${'"'}
    :param url: 章节链接
    :return: 章节内容字符串
    ""${'"'}
    pass
                    """.trimIndent()
                }
            }
            val src = bookSource ?: return@launch

            // 填充元数据到设置面板
            binding!!.bookSourceName.setText(src.name)
            binding!!.bookSourceGroup.setText(src.group)
            binding!!.bookSourceId.setText(src.id)
            if (src.version >= 0) binding!!.bookSourceVersion.setText(src.version.toString())
            binding!!.bookSourceRequestDelay.setText(src.requestDelay.toString())
            binding!!.bookSourceComment.setText(src.comment)
            // 选择脚本语言
            val langName = src.lauanage.name
            val index = languageList.indexOfFirst { it.startsWith(langName) }.coerceAtLeast(0)
            binding!!.bookSourceLanguage.setSelection(index)

            if (src.lauanage == BookSource.Language.yuedu) {
                //阅读书源是json，格式化
                val json = src.js
                val formatJson = try {
                    JSONObject(json).toString(2)
                } catch (e: Exception) {
                    json
                }
                binding!!.script.setText(formatJson)
            } else {
                binding!!.script.setText(src.js)
            }
        }
        binding!!.settingsPanel.click {
            binding!!.editInfo.performClick()
        }

        // 编辑按钮：展开/收起设置面板
        binding!!.editInfo.click {
            val panel = binding!!.settingsPanel
            if (panel.isVisible) {
                panel.visibility = android.view.View.GONE
                binding!!.editInfo.text = "展开"
            } else {
                panel.visibility = android.view.View.VISIBLE
                binding!!.editInfo.text = "折叠"
            }
        }

        binding!!.saveScript.click {
            launch {
                bookSource?.let { src ->
                    // 从表单读取元数据并保存到对象
                    try {
                        src.name = binding!!.bookSourceName.text.toString().trim()
                        src.group = binding!!.bookSourceGroup.text.toString().trim()
                        src.id = binding!!.bookSourceId.text.toString().trim()
                        val versionText = binding!!.bookSourceVersion.text.toString().trim()
                        if (versionText.isNotEmpty()) {
                            src.version = try {
                                versionText.toLong()
                            } catch (e: Exception) {
                                src.version
                            }
                        }
                        val delayText = binding!!.bookSourceRequestDelay.text.toString().trim()
                        if (delayText.isNotEmpty()) {
                            src.requestDelay = try {
                                delayText.toLong()
                            } catch (e: Exception) {
                                src.requestDelay
                            }
                        }
                        src.comment = binding!!.bookSourceComment.text.toString().trim()
                        val lang = binding!!.bookSourceLanguage.selectedItem.toString()
                        src.lauanage = if (lang.startsWith(BookSource.Language.py.name)) {
                            BookSource.Language.py
                        } else if (lang.startsWith(BookSource.Language.yuedu.name)) {
                            BookSource.Language.yuedu
                        } else {
                            src.lauanage
                        }
                        // js 内容
                        src.js = binding!!.script.text.toString()
                        if (src.js.isBlank()) {
                            toast("书源脚本不能为空")
                            return@let
                        }
                        if (src.name.isBlank()) {
                            toast("书源名称不能为空")
                            return@let
                        }
                        if (src.group.isBlank()) {
                            toast("书源分组不能为空")
                            return@let
                        }
                        if (src.id.isBlank()) {
                            toast("书源ID不能为空")
                            return@let
                        }

                        val bookSource = viewModel.getBookSource(src.id)
                        MaterialAlertDialogBuilder(this@EditJavaScriptActivity).apply {
                            setTitle("确认保存")
                            if (bookSource != null) {
                                setMessage("已存在相同ID的书源，是否覆盖？")
                            } else {
                                setMessage("是否新建书源？")
                            }

                            setPositiveButton(if (bookSource != null) "覆盖" else "新建") { _, _ ->
                                lifecycle.launch {
                                    try {
                                        viewModel.saveJs(src)
                                        toast("书源保存成功")
                                        // 返回时通知调用方刷新
                                        setResult(RESULT_OK)
                                        finish()
                                    } catch (t: Throwable) {
                                        toast("保存书源失败: ${t.message}")
                                    }
                                }
                            }
                            setNegativeButton("取消", null)
                        }.show()


                    } catch (t: Throwable) {
                        toast("保存书源失败: ${t.message}")
                    }
                }
            }
        }

        var lastTime = 0L
        setOnBackPressed {
            val rootInsets = ViewCompat.getRootWindowInsets(binding!!.root)
            val imeVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (imeVisible) {
                WindowInsetsControllerCompat(window, binding!!.root).hide(WindowInsetsCompat.Type.ime())
                return@setOnBackPressed true
            }
            if (System.currentTimeMillis() - lastTime > 1000) {
                toast("再按一次退出")
                lastTime = System.currentTimeMillis()
                return@setOnBackPressed true
            } else {
                return@setOnBackPressed false
            }
        }
        setOnBackPressed {
            if (binding!!.settingsPanel.isVisible) {
                binding!!.settingsPanel.visibility = android.view.View.GONE
                binding!!.editInfo.text = "编辑信息"
                return@setOnBackPressed true
            }
            return@setOnBackPressed false
        }
    }
}
