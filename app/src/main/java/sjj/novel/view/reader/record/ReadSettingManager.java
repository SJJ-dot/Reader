package sjj.novel.view.reader.record;

import android.content.Context;
import android.content.SharedPreferences;

import sjj.novel.view.reader.page.PageMode;
import sjj.novel.view.reader.page.PageStyle;
import sjj.novel.view.reader.utils.ScreenUtils;
import sjj.novel.view.reader.utils.SharedPreUtils;

/**
 * Created by newbiechen on 17-5-17.
 * 阅读器的配置管理
 */

public class ReadSettingManager {

    public static final String SHARED_READ_TEXT_SIZE = "shared_read_text_size1";
    private SharedPreferences sharedPreUtils;
    private ScreenUtils screenUtils;

    public ReadSettingManager(Context context) {
        sharedPreUtils = SharedPreUtils.getInstance(context);
        screenUtils = new ScreenUtils(context);
    }

    public void setTextSize(float textSize) {
        sharedPreUtils.edit().putFloat(SHARED_READ_TEXT_SIZE, textSize).apply();
    }

    public float getTextSize() {
        return sharedPreUtils.getFloat(SHARED_READ_TEXT_SIZE, screenUtils.spToPx(28));
    }

}
