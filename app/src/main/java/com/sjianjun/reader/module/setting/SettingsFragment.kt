package com.sjianjun.reader.module.setting

import android.os.Bundle
import android.text.Editable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.WebDavMgr
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


        val checkWebDavSettingListener: (text: Editable?) -> Unit = {
            val props = listOf(
                webdav_server_url_input.text.toString(), webdav_username_input.text.toString(),
                webdav_password_input.text.toString(), webdav_dir_input.text.toString()
            )
            webdav_save.isEnabled = props.indexOfFirst { it.isBlank() } == -1
        }

        webdav_server_url_input.setText(globalConfig.webdavUrl)
        webdav_server_url_input.addTextChangedListener(afterTextChanged = checkWebDavSettingListener)

        webdav_username_input.setText(globalConfig.webdavUsername)
        webdav_username_input.addTextChangedListener(afterTextChanged = checkWebDavSettingListener)

        webdav_password_input.setText(globalConfig.webdavPassword)
        webdav_password_input.addTextChangedListener(afterTextChanged = checkWebDavSettingListener)

        webdav_dir_input.setText(globalConfig.webdavSubdir)
        webdav_dir_input.addTextChangedListener(afterTextChanged = checkWebDavSettingListener)

        webdav_save.setOnClickListener {
            launch {
                val init = WebDavMgr.init(
                    webdav_server_url_input.text.toString().trim(),
                    webdav_username_input.text.toString().trim(),
                    webdav_password_input.text.toString().trim(),
                    webdav_dir_input.text.toString().trim()
                )
                toast("WebDav配置${if (init.isSuccess) "成功" else "失败"}")
                if (init.isSuccess) {
                    webdav_save.isEnabled = false
                }
            }
        }
        webdav_save.isEnabled = false
    }

}