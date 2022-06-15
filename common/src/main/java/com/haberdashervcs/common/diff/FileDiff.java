package com.haberdashervcs.common.diff;

import java.util.List;

import com.google.common.collect.ImmutableList;


// TODO! Toss this, and related?
public final class FileDiff {

    public enum Type {
        DIFF,
        ADDED,
        DELETED
    }

    public static FileDiff of(String path, Type type, List<LineDiff> diffs) {
        return new FileDiff(path, type, diffs);
    }


    private final String path;
    private final Type type;
    private final List<LineDiff> diffs;

    private FileDiff(String path, Type type, List<LineDiff> diffs) {
        this.path = path;
        this.type = type;
        this.diffs = ImmutableList.copyOf(diffs);
    }

    public String getPath() {
        return path;
    }

    public Type getType() {
        return type;
    }

    public List<LineDiff> getDiffs() {
        return diffs;
    }
}
