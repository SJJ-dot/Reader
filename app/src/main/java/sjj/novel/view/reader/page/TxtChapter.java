package sjj.novel.view.reader.page;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;

import java.util.Objects;

/**
 * Created by newbiechen on 17-7-1.
 */

public class TxtChapter {
    // 章节内容缓存：只保留必要的当前章 + 上一章 + 下一章，共 3 章
    private static final LruCache<String, CharSequence> contents = new LruCache<>(3);

    public static void evictAll() {
        contents.evictAll();
    }

    public static void evict(String link) {
        if (link == null) return;
        contents.remove(link);
    }

    public int chapterIndex;
    //章节所属的小说(网络)
    public String bookId;
    //章节的链接(网络)
    public String link;

    //章节名(共用)
    public String title;

    /**
     * 上一次加载章节内容是否失败。
     * 失败时 content 里存的是错误提示（非空），阅读页据此判断该章要不要重新请求加载
     */
    public boolean contentError;

    /**
     * 章节内容
     */
    public CharSequence getContent() {
        return contents.get(link);
    }

    public void setContent(CharSequence content) {
        if (!TextUtils.isEmpty(content)){
            contents.put(link, content);
            ChapterPageCache.remove(chapterIndex);
        }
    }



    @NonNull
    @Override
    public String toString() {
        return "TxtChapter{" +
                "bookId='" + bookId + '\'' +
                ", link='" + link + '\'' +
                ", title='" + title + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TxtChapter that = (TxtChapter) o;
        return chapterIndex == that.chapterIndex && Objects.equals(bookId, that.bookId) && Objects.equals(link, that.link) && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chapterIndex, bookId, link, title);
    }
}
