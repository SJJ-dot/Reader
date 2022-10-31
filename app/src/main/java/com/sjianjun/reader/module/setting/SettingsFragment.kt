package com.sjianjun.reader.module.setting

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.toast
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import kotlinx.android.synthetic.main.fragment_settings.*

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

        webdav_server_url_input.setText(globalConfig.webdavUrl)
        webdav_server_url_input.addTextChangedListener {
            var url = it.toString().trim()
            if (!url.endsWith("/")) {
               url = "${url}/"
            }
            globalConfig.webdavUrl = url
            needSave()
        }

        webdav_username_input.setText(globalConfig.webdavUsername)
        webdav_username_input.addTextChangedListener {
            globalConfig.webdavUsername = it.toString().trim()
            needSave()
        }

        webdav_password_input.setText(globalConfig.webdavPassword)
        webdav_password_input.addTextChangedListener {
            globalConfig.webdavPassword = it.toString().trim()
            needSave()
        }

        webdav_dir_input.setText(globalConfig.webdavSubdir)
        webdav_dir_input.addTextChangedListener {
            globalConfig.webdavSubdir = it.toString().trim()
            needSave()
        }
        webdav_save.setOnClickListener {
            launch {
                val auth = Authorization(
                    globalConfig.webdavUsername ?: "",
                    globalConfig.webdavPassword ?: ""
                )
                val success = WebDav(
                    "${globalConfig.webdavUrl}${globalConfig.webdavSubdir}/",
                    auth
                ).makeAsDir()
                toast("WebDav配置${if (success) "成功" else "失败"}")
                if (success) {
                    globalConfig.webdavConfigStatus = 2
                    webdav_save.isEnabled = false
                }
            }
        }
        webdav_save.isEnabled = globalConfig.webdavConfigStatus == 1
    }

    private fun needSave() {
        val props = listOf(
            globalConfig.webdavUrl, globalConfig.webdavUsername,
            globalConfig.webdavPassword, globalConfig.webdavSubdir
        )

        webdav_save.isEnabled = props.indexOfFirst { it.isNullOrBlank() } == -1
        if (webdav_save.isEnabled) {
            globalConfig.webdavConfigStatus = 1
        } else {
            globalConfig.webdavConfigStatus = 0
        }
    }

}