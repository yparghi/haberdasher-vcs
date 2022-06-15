package com.haberdashervcs.common.diff;

import java.util.Objects;

import com.google.common.base.MoreObjects;


public final class LineDiff {

   public enum Type {
        PLUS,
        MINUS,
        SAME
    }

    private final Type type;
    private final int lineNumberOld;
    private final int lineNumberNew;
    private final String text;

    public LineDiff(Type type, int lineNumberOld, int lineNumberNew, String text) {
        this.type = type;
        this.lineNumberOld = lineNumberOld;
        this.lineNumberNew = lineNumberNew;
        this.text = text;
    }

    public Type getType() {
        return type;
    }

    // -1 if there is no old line
    public int getLineNumberOld() {
        return lineNumberOld;
    }

    // -1 if there is no new line
    public int getLineNumberNew() {
        return lineNumberNew;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineDiff lineDiff = (LineDiff) o;
        return (lineNumberOld == lineDiff.lineNumberOld
                && lineNumberNew == lineDiff.lineNumberNew
                && type == lineDiff.type
                && text.equals(lineDiff.text));
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lineNumberOld, lineNumberNew, text);
    }

    public String getDisplayLine() {
        String prefix;
        switch (type) {
            case PLUS:
                prefix = "+ ";
                break;
            case MINUS:
                prefix = "- ";
                break;
            case SAME:
            default:
                prefix = "  ";
                break;
        }

        return prefix + text.stripTrailing();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("lineNumberOld", lineNumberOld)
                .add("lineNumberNew", lineNumberNew)
                .add("text", text)
                .toString();
    }
}
