package sjj.novel.view.reader.page;

import java.util.Objects;

public class TxtLine {
    public CharSequence txt;
    public boolean isTitle;


    public int top;
    public int bottom;
    public int left;
    public int right;

    /**
     * 当前行号
     */
    public int index;
    public int charStart;

    public TxtLine(CharSequence txt,boolean isTitle) {
        this.txt = txt;
        this.isTitle = isTitle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TxtLine line = (TxtLine) o;
        return index == line.index && Float.compare(line.top, top) == 0 && Float.compare(line.bottom, bottom) == 0 && Float.compare(line.left, left) == 0 && Float.compare(line.right, right) == 0 && Objects.equals(txt, line.txt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txt, index, top, bottom, left, right);
    }

    @Override
    public String toString() {
        return "TxtLine{" +
                "txt='" + txt + '\'' +
                ", isTitle=" + isTitle +
                ", top=" + top +
                ", bottom=" + bottom +
                ", left=" + left +
                ", right=" + right +
                ", index=" + index +
                ", charStart=" + charStart +
                '}';
    }
}
