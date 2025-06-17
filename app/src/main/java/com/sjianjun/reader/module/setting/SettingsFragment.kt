package com.sjianjun.reader.module.setting

import android.os.Bundle
import android.text.Editable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogEditTextBinding
import com.sjianjun.reader.databinding.FragmentSettingsBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookSourceMgr
import com.sjianjun.reader.repository.WebDavMgr
import com.sjianjun.reader.utils.HttpServiceHelper
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.toast
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentSettingsBinding.bind(view).apply {

            webdavServerUrlInput.setText(globalConfig.webdavUrl)

            webdavUsernameInput.setText(globalConfig.webdavUsername)

            webdavPasswordInput.setText(globalConfig.webdavPassword)

            webdavDirInput.setText(globalConfig.webdavSubdir)

            webdavSave.setOnClickListener {
                launch {
                    globalConfig.apply {
                        webdavUrl = webdavServerUrlInput.text.toString().trim()
                        webdavUsername = webdavUsernameInput.text.toString().trim()
                        webdavPassword = webdavPasswordInput.text.toString().trim()
                        webdavSubdir = webdavDirInput.text.toString().trim()
                        webdavHasCfg = false
                    }

                    val init = WebDavMgr.setAccount(
                        webdavServerUrlInput.text.toString().trim(),
                        webdavUsernameInput.text.toString().trim(),
                        webdavPasswordInput.text.toString().trim(),
                        webdavDirInput.text.toString().trim()
                    )
                    globalConfig.webdavHasCfg = init.isSuccess
                    toast("WebDav配置${if (init.isSuccess) "成功" else "失败"}")
                    Log.i(init)
                    if (init.isSuccess) {
                        WebDavMgr.init()
                    }
                }
            }
            Log.i("bookCityUrl:" + globalConfig.bookCityUrl)
            bookCityUrlInput.text = globalConfig.bookCityUrl
            bookCityUrlInput.setOnClickListener {
                val bindingDialog = DialogEditTextBinding.inflate(LayoutInflater.from(requireContext()))
                bindingDialog.editView.apply {
                    val historyList = globalConfig.bookCityUrlHistoryList
                    setFilterValues(historyList)
                    delCallBack = {
                        if (historyList.remove(it)) {
                            globalConfig.bookCityUrlHistoryList = historyList
                        }
                    }
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("书城地址")
                    .setView(bindingDialog.root)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        var url = bindingDialog.editView.text.toString().trim().lowercase()
                        if (!url.startsWith("http")) {
                            url = "https://$url"
                        }
                        if (url.toHttpUrlOrNull() != null) {
                            val historyList = globalConfig.bookCityUrlHistoryList
                            historyList.remove(url)
                            historyList.add(0, url)
                            globalConfig.bookCityUrlHistoryList = historyList
                            globalConfig.bookCityUrl = url
                            bookCityUrlInput.text = url
                            toast("设置成功")
                        } else {
                            toast("请输入正确的网址")
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            HttpServiceHelper.isRunning.observe(viewLifecycleOwner) {
                debugServiceSwitch.isChecked = it
            }
            debugServiceSwitch.setOnCheckedChangeListener { btn, isChecked ->
                if (isChecked){
                    if (HttpServiceHelper.isRunning.value != true){
                        HttpServiceHelper.startHttpServer()
                    }
                }else{
                    HttpServiceHelper.stopHttpServer()
                }
            }

        }
    }

}