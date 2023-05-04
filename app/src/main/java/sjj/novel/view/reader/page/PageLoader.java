package sjj.novel.view.reader.page;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jaeger.library.OnSelectListener;
import com.jaeger.library.SelectableTextHelper;
import com.jaeger.library.SelectionInfo;
import com.jaeger.library.TxtLocation;
import com.sjianjun.reader.BuildConfig;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import kotlin.text.CharsKt;
import kotlin.text.StringsKt;
import sjj.alog.Log;
import sjj.novel.view.reader.bean.BookBean;
import sjj.novel.view.reader.bean.BookRecordBean;
import sjj.novel.view.reader.record.ReadSettingManager;
import sjj.novel.view.reader.utils.RxUtils;
import sjj.novel.view.reader.utils.ScreenUtils;
import sjj.novel.view.reader.utils.StringUtils;

/**
 * Created by newbiechen on 17-7-1.
 */

@SuppressWarnings("ALL")
public abstract class PageLoader implements OnSelectListener {
    private static final String TAG = "PageLoader";

    // 当前页面的状态
    public static final int STATUS_LOADING = 1;         // 正在加载
    public static final int STATUS_FINISH = 2;          // 加载完成
    public static final int STATUS_ERROR = 3;           // 加载错误 (一般是网络加载情况)
    public static final int STATUS_EMPTY = 4;           // 空数据
    public static final int STATUS_PARING = 5;          // 正在解析 (装载本地数据)
    public static final int STATUS_PARSE_ERROR = 6;     // 本地文件解析错误(暂未被使用)
    public static final int STATUS_CATEGORY_EMPTY = 7;  // 获取到的目录为空

    // 当前章节列表
    protected List<TxtChapter> mChapterList;
    // 书本对象
    protected BookBean mCollBook;
    // 监听器
    protected OnPageChangeListener mPageChangeListener;

    private Context mContext;
    // 页面显示类
    private PageView mPageView;
    // 当前显示的页
    private TxtPage mCurPage;
    private Paint.FontMetrics metrics = new Paint.FontMetrics();
    // 上一章的页面列表缓存
    private List<TxtPage> mPrePageList;
    // 当前章节的页面列表
    private List<TxtPage> mCurPageList;
    // 下一章的页面列表缓存
    private List<TxtPage> mNextPageList;

    // 绘制提示的画笔
    private Paint mTipPaint;
    // 绘制标题的画笔
    private TextPaint mTitlePaint;
    // 绘制背景颜色的画笔(用来擦除需要重绘的部分)
    private Paint mSelectedPaint;
    // 绘制小说内容的画笔
    private TextPaint mTextPaint;
    // 阅读器的配置选项
    private ReadSettingManager mSettingManager;
    // 被遮盖的页，或者认为被取消显示的页
    private TxtPage mCancelPage;
    // 存储阅读记录类
    private BookRecordBean mBookRecord = new BookRecordBean();

    private Disposable mPreLoadDisp;

    /*****************params**************************/
    // 当前的状态
    protected int mStatus = STATUS_LOADING;
    // 判断章节列表是否加载完成
    protected boolean isChapterListPrepare;

    // 是否打开过章节
    private boolean isChapterOpen;
    private boolean isFirstOpen = true;
    private boolean isClose;
    // 页面的翻页效果模式
    private PageMode mPageMode;
    //当前是否是夜间模式
    private boolean isNightMode;
    private DisplayParams mDisplayParams;
    //字体的颜色
    private int mTextColor;
    //标题的大小
    private float mTitleSize;
    //字体的大小
    private float mTextSize;
    //电池的百分比
    private int mBatteryLevel;
    //当前页面的背景
    private Drawable mBackground;

    // 当前章
    protected int mCurChapterPos = 0;
    //上一章的记录
    private int mLastChapterPos = 0;
    private ScreenUtils screenUtils;

    private SelectableTextHelper mSelectableTextHelper;
    private TxtLocationImpl mLocation = new TxtLocationImpl();

    /*****************************init params*******************************/
    public PageLoader(PageView pageView) {
        mPageView = pageView;
        mContext = pageView.getContext();
        mDisplayParams = new DisplayParams();
        mChapterList = new ArrayList<>(1);
        screenUtils = new ScreenUtils(mContext);
        mSelectableTextHelper = new SelectableTextHelper.Builder(pageView, mLocation).build();
        mSelectableTextHelper.setSelectListener(this);
        // 初始化数据
        initData();
        // 初始化画笔
        initPaint();
        // 初始化PageView
        initPageView();
        // 初始化页面样式
        setPageStyle(mSettingManager.getPageStyle());
    }

    private void initData() {
        // 获取配置管理器
        mSettingManager = new ReadSettingManager(mContext);
        // 获取配置参数
        mPageMode = mSettingManager.getPageMode();
        // 配置文字有关的参数
        setUpTextParams(mSettingManager.getTextSize(), mSettingManager.getLineSpace());
    }

    public void setBook(BookBean book) {
        TxtChapter.evictAll();
        mCollBook = book;
    }

    /**
     * 作用：设置与文字相关的参数
     *
     * @param textSize
     */
    private void setUpTextParams(float textSize, float lineSpace) {
        mSettingManager.setLineSpace(lineSpace);
        // 文字大小
        mTextSize = textSize;
        mDisplayParams.setTextInterval(mTextSize * lineSpace);
        mDisplayParams.setTextPara(mTextSize * 1.5f);

        mTitleSize = mTextSize * 1.1f;
        mDisplayParams.setTitleInterval(mDisplayParams.getTextInterval());
        mDisplayParams.setTitlePara(mTitleSize * 1.5f);
    }

    private void initPaint() {
        // 绘制提示的画笔
        mTipPaint = new Paint();
        mTipPaint.setTextAlign(Paint.Align.LEFT); // 绘制的起始点
        mTipPaint.setTextSize(screenUtils.spToPx(12)); // Tip默认的字体大小
        mTipPaint.setAntiAlias(true);
        mTipPaint.setSubpixelText(true);

        // 绘制页面内容的画笔
        mTextPaint = new TextPaint();
        mTextPaint.setSubpixelText(true);
        mTextPaint.setAntiAlias(true);

        // 绘制标题的画笔
        mTitlePaint = new TextPaint();
        mTitlePaint.setSubpixelText(true);
        mTitlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTitlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        mTitlePaint.setAntiAlias(true);

        // 绘制背景的画笔
        mSelectedPaint = new Paint();
        mSelectedPaint.setSubpixelText(true);
        mSelectedPaint.setAntiAlias(true);
        mSelectedPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    private void initPageView() {
        //配置参数
        mPageView.setPageMode(mPageMode);
        mPageView.setBackground(mBackground);
    }

    /****************************** public method***************************/
    /**
     * 跳转到上一章
     *
     * @return
     */
    public boolean skipPreChapter() {
        if (!hasPrevChapter()) {
            return false;
        }

        // 载入上一章。
        if (parsePrevChapter()) {
            mCurPage = getCurPage(0);
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawCurPage(false);
        return true;
    }

    /**
     * 跳转到下一章
     *
     * @return
     */
    public boolean skipNextChapter() {
        if (!hasNextChapter()) {
            return false;
        }

        //判断是否达到章节的终止点
        if (parseNextChapter()) {
            mCurPage = getCurPage(0);
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawCurPage(false);
        return true;
    }

    public void refreshChapter(TxtChapter chapter) {
        if (chapter == mChapterList.get(mCurChapterPos)) skipToChapter(mCurChapterPos);
    }

    /**
     * 跳转到指定章节
     *
     * @param pos:从 0 开始。
     */
    public void skipToChapter(int pos) {
        // 设置参数
        mCurChapterPos = pos;

        // 将上一章的缓存设置为null
        mPrePageList = null;
        // 如果当前下一章缓存正在执行，则取消
        if (mPreLoadDisp != null) {
            mPreLoadDisp.dispose();
        }
        // 将下一章缓存设置为null
        mNextPageList = null;

        // 打开指定章节
        openChapter();
    }

    /**
     * 跳转到指定的页
     *
     * @param pos
     */
    public boolean skipToPage(int pos) {
        if (!isChapterListPrepare) {
            return false;
        }
        mCurPage = getCurPage(pos);
        mPageView.drawCurPage(false);
        return true;
    }

    /**
     * 翻到上一页
     *
     * @return
     */
    public boolean skipToPrePage() {
        return mPageView.autoPrevPage();
    }

    /**
     * 翻到下一页
     *
     * @return
     */
    public boolean skipToNextPage() {
        return mPageView.autoNextPage();
    }

    /**
     * 更新时间
     */
    public void updateTime() {
        if (!mPageView.isRunning()) {
            mPageView.drawCurPage(true);
        }
    }

    /**
     * 更新电量
     *
     * @param level
     */
    public void updateBattery(int level) {
        mBatteryLevel = level;

        if (!mPageView.isRunning()) {
            mPageView.drawCurPage(true);
        }
    }

    /**
     * 设置提示的文字大小
     *
     * @param textSize:单位为 px。
     */
    public void setTipTextSize(int textSize) {
        mTipPaint.setTextSize(textSize);

        // 如果屏幕大小加载完成
        mPageView.drawCurPage(false);
    }

    public void setTextSizeIncrease(boolean increase) {
        if (increase) {
            setTextSize(mTextSize + screenUtils.spToPx(1), mSettingManager.getLineSpace());
        } else {
            setTextSize(mTextSize - screenUtils.spToPx(1), mSettingManager.getLineSpace());
        }
    }

    /**
     * 设置文字相关参数
     *
     * @param textSize
     */
    public void setTextSize(float textSize, float lineSpace) {
        // 设置文字相关参数
        setUpTextParams(textSize, lineSpace);

        // 设置画笔的字体大小
        mTextPaint.setTextSize(mTextSize);
        // 设置标题的字体大小
        mTitlePaint.setTextSize(mTitleSize);
        // 存储文字大小
        mSettingManager.setTextSize(mTextSize);
        Log.e("字体大小：" + mTextSize + " 标题大小:" + mTitleSize);
        // 取消缓存
        mPrePageList = null;
        mNextPageList = null;

        // 如果当前已经显示数据
        if (isChapterListPrepare && mStatus == STATUS_FINISH) {
            // 重新计算当前页面
            dealLoadPageList(mCurChapterPos);

            // 防止在最后一页，通过修改字体，以至于页面数减少导致崩溃的问题
            if (mCurPage.position >= mCurPageList.size()) {
                mCurPage.position = mCurPageList.size() - 1;
            }

            // 重新获取指定页面
            mCurPage = mCurPageList.get(mCurPage.position);
        }

        mPageView.drawCurPage(false);
    }

    /**
     * 设置页面样式
     *
     * @param pageStyle:页面样式
     */
    public void setPageStyle(PageStyle pageStyle) {
        mSettingManager.setPageStyle(pageStyle);
        // 设置当前颜色样式
        mTextColor = pageStyle.getChapterContentColor(mContext);
        mBackground = pageStyle.getBg(mContext);

        mTipPaint.setColor(pageStyle.getLabelColor(mContext));
        mTitlePaint.setColor(pageStyle.getChapterTitleColor(mContext));
        mTextPaint.setColor(mTextColor);
        mSelectedPaint.setColor(pageStyle.getSelectedColor(mContext));

        mPageView.drawCurPage(false);
    }

    /**
     * 翻页动画
     *
     * @param pageMode:翻页模式
     * @see PageMode
     */
    public void setPageMode(PageMode pageMode) {
        mPageMode = pageMode;

        mPageView.setPageMode(mPageMode);
        mSettingManager.setPageMode(mPageMode);

        // 重新绘制当前页
        mPageView.drawCurPage(false);
    }

    /**
     * 设置内容与屏幕的间距
     *
     * @param marginWidth  :单位为 px
     * @param marginHeight :单位为 px
     */
    public void setPadding(int paddingWidth, int marginHeight) {
        mDisplayParams.setPaddingWidth(paddingWidth);
        mDisplayParams.setPaddingHeight(marginHeight);
        // 如果是滑动动画，则需要重新创建了
        if (mPageMode == PageMode.SCROLL) {
            mPageView.setPageMode(PageMode.SCROLL);
        }

        mPageView.drawCurPage(false);
    }

    /**
     * 设置页面切换监听
     *
     * @param listener
     */
    public void setOnPageChangeListener(OnPageChangeListener listener) {
        mPageChangeListener = listener;

        // 如果目录加载完之后才设置监听器，那么会默认回调
        if (isChapterListPrepare) {
            mPageChangeListener.onCategoryFinish(mChapterList);
        }
    }

    /**
     * 获取当前页的状态
     *
     * @return
     */
    public int getPageStatus() {
        return mStatus;
    }

    public void setPageStatus(int status) {
        mStatus = status;
        mPageView.drawCurPage(false);
    }

    /**
     * 获取书籍信息
     *
     * @return
     */
    @Nullable
    public BookBean getBook() {
        return mCollBook;
    }

    /**
     * 获取章节目录。
     *
     * @return
     */
    public List<TxtChapter> getChapterCategory() {
        return mChapterList;
    }

    /**
     * 获取当前页的页码
     *
     * @return
     */
    public int getPagePos() {
        return mCurPage == null ? 0 : mCurPage.position;
    }

    public int getPageCount() {
        return mCurPageList == null ? 0 : mCurPageList.size();
    }

    /**
     * 获取当前章节的章节位置
     *
     * @return
     */
    public int getChapterPos() {
        return mCurChapterPos;
    }

    public TxtChapter getCurChapter() {
        if (mChapterList == null) {
            return null;
        }
        return mChapterList.size() > mCurChapterPos ? mChapterList.get(mCurChapterPos) : null;
    }

    /**
     * 获取距离屏幕的高度
     *
     * @return
     */
    public int getMarginHeight() {
        return Math.round(mDisplayParams.getTipHeight());
    }

    /**
     * 保存阅读记录
     */
    public void saveRecord() {
        List<TxtChapter> chapterList = this.mChapterList;
        BookBean collBook = this.mCollBook;
        TxtPage curPage = this.mCurPage;
        BookRecordBean bookRecord = this.mBookRecord;
        int curChapterPos = this.mCurChapterPos;
        List<TxtPage> curPageList = this.mCurPageList;
        if (!isChapterOpen || chapterList.isEmpty() || collBook == null || curPage == null || curPageList == null) {
            return;
        }
//        if (curChapterPos == bookRecord.chapter && bookRecord.pagePos == curPage.position) {
//            return;
//        }
        bookRecord.bookId = collBook.id;
        bookRecord.chapter = curChapterPos;
        bookRecord.pagePos = curPage.position;

        bookRecord.isEnd = curChapterPos == chapterList.size() - 1 && curPageList.size() == curPage.position + 1;
        mPageChangeListener.onBookRecordChange(bookRecord);
    }

    public void setBookRecord(BookRecordBean record) {
        Log.i("设置阅读记录");
        mBookRecord = record;
        mCurChapterPos = record.chapter;
        mLastChapterPos = mCurChapterPos;
        if (isChapterOpen) {
            skipToChapter(record.chapter);
            skipToPage(record.pagePos);
        }
    }

    /**
     * 打开指定章节
     */
    public void openChapter() {
        isFirstOpen = false;

        if (!mPageView.isPrepare()) {
            Log.e("阅读页没准备好");
            return;
        }

        // 如果章节目录没有准备好
        if (!isChapterListPrepare) {
            Log.e("章节数没准备好");
            mStatus = STATUS_LOADING;
            mPageView.drawCurPage(false);
            return;
        }

        // 如果获取到的章节目录为空
        if (mChapterList.isEmpty()) {
            Log.e("章节为空");
            mStatus = STATUS_CATEGORY_EMPTY;
            mPageView.drawCurPage(false);
            return;
        }

        if (parseCurChapter()) {
            Log.e("章节解析完成 " + mCurPageList.size());
            // 如果章节从未打开
            if (!isChapterOpen) {
                int position = mBookRecord.pagePos;

                // 防止记录页的页号，大于当前最大页号
                if (position >= mCurPageList.size()) {
                    position = mCurPageList.size() - 1;
                }
                mCurPage = getCurPage(position);
                mCancelPage = mCurPage;
                // 切换状态
                isChapterOpen = true;
            } else {
                mCurPage = getCurPage(0);
            }
        } else {
            mCurPage = new TxtPage();
        }

        mPageView.drawCurPage(false);
    }

    public void chapterError() {
        //加载错误
        mStatus = STATUS_ERROR;
        mPageView.drawCurPage(false);
    }

    /**
     * 关闭书本
     */
    public void closeBook() {
        isChapterListPrepare = false;
        isClose = true;

        if (mPreLoadDisp != null) {
            mPreLoadDisp.dispose();
        }

        clearList(mChapterList);
        clearList(mCurPageList);
        clearList(mNextPageList);

        mChapterList = null;
        mCurPageList = null;
        mNextPageList = null;
        mPageView = null;
        mCurPage = null;
    }

    private void clearList(List list) {
        if (list != null) {
            list.clear();
        }
    }

    public boolean isClose() {
        return isClose;
    }

    public boolean isChapterOpen() {
        return isChapterOpen;
    }

    /**
     * 加载页面列表
     *
     * @param chapterPos:章节序号
     * @return
     */
    private List<TxtPage> loadPageList(int chapterPos) throws Exception {
        // 获取章节
        TxtChapter chapter = mChapterList.get(chapterPos);
        // 判断章节是否存在
        if (!hasChapterData(chapter)) {
            return null;
        }
        // 获取章节的文本流
        List<TxtPage> chapters = loadPages(chapter);
        return chapters;
    }

    /*******************************abstract method***************************************/

    /**
     * 刷新章节列表
     */
    public abstract void refreshChapterList();

    /**
     * 章节数据是否存在
     *
     * @return
     */
    protected boolean hasChapterData(TxtChapter chapter) {
        return chapter.getContent() != null;
    }

    /***********************************default method***********************************************/

    void drawPage(Bitmap bitmap, boolean isUpdate) {

        drawBackground(mPageView.getBgBitmap(), isUpdate);
        if (!isUpdate) {
            drawContent(bitmap);
        }
        Log.i("书籍绘制完成");
        //更新绘制
        mPageView.invalidate();
    }

    private void drawBackground(Bitmap bitmap, boolean isUpdate) {
        Log.i("绘制背景 Width:" + bitmap.getWidth() + " Height:" + bitmap.getHeight());
        Canvas canvas = new Canvas(bitmap);
        int tipMarginHeight = screenUtils.dpToPx(3);
        if (!isUpdate) {
            /****绘制背景****/
            if (mBackground != null) {
                mBackground.draw(canvas);
            } else {
                Log.e("没设置背景？");
            }

            if (!mChapterList.isEmpty()) {
                /*****初始化标题的参数********/
                //需要注意的是:绘制text的y的起始点是text的基准线的位置，而不是从text的头部的位置
                float tipTop = mDisplayParams.getTipHeight() / 2 + (mTipPaint.getFontMetrics().bottom - mTipPaint.getFontMetrics().top) / 2;
                //根据状态不一样，数据不一样
                if (mStatus != STATUS_FINISH) {
                    if (isChapterListPrepare) {
                        canvas.drawText(mChapterList.get(mCurChapterPos).title, mDisplayParams.getContentLeft(), tipTop, mTipPaint);
                    }
                } else {
                    /******绘制页码********/
                    String percent = (mCurPage.position + 1) + "/" + mCurPageList.size();
                    canvas.drawText(percent, mDisplayParams.getContentRight() - mTipPaint.measureText(percent), tipTop, mTipPaint);

                    int count = mTipPaint.breakText(mCurPage.title, true, mDisplayParams.getContentRight() - mTipPaint.measureText(percent) - tipMarginHeight, null);
                    canvas.drawText(mCurPage.title, 0, count, mDisplayParams.getContentLeft(), tipTop, mTipPaint);
                }

            }
        } else {
            throw new UnsupportedOperationException("不支持绘制时间。后续再改");
        }
    }

    private void drawContent(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        Log.i("绘制书籍内容 w:" + bitmap.getWidth() + " h:" + bitmap.getHeight() + " mStatus:" + mStatus);
        if (mPageMode == PageMode.SCROLL) {
            if (mBackground != null) {
                mBackground.draw(canvas);
            }
        }
        if (BuildConfig.DEBUG) {
            canvas.drawLine(mDisplayParams.getContentLeft(), 0f, mDisplayParams.getContentLeft(), mDisplayParams.getHeight(), mTitlePaint);
            canvas.drawLine(mDisplayParams.getContentRight(), 0f, mDisplayParams.getContentRight(), mDisplayParams.getHeight(), mTitlePaint);

            canvas.drawLine(0f, mDisplayParams.getContentTop(), mDisplayParams.getWidth(), mDisplayParams.getContentTop(), mTitlePaint);
            canvas.drawLine(0f, mDisplayParams.getContentBottom(), mDisplayParams.getWidth(), mDisplayParams.getContentBottom(), mTitlePaint);
        }

        /******绘制内容****/
        if (mStatus != STATUS_FINISH) {
            //绘制字体
            String tip = "";
            switch (mStatus) {
                case STATUS_LOADING:
                    tip = "正在拼命加载中...";
                    break;
                case STATUS_ERROR:
                    tip = "加载失败(点击边缘重试)";
                    break;
                case STATUS_EMPTY:
                    tip = "文章内容为空";
                    break;
                case STATUS_PARING:
                    tip = "正在排版请等待...";
                    break;
                case STATUS_PARSE_ERROR:
                    tip = "文件解析错误";
                    break;
                case STATUS_CATEGORY_EMPTY:
                    tip = "目录列表为空";
                    break;
            }

            //将提示语句放到正中间
            Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
            float textHeight = fontMetrics.top - fontMetrics.bottom;
            float textWidth = mTextPaint.measureText(tip);
            float pivotX = (mDisplayParams.getContentWidth() - textWidth) / 2;
            float pivotY = (mDisplayParams.getContentHeight() - textHeight) / 2;
            canvas.drawText(tip, pivotX, pivotY, mTextPaint);
        } else {
            if (mSelectableTextHelper.mSelectionInfo.select) {
                int start = mSelectableTextHelper.mSelectionInfo.start;
                int end = mSelectableTextHelper.mSelectionInfo.end;

                int startLine = mLocation.getLineForOffset(start);
                int endLine = mLocation.getLineForOffset(end);
//                Log.e("startLine:" + startLine + " endLine:" + endLine + " start:" + start + " end:" + end);
                for (int i = startLine; i <= endLine; i++) {
                    TxtLine txtLine = mCurPage.lines.get(i);
                    float left;
                    float right;
                    if (startLine == i) {
                        left = mLocation.getHorizontalLeft(start);
                    } else {
                        left = mLocation.getLineStart(i);
                    }
                    if (endLine == i) {
                        right = mLocation.getHorizontalRight(end);
                    } else {
                        right = mLocation.getLineEnd(i);
                    }
//                    Log.e("left:" + left + " top:" + txtLine.top + " right:" + right + " bottom:" + txtLine.bottom + " " + txtLine);
                    canvas.drawRect(left, txtLine.top, right, txtLine.bottom, mSelectedPaint);
                }

            }

            float top;

            if (mPageMode == PageMode.SCROLL) {
                top = -mTextPaint.getFontMetrics().top;
            } else {
                top = mDisplayParams.getTipHeight() - mTextPaint.getFontMetrics().top;
            }
            Paint.FontMetrics titleMetrics = mTitlePaint.getFontMetrics();
            float titleBase = -titleMetrics.ascent;
            Paint.FontMetrics textMetrics = mTextPaint.getFontMetrics();
            float textBase = -textMetrics.ascent;
            for (TxtLine line : mCurPage.lines) {
                float y = line.top + (line.isTitle ? titleBase : textBase);
                Paint paint = line.isTitle ? mTitlePaint : mTextPaint;
                for (int i = 0; i < line.txt.length(); i++) {
                    canvas.drawText(line.txt, i, i + 1, line.charLeft[i], y, paint);
                }
//                canvas.drawText(line.txt, line.left, y, paint);

                if (BuildConfig.DEBUG) {
                    canvas.drawLine(line.left, line.top, line.right, line.top, mTitlePaint);
                    canvas.drawLine(line.left, line.bottom, line.right, line.bottom, mTitlePaint);
                }
            }
        }
    }

    void prepareDisplay(int w, int h) {
        // 获取PageView的宽高
        mDisplayParams.setWidth(w);
        mDisplayParams.setHeight(h);
        // 获取内容显示位置的大小
        // 重置 PageMode
        mPageView.setPageMode(mPageMode);

        if (!isChapterOpen) {
            // 展示加载界面
            mPageView.drawCurPage(false);
            // 如果在 display 之前调用过 openChapter 肯定是无法打开的。
            // 所以需要通过 display 再重新调用一次。
            if (!isFirstOpen) {
                // 打开书籍
                openChapter();
            }
        } else {
            // 如果章节已显示，那么就重新计算页面
            if (mStatus == STATUS_FINISH) {
                dealLoadPageList(mCurChapterPos);
                // 重新设置文章指针的位置
                mCurPage = getCurPage(mCurPage.position);
            }
            mPageView.drawCurPage(false);
        }
    }

    /**
     * 翻阅上一页
     *
     * @return
     */
    boolean prev() {
        // 以下情况禁止翻页
        if (!canTurnPage()) {
            return false;
        }

        if (mStatus == STATUS_FINISH) {
            // 先查看是否存在上一页
            TxtPage prevPage = getPrevPage();
            if (prevPage != null) {
                mCancelPage = mCurPage;
                mCurPage = prevPage;
                mPageView.drawNextPage();
                return true;
            }
        }

        if (!hasPrevChapter()) {
            return false;
        }

        mCancelPage = mCurPage;
        if (parsePrevChapter()) {
            mCurPage = getPrevLastPage();
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawNextPage();
        return true;
    }

    /**
     * 解析上一章数据
     *
     * @return:数据是否解析成功
     */
    boolean parsePrevChapter() {
        // 加载上一章数据
        int prevChapter = mCurChapterPos - 1;

        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = prevChapter;

        // 当前章缓存为下一章
        mNextPageList = mCurPageList;

        // 判断是否具有上一章缓存
        if (mPrePageList != null) {
            mCurPageList = mPrePageList;
            mPrePageList = null;

            // 回调
            chapterChangeCallback();
        } else {
            dealLoadPageList(prevChapter);
        }
        return mCurPageList != null ? true : false;
    }

    private boolean hasPrevChapter() {
        //判断是否上一章节为空
        if (mCurChapterPos - 1 < 0) {
            return false;
        }
        return true;
    }

    /**
     * 翻到下一页
     *
     * @return:是否允许翻页
     */
    boolean next() {
        // 以下情况禁止翻页
        if (!canTurnPage()) {
            return false;
        }

        if (mStatus == STATUS_FINISH) {
            // 先查看是否存在下一页
            TxtPage nextPage = getNextPage();
            if (nextPage != null) {
                mCancelPage = mCurPage;
                mCurPage = nextPage;
                mPageView.drawNextPage();
                return true;
            }
        }

        if (!hasNextChapter()) {
            return false;
        }

        mCancelPage = mCurPage;
        // 解析下一章数据
        if (parseNextChapter()) {
            mCurPage = mCurPageList.get(0);
        } else {
            mCurPage = new TxtPage();
        }
        mPageView.drawNextPage();
        return true;
    }

    private boolean hasNextChapter() {
        // 判断是否到达目录最后一章
        if (mCurChapterPos + 1 >= mChapterList.size()) {
            return false;
        }
        return true;
    }

    boolean parseCurChapter() {
        // 解析数据
        Log.e("章节分页");
        dealLoadPageList(mCurChapterPos);
        // 预加载下一页面
        Log.e("预加载");
        preLoadNextChapter();
        return mCurPageList != null ? true : false;
    }

    /**
     * 解析下一章数据
     *
     * @return:返回解析成功还是失败
     */
    boolean parseNextChapter() {
        int nextChapter = mCurChapterPos + 1;

        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = nextChapter;

        // 将当前章的页面列表，作为上一章缓存
        mPrePageList = mCurPageList;

        // 是否下一章数据已经预加载了
        if (mNextPageList != null) {
            mCurPageList = mNextPageList;
            mNextPageList = null;
            // 回调
            chapterChangeCallback();
        } else {
            // 处理页面解析
            dealLoadPageList(nextChapter);
        }
        // 预加载下一页面
        preLoadNextChapter();
        return mCurPageList != null ? true : false;
    }

    private void dealLoadPageList(int chapterPos) {
        try {
            mCurPageList = loadPageList(chapterPos);
            if (mCurPageList != null) {
                if (mCurPageList.isEmpty()) {
                    mStatus = STATUS_EMPTY;

                    // 添加一个空数据
                    TxtPage page = new TxtPage();
                    page.lines = new ArrayList<>(1);
                    mCurPageList.add(page);
                } else {
                    mStatus = STATUS_FINISH;
                }
            } else {
                mStatus = STATUS_LOADING;
            }
        } catch (Exception e) {
            Log.e("分页加载失败", e);
            mCurPageList = null;
            mStatus = STATUS_ERROR;
        }

        // 回调
        chapterChangeCallback();
    }

    private void chapterChangeCallback() {
        if (mPageChangeListener != null) {
            mPageChangeListener.onChapterChange(mCurChapterPos);
            mPageChangeListener.onPageCountChange(mCurPageList != null ? mCurPageList.size() : 0);
        }
    }

    // 预加载下一章
    private void preLoadNextChapter() {
        int nextChapter = mCurChapterPos + 1;

        // 如果不存在下一章，且下一章没有数据，则不进行加载。
        if (!hasNextChapter() || !hasChapterData(mChapterList.get(nextChapter))) {
            return;
        }

        //如果之前正在加载则取消
        if (mPreLoadDisp != null) {
            mPreLoadDisp.dispose();
        }

        //调用异步进行预加载加载
        Single.create(new SingleOnSubscribe<List<TxtPage>>() {
            @Override
            public void subscribe(SingleEmitter<List<TxtPage>> e) throws Exception {
                e.onSuccess(loadPageList(nextChapter));
            }
        }).compose(RxUtils::toSimpleSingle).subscribe(new SingleObserver<List<TxtPage>>() {
            @Override
            public void onSubscribe(Disposable d) {
                mPreLoadDisp = d;
            }

            @Override
            public void onSuccess(List<TxtPage> pages) {
                mNextPageList = pages;
            }

            @Override
            public void onError(Throwable e) {
                //无视错误
            }
        });
    }

    // 取消翻页
    void pageCancel() {
        if (mCurPage.position == 0 && mCurChapterPos > mLastChapterPos) { // 加载到下一章取消了
            if (mPrePageList != null) {
                cancelNextChapter();
            } else {
                if (parsePrevChapter()) {
                    mCurPage = getPrevLastPage();
                } else {
                    mCurPage = new TxtPage();
                }
            }
        } else if (mCurPageList == null || (mCurPage.position == mCurPageList.size() - 1 && mCurChapterPos < mLastChapterPos)) {  // 加载上一章取消了

            if (mNextPageList != null) {
                cancelPreChapter();
            } else {
                if (parseNextChapter()) {
                    mCurPage = mCurPageList.get(0);
                } else {
                    mCurPage = new TxtPage();
                }
            }
        } else {
            // 假设加载到下一页，又取消了。那么需要重新装载。
            mCurPage = mCancelPage;
        }
    }

    private void cancelNextChapter() {
        int temp = mLastChapterPos;
        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = temp;

        mNextPageList = mCurPageList;
        mCurPageList = mPrePageList;
        mPrePageList = null;

        chapterChangeCallback();

        mCurPage = getPrevLastPage();
        mCancelPage = null;
    }

    private void cancelPreChapter() {
        // 重置位置点
        int temp = mLastChapterPos;
        mLastChapterPos = mCurChapterPos;
        mCurChapterPos = temp;
        // 重置页面列表
        mPrePageList = mCurPageList;
        mCurPageList = mNextPageList;
        mNextPageList = null;

        chapterChangeCallback();

        mCurPage = getCurPage(0);
        mCancelPage = null;
    }

    /**************************************private method********************************************/
    /**
     * 将章节数据，解析成页面列表
     *
     * @param chapter ：章节信息
     * @param br      ：章节的文本流
     * @return
     */
    private List<TxtPage> loadPages(TxtChapter chapter) {
        //生成的页面
        List<TxtPage> pages = new ArrayList<>();
        //使用流的方式加载
        List<TxtLine> lines = new ArrayList<>();

        createTxtLine(lines, chapter.title, mTitlePaint);
        int titleLines = lines.size();
        createTxtLine(lines, chapter.getContent(), mTextPaint);
        List<TxtLine> pageLine = new ArrayList<>();
        for (int i = 0; i < lines.size(); ) {
            float top = mDisplayParams.getContentTop();
            if (i == 0) {
                top += mDisplayParams.getTitlePara();
            }
            int nextCharStart = 0;
            while (i < lines.size()) {
                TxtLine line = lines.get(i);
                float remHeight = mDisplayParams.getContentBottom() - top - line.height;
                if (remHeight < 0) {
                    break;
                }
                line.left = mDisplayParams.getContentLeft();
                line.top = top;
                line.right = mDisplayParams.getContentRight();
                line.bottom = top + line.height;

                line.index = pageLine.size();
                line.charStart = nextCharStart;
                nextCharStart += line.txt.length();
                pageLine.add(line);
//                Log.e(line.txt + "-" + line.txt.endsWith("\n") + "-" + line.isTitle);
                i++;
                top += line.height;
                if (line.isTitle) {
                    if (line.index == titleLines - 1) {
                        top += mDisplayParams.getTitlePara();
                    } else {
                        top += mDisplayParams.getTitleInterval();
                    }
                } else {
                    if (line.txt.endsWith("\n")) {
                        top += mDisplayParams.getTextPara();
                    } else {
                        top += mDisplayParams.getTextInterval();
                    }
                }
            }
            TxtPage page = new TxtPage();
            page.position = pages.size();
            page.title = chapter.title;
            page.lines = new ArrayList<>(pageLine);
            pages.add(page);
            pageLine.clear();
        }
        return pages;
    }

    private void createTxtLine(List<TxtLine> lines, CharSequence text, TextPaint paint) {
        StringBuilder sb = new StringBuilder();
        for (String line : StringsKt.lines(text)) {
            line = StringsKt.trim(line).toString();
            if (!line.isEmpty()) {
                sb.append("　　").append(line).append(System.lineSeparator());
            }
        }
        text = StringsKt.trimEnd(sb);

        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.round(mDisplayParams.getContentWidth())).build();
        for (int i = 0; i < layout.getLineCount(); i++) {
            float left = layout.getLineLeft(i);
            float right = layout.getLineRight(i);
            int lineStart = layout.getLineStart(i);
            int lineEnd = layout.getLineEnd(i);
            int lineHeight = layout.getLineBottom(i) - layout.getLineTop(i);
            CharSequence lineStr = layout.getText().subSequence(lineStart, lineEnd);
            if (StringsKt.isBlank(lineStr)) {
                continue;
            }
            TxtLine line = new TxtLine(lineStr.toString(), paint == mTitlePaint, lineHeight, right - left);
            lines.add(line);
            for (int offset = lineStart; offset < lineEnd; offset++) {
                float leftOf = layout.getPrimaryHorizontal(offset);
                float rightOf = offset == lineEnd - 1 ? right : layout.getPrimaryHorizontal(offset + 1);
                line.setLeftOfRight(offset - lineStart, leftOf + mDisplayParams.getContentLeft(), rightOf + mDisplayParams.getContentLeft());
            }


            float extX = 0;
            int len = line.txt.length();
            int st = 0;

            while ((st < len) && (CharsKt.isWhitespace(line.txt.charAt(st)))) {
                st++;
            }
            while ((st < len) && (CharsKt.isWhitespace(line.txt.charAt(len - 1)))) {
                len--;
            }
            if (len - st <= 1) {
                continue;
            }

            float maxCharWidth = 0;
            for (int idx = 0; idx < line.charLeft.length; idx++) {
                maxCharWidth = Math.max(line.charRight[idx] - line.charLeft[idx], maxCharWidth);
            }

            float remWidth = mDisplayParams.getContentWidth() - line.width;
            if (remWidth > maxCharWidth * 2) {
                remWidth = maxCharWidth;
            }

            extX = remWidth / (len - st - 1);
            extX = Math.min(extX, maxCharWidth / 8f);

            for (int idx = 0; idx < line.charLeft.length; idx++) {
                float offsetX = Math.max((idx - st), 0) * extX;
                line.setLeftOfRight(idx, offsetX + line.charLeft[idx], offsetX + line.charRight[idx]);
            }
        }
    }

    /**
     * @return:获取初始显示的页面
     */
    private TxtPage getCurPage(int pos) {
        Log.e("获取书页：" + pos);
        pos = Math.min(pos, mCurPageList.size() - 1);
        pos = Math.max(0, pos);
        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }
        return mCurPageList.get(pos);
    }

    /**
     * @return:获取上一个页面
     */
    private TxtPage getPrevPage() {
        if (mCurPage == null) {
            return null;
        }
        int pos = mCurPage.position - 1;
        if (pos < 0) {
            return null;
        }
        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }
        return mCurPageList.get(pos);
    }

    /**
     * @return:获取下一的页面
     */
    private TxtPage getNextPage() {
        if (mCurPage == null) {
            return null;
        }
        int pos = mCurPage.position + 1;
        if (pos >= mCurPageList.size()) {
            return null;
        }
        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }
        return mCurPageList.get(pos);
    }

    /**
     * @return:获取上一个章节的最后一页
     */
    private TxtPage getPrevLastPage() {
        int pos = mCurPageList.size() - 1;

        if (mPageChangeListener != null) {
            mPageChangeListener.onPageChange(pos);
        }

        return mCurPageList.get(pos);
    }

    /**
     * 根据当前状态，决定是否能够翻页
     *
     * @return
     */
    private boolean canTurnPage() {

        if (!isChapterListPrepare) {
            return false;
        }

        if (mStatus == STATUS_PARSE_ERROR || mStatus == STATUS_PARING) {
            return false;
        } else if (mStatus == STATUS_ERROR) {
            mStatus = STATUS_LOADING;
        }
        return true;
    }

    public boolean hideSelectView() {
        if (!mSelectableTextHelper.mSelectionInfo.select) {
            return false;
        }
        mSelectableTextHelper.resetSelectionInfo();
        mSelectableTextHelper.hideSelectView();
        mPageView.drawNextPage();
        return true;
    }

    public void onLongPress(int x, int y) {
        Log.e("长按 x:" + x + " y:" + y);
        mSelectableTextHelper.showSelectView(x, y);
    }

    @Override
    public void onTextSelected(SelectionInfo info) {
        Log.e("被选中的文字");
    }

    @Override
    public void onTextSelectedChange(SelectionInfo info) {
        mPageView.drawNextPage();
    }

    private class TxtLocationImpl implements TxtLocation {
        @Override
        public int getLine(float y) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            for (TxtLine line : page.lines) {
                if (line.top <= y && line.bottom >= y) {
                    return line.index;
                }
            }
            return -1;
        }

        @Override
        public int getLineForOffset(int offset) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            for (TxtLine line : page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length() > offset) {
                    return line.index;
                }
            }
            return page.lines.size() - 1;
        }

        @Override
        public float getLineStart(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            if (StringsKt.isBlank(txtLine.txt)) {
                return -1;
            }
            for (int i = 0; i < txtLine.txt.length(); i++) {
                if (Character.isWhitespace(txtLine.txt.charAt(i))) {
                    continue;
                }
                return getHorizontalLeft(txtLine.charStart + i);
            }
            return -1;
        }

        @Override
        public int getLineStartOffset(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            return txtLine.charStart;
        }

        @Override
        public float getLineEnd(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            if (StringsKt.isBlank(txtLine.txt)) {
                return -1;
            }
            for (int i = txtLine.txt.length() - 1; i >= 0; i--) {
                if (Character.isWhitespace(txtLine.txt.charAt(i))) {
                    continue;
                }
                return getHorizontalRight(txtLine.charStart + i);
            }
            return -1;
        }

        @Override
        public int getOffset(float x, float y) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            int line = getLine(y);
            if (line == -1) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            Paint paint;
            if (txtLine.isTitle) {
                paint = mTitlePaint;
            } else {
                paint = mTextPaint;
            }
//            Log.e(txtLine);
//            Log.e(page.lines.get(line + 1));
//            Log.e("len:" + txtLine.txt.length() + ">>" + txtLine.txt);
            float lineStart = getLineStart(txtLine.index);
            if (x < lineStart) {
                return -1;
            }
            float lineEnd = getLineEnd(txtLine.index);
            if (x > lineEnd) {
                return -1;
            }
            for (int i = 0; i < txtLine.charLeft.length; i++) {
                if (x >= txtLine.charLeft[i] && x <= txtLine.charRight[i]) {
                    return i + txtLine.charStart;
                }
            }
            return -1;
        }

        @Override
        public int getHysteresisOffset(float x, float y, int oldOffset, boolean isLeft) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return oldOffset;
            }

            float difY = Integer.MAX_VALUE;
            int line = -1;
            for (TxtLine txtLine : page.lines) {
                if (!StringsKt.isBlank(txtLine.txt) && Math.abs(txtLine.bottom - y) < difY) {
                    line = txtLine.index;
                    difY = Math.abs(txtLine.bottom - y);
                }
            }
            if (line == -1) {
                return oldOffset;
            }
            TxtLine txtLine = page.lines.get(line);
//            Log.e(txtLine);
            float difX = Integer.MAX_VALUE;
            int lineOffset = -1;
            for (int i = 0; i < txtLine.txt.length(); i++) {
                if (!CharsKt.isWhitespace(txtLine.txt.charAt(i)) && Math.abs((txtLine.charLeft[i] + txtLine.charRight[i]) / 2 - x) < difX) {
                    lineOffset = i;
                    difX = Math.abs((txtLine.charLeft[i] + txtLine.charRight[i]) / 2 - x);
                }
            }
            return lineOffset == -1 ? oldOffset : (lineOffset + txtLine.charStart);
        }

        public int getNotWhitespace(int line, boolean isLeft) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            if (line < 0 || line >= page.lines.size()) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            if (StringsKt.isBlank(txtLine.txt)) {
                if (isLeft) {
                    return getNotWhitespace(line + 1, isLeft);
                } else {
                    return getNotWhitespace(line - 1, isLeft);
                }
            } else {
                if (isLeft) {
                    for (int i = 0; i < txtLine.txt.length(); i++) {
                        if (Character.isWhitespace(txtLine.txt.charAt(i))) {
                            continue;
                        }
                        return txtLine.charStart + i;
                    }
                } else {
                    for (int i = txtLine.txt.length() - 1; i >= 0; i--) {
                        if (Character.isWhitespace(txtLine.txt.charAt(i))) {
                            continue;
                        }
                        return txtLine.charStart + i;
                    }
                }
            }
            return -1;
        }

        @Override
        public float getHorizontalRight(int offset) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            for (TxtLine line : page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length() > offset) {
                    int lineOffset = offset - line.charStart;
                    return line.charRight[lineOffset];
                }
            }
            return -1;
        }

        @Override
        public float getHorizontalLeft(int offset) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            for (TxtLine line : page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length() > offset) {
                    int lineOffset = offset - line.charStart;
                    if (lineOffset == 0) {
                        return line.left;
                    }

                    return line.charLeft[lineOffset];
                }
            }
            return -1;
        }

        @Override
        public float getLineTop(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            return txtLine.top;
        }

        @Override
        public float getLineBottom(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            return txtLine.bottom;
        }

        @Override
        public String getTxt(int start, int end) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return "";
            }

            TxtLine startLine = page.lines.get(getLineForOffset(start));
            TxtLine endLine = page.lines.get(getLineForOffset(end));
            if (startLine == endLine) {
                return startLine.txt.substring(start - startLine.charStart, end - startLine.charStart + 1);
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = startLine.index; i <= endLine.index; i++) {
                TxtLine line = page.lines.get(i);
                if (line == startLine) {
                    stringBuilder.append(line.txt.substring(start - line.charStart));
                } else if (line == endLine) {
                    stringBuilder.append(line.txt.substring(0, end - line.charStart + 1));
                } else {
                    stringBuilder.append(line.txt);
                }
            }
            return stringBuilder.toString();
        }
    }


    /*****************************************interface*****************************************/

    public interface OnPageChangeListener {
        /**
         * 作用：章节切换的时候进行回调
         *
         * @param pos:切换章节的序号
         */
        void onChapterChange(int pos);

        /**
         * 作用：请求加载章节内容
         *
         * @param requestChapters:需要下载的章节列表
         */
        void requestChapters(List<TxtChapter> requestChapters);

        /**
         * 作用：章节目录加载完成时候回调
         *
         * @param chapters：返回章节目录
         */
        void onCategoryFinish(List<TxtChapter> chapters);

        /**
         * 作用：章节页码数量改变之后的回调。==> 字体大小的调整，或者是否关闭虚拟按钮功能都会改变页面的数量。
         *
         * @param count:页面的数量
         */
        void onPageCountChange(int count);

        /**
         * 作用：当页面改变的时候回调
         *
         * @param pos:当前的页面的序号
         */
        void onPageChange(int pos);

        /**
         * 书籍阅读记录发生改变
         *
         * @param bean
         */
        void onBookRecordChange(@NonNull BookRecordBean bean);
    }

}
