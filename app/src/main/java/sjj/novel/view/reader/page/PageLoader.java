package sjj.novel.view.reader.page;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
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
    private int mTitleSize;
    //字体的大小
    private int mTextSize;
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
        mDisplayParams = new DisplayParams(pageView.getContext());
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
    private void setUpTextParams(int textSize, float lineSpace) {
        mSettingManager.setLineSpace(lineSpace);
        // 文字大小
        mTextSize = textSize;
        mTitleSize = mTextSize;
        mDisplayParams.setTextInterval((int) (mTextSize * lineSpace));
        mDisplayParams.setTitleInterval((int) (mTitleSize * lineSpace));
        mDisplayParams.setTextPara(mTextSize);
        mDisplayParams.setTitlePara(mTitleSize);
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
        if (chapter == mChapterList.get(mCurChapterPos))
            skipToChapter(mCurChapterPos);
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
    public void setTextSize(int textSize, float lineSpace) {
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
        return mDisplayParams.getTipHeight();
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
                    canvas.drawText(mCurPage.title, mDisplayParams.getContentLeft(), tipTop, mTipPaint);
                }

                /******绘制页码********/
                // 只有finish的时候采用页码
                if (mStatus == STATUS_FINISH) {
                    String percent = (mCurPage.position + 1) + "/" + mCurPageList.size();
                    canvas.drawText(percent, mDisplayParams.getContentRight() - mTipPaint.measureText(percent), tipTop, mTipPaint);
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

        canvas.drawLine(mDisplayParams.getContentLeft(), 0f, mDisplayParams.getContentLeft(), mDisplayParams.getHeight(), mTitlePaint);
        canvas.drawLine(mDisplayParams.getContentRight(), 0f, mDisplayParams.getContentRight(), mDisplayParams.getHeight(), mTitlePaint);

        canvas.drawLine(0f, mDisplayParams.getContentTop(), mDisplayParams.getWidth(), mDisplayParams.getContentTop(), mTitlePaint);
        canvas.drawLine(0f, mDisplayParams.getContentBottom(), mDisplayParams.getWidth(), mDisplayParams.getContentBottom(), mTitlePaint);

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
                    int left;
                    int right;
                    if (startLine == i) {
                        left = mLocation.getHorizontalLeft(start);
                    } else {
                        left = mLocation.getLineStart(i);
                    }
                    if (endLine == i) {
                        right = mLocation.getHorizontalLeft(end);
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

            //设置总距离
            int interval = mDisplayParams.getTextInterval() + (int) mTextPaint.getTextSize();
            int para = mDisplayParams.getTextPara() + (int) mTextPaint.getTextSize();
            int titleInterval = mDisplayParams.getTitleInterval() + (int) mTitlePaint.getTextSize();
            int titlePara = mDisplayParams.getTitlePara() + (int) mTextPaint.getTextSize();
            TxtLine line = null;

            //对标题进行绘制
            mTitlePaint.getFontMetrics(metrics);
            for (int i = 0; i < mCurPage.titleLines; ++i) {
                line = mCurPage.lines.get(i);
                //设置顶部间距
                if (i == 0) {
                    top += mDisplayParams.getTitlePara();
                }

                //计算文字显示的起始点
                int start = (int) (mDisplayParams.getWidth() - mTitlePaint.measureText(line.txt)) / 2;
                //进行绘制
                canvas.drawText(line.txt, start, top, mTitlePaint);
                line.left = start;
                line.top = Math.round(top + metrics.ascent);
                line.right = mDisplayParams.getWidth() - line.left;
                line.bottom = Math.round(top + metrics.descent);
                //设置尾部间距
                if (i == mCurPage.titleLines - 1) {
                    top += titlePara;
                } else {
                    //行间距
                    top += titleInterval;
                }
            }

            //对内容进行绘制
            for (int i = mCurPage.titleLines; i < mCurPage.lines.size(); ++i) {
                line = mCurPage.lines.get(i);

                canvas.drawText(line.txt, mDisplayParams.getPaddingWidth(), top, mTextPaint);
                line.left = mDisplayParams.getPaddingWidth();
                line.top = Math.round(top + metrics.ascent);
                line.right = mDisplayParams.getWidth() - line.left;
                line.bottom = Math.round(top + metrics.descent);
                if (line.txt.endsWith("\n")) {
                    top += para;
                } else {
                    top += interval;
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
            e.printStackTrace();

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
        if (!hasNextChapter()
                || !hasChapterData(mChapterList.get(nextChapter))) {
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
                }).compose(RxUtils::toSimpleSingle)
                .subscribe(new SingleObserver<List<TxtPage>>() {
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
        } else if (mCurPageList == null
                || (mCurPage.position == mCurPageList.size() - 1
                && mCurChapterPos < mLastChapterPos)) {  // 加载上一章取消了

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
//        String str = "啊啊啊啊啊啊";
//        float width = mTextPaint.measureText(str, 0, 1);
//        Log.e("width3:"+width);
//        int offset = mTextPaint.breakText(str, 0, str.length(), true, 30, null);
//        Log.e("offset30："+offset);
//        offset = mTextPaint.breakText(str, 0, str.length(), true, 80, null);
//        Log.e("offset80："+offset);
//        mTextPaint.measureText(str,)
        //生成的页面
        List<TxtPage> pages = new ArrayList<>();
        //使用流的方式加载
        List<TxtLine> lines = new ArrayList<>();
        String title = StringUtils.halfToFull(chapter.title);
        StaticLayout titleLayout = StaticLayout.Builder.obtain(title, 0, title.length(), mTitlePaint, mDisplayParams.getContentWidth()).build();

        int top = mDisplayParams.getContentTop();
        for (int i = 0; i < titleLayout.getLineCount(); i++) {
            int lineStart = titleLayout.getLineStart(i);
            int lineEnd = titleLayout.getLineEnd(i);
            TxtLine line = new TxtLine(titleLayout.getText().subSequence(lineStart, lineEnd), true);
            line
        }

        int rHeight = mDisplayParams.getContentHeight();
        int titleLinesCount = 0;
        boolean showTitle = true; // 是否展示标题
        String paragraph = chapter.title;//默认展示标题
        String[] strings = chapter.getContent().split("\n");
        int i = 0;
        while (showTitle || strings.length > i) {
            if (!showTitle) {
                paragraph = strings[i];
                i++;
            }
            // 重置段落
            if (!showTitle) {
                paragraph = paragraph.replaceAll("\\s", "");
                // 如果只有换行符，那么就不执行
                if (paragraph.equals("")) continue;
                paragraph = StringUtils.halfToFull("  " + paragraph + "\n");
            } else {
                //设置 title 的顶部间距
                rHeight -= mDisplayParams.getTitlePara();
            }
            int wordCount = 0;
            String subStr = null;
            while (paragraph.length() > 0) {
                //当前空间，是否容得下一行文字
                if (showTitle) {
                    rHeight -= mTitlePaint.getTextSize();
                } else {
                    rHeight -= mTextPaint.getTextSize();
                }
                // 一页已经填充满了，创建 TextPage
                if (rHeight <= 0) {
                    // 创建Page
                    TxtPage page = new TxtPage();
                    page.position = pages.size();
                    page.title = chapter.title;
                    page.lines = new ArrayList<>(lines);
                    page.titleLines = titleLinesCount;
                    pages.add(page);
                    // 重置Lines
                    lines.clear();
                    rHeight = mDisplayParams.getContentHeight();
                    titleLinesCount = 0;

                    continue;
                }

                //测量一行占用的字节数
                if (showTitle) {
                    wordCount = mTitlePaint.breakText(paragraph,
                            true, mDisplayParams.getContentWidth(), null);
                } else {
                    wordCount = mTextPaint.breakText(paragraph,
                            true, mDisplayParams.getContentWidth(), null);
                }

                subStr = paragraph.substring(0, wordCount);
                if (!subStr.equals("\n")) {
                    //将一行字节，存储到lines中
                    lines.add(new TxtLine(subStr, showTitle));

                    //设置段落间距
                    if (showTitle) {
                        titleLinesCount += 1;
                        rHeight -= mDisplayParams.getTitleInterval();
                    } else {
                        rHeight -= mDisplayParams.getTextInterval();
                    }
                }
                //裁剪
                paragraph = paragraph.substring(wordCount);
            }

            //增加段落的间距
            if (!showTitle && lines.size() != 0) {
                rHeight = rHeight - mDisplayParams.getTextPara() + mDisplayParams.getTextInterval();
            }

            if (showTitle) {
                rHeight = rHeight - mDisplayParams.getTitlePara() + mDisplayParams.getTitleInterval();
                showTitle = false;
            }
        }

        if (lines.size() != 0) {
            //创建Page
            TxtPage page = new TxtPage();
            page.position = pages.size();
            page.title = chapter.title;
            page.lines = new ArrayList<>(lines);
            page.titleLines = titleLinesCount;
            pages.add(page);
            //重置Lines
            lines.clear();
        }
        for (int idx = 0; idx < pages.size(); idx++) {
            TxtPage page = pages.get(idx);
            int nextCharStart = 0;
            for (int line = 0; line < page.lines.size(); line++) {
                TxtLine txtLine = page.lines.get(line);
                txtLine.index = line;
                txtLine.charStart = nextCharStart;
                nextCharStart = nextCharStart + txtLine.txt.length();
            }
        }
        return pages;
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

        if (mStatus == STATUS_PARSE_ERROR
                || mStatus == STATUS_PARING) {
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
        public int getLine(int y) {
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
        public int getLineStart(int line) {
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
        public int getLineEnd(int line) {
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
        public int getOffset(int x, int y) {
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
            int lineStart = getLineStart(txtLine.index);
            if (x < lineStart) {
                return -1;
            }
            int lineEnd = getLineEnd(txtLine.index);
            if (x > lineEnd) {
                return -1;
            }
            int word = paint.breakText(txtLine.txt, 0, txtLine.txt.length(), true, x - txtLine.left, null);
            return word + txtLine.charStart;
        }

        @Override
        public int getHysteresisOffset(int x, int y, int oldOffset, boolean isLeft) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return oldOffset;
            }
            int line = getLine(y);
            if (line == -1) {
                line = getLineForOffset(oldOffset);
            }
            TxtLine txtLine = page.lines.get(line);

            if (StringsKt.isBlank(txtLine.txt)) {
                if (isLeft) {
                    int idx = getNotWhitespace(line + 1, isLeft);
                    return idx == -1 ? oldOffset : idx;
                } else {
                    int idx = getNotWhitespace(line - 1, isLeft);
                    return idx == -1 ? oldOffset : (idx + 1);
                }
            }
            int lineStart = getLineStart(txtLine.index);
            if (x < lineStart) {
                if (isLeft) {
                    for (int i = 0; i < txtLine.txt.length(); i++) {
                        if (Character.isWhitespace(txtLine.txt.charAt(i))) {
                            continue;
                        }
                        return txtLine.charStart + i;
                    }
                    return oldOffset;
                } else {
                    int idx = getNotWhitespace(line - 1, isLeft);
                    return idx == -1 ? oldOffset : (idx + 1);
                }
            }
            int lineEnd = getLineEnd(txtLine.index);
            if (x > lineEnd) {
                if (isLeft) {
                    int idx = getNotWhitespace(line + 1, isLeft);
                    return idx == -1 ? oldOffset : idx;
                } else {
                    for (int i = txtLine.txt.length() - 1; i >= 0; i--) {
                        if (Character.isWhitespace(txtLine.txt.charAt(i))) {
                            continue;
                        }
                        return txtLine.charStart + i + 1;
                    }
                    return oldOffset;
                }
            }

            Paint paint;
            if (txtLine.isTitle) {
                paint = mTitlePaint;
            } else {
                paint = mTextPaint;
            }
            int word = paint.breakText(txtLine.txt, 0, txtLine.txt.length(), true, x - txtLine.left, null);
            float width = paint.measureText(txtLine.txt, word, word + 1);
            float wordWidth = paint.measureText(txtLine.txt, 0, word);
            if (x - wordWidth > width / 2) {
                return word + 1 + txtLine.charStart;
            } else {
                return word + txtLine.charStart;
            }
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
        public int getHorizontalRight(int offset) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            for (TxtLine line : page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length() > offset) {
                    int lineOffset = offset - line.charStart;
                    Paint paint;
                    if (line.isTitle) {
                        paint = mTitlePaint;
                    } else {
                        paint = mTextPaint;
                    }
                    return Math.round(paint.measureText(line.txt, 0, lineOffset + 1) + line.left);
                }
            }
            return -1;
        }

        @Override
        public int getHorizontalLeft(int offset) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            for (TxtLine line : page.lines) {
                if (line.charStart <= offset && line.charStart + line.txt.length() > offset) {
                    int lineOffset = offset - line.charStart;
                    if (lineOffset == 0) {
                        return Math.round(line.left);
                    }

                    Paint paint;
                    if (line.isTitle) {
                        paint = mTitlePaint;
                    } else {
                        paint = mTextPaint;
                    }
                    return Math.round(paint.measureText(line.txt, 0, lineOffset) + line.left);
                }
            }
            return -1;
        }

        @Override
        public int getLineTop(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            return Math.round(txtLine.top);
        }

        @Override
        public int getLineBottom(int line) {
            TxtPage page = PageLoader.this.mCurPage;
            if (page == null) {
                return -1;
            }
            TxtLine txtLine = page.lines.get(line);
            return Math.round(txtLine.bottom);
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
                return startLine.txt.substring(start - startLine.charStart, end - startLine.charStart);
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = startLine.index; i <= endLine.index; i++) {
                TxtLine line = page.lines.get(i);
                if (line == startLine) {
                    stringBuilder.append(line.txt.substring(start - line.charStart));
                } else if (line == endLine) {
                    stringBuilder.append(line.txt.substring(0, end - line.charStart));
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
