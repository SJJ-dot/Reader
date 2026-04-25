package sjj.novel.view.reader.animation;

import android.graphics.Bitmap;

public class BitmapWrapper {
    public final Bitmap bitmap;
    public int chapterPos = -1;
    public int pagePos = -1;

    public BitmapWrapper(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public BitmapWrapper copy(Bitmap.Config config, boolean isMutable) {
        Bitmap copyBitmap = bitmap.copy(config, isMutable);
        BitmapWrapper copyWrapper = new BitmapWrapper(copyBitmap);
        copyWrapper.chapterPos = chapterPos;
        copyWrapper.pagePos = pagePos;
        return copyWrapper;
    }
}
