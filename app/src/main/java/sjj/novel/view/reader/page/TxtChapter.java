package sjj.novel.view.reader.page;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;
import android.text.TextUtils;

import java.util.Objects;

/**
 * Created by newbiechen on 17-7-1.
 */

public class TxtChapter {
    //保存10章的章节内容 这里的10个字符串会导致内存泄漏。但不重要
    private static final LruCache<String, CharSequence> contents = new LruCache<>(10);

    public static void evictAll() {
        contents.evictAll();
    }
    public int chapterIndex;
    //章节所属的小说(网络)
    public String bookId;
    //章节的链接(网络)
    public String link;

    //章节名(共用)
    public String title;

    /**
     * 章节内容
     */
    public CharSequence getContent() {
        return contents.get(link);
    }

    public void setContent(CharSequence content) {
        if (!TextUtils.isEmpty(content))
            contents.put(link, content);
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
