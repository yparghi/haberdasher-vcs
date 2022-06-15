package com.haberdashervcs.common.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;


public final class CheckoutPathSet {

    public static final CheckoutPathSet fromStrings(List<String> paths) {
        return new CheckoutPathSet(paths);
    }

    public static final CheckoutPathSet withAddition(CheckoutPathSet base, String pathToAdd) {
        return new CheckoutPathSet(base.withAddition(pathToAdd));
    }

    private static final Joiner TO_STRING_JOINER = Joiner.on(':');


    private final List<String> canonicalPaths;

    private CheckoutPathSet(List<String> somePaths) {
        validate(somePaths);
        this.canonicalPaths = ImmutableList.copyOf(somePaths);
    }

    private void validate(List<String> somePaths) {
        for (String path : somePaths) {
            if (!path.startsWith("/") || !path.endsWith("/")) {
                throw new IllegalArgumentException("Invalid path: " + path);
            }

            for (String otherPath : somePaths) {
                if (path.equals(otherPath)) {
                    continue;
                } else if (path.startsWith(otherPath) || otherPath.startsWith(path)) {
                    throw new IllegalArgumentException(String.format(
                            "Invalid paths list: paths %s and %s overlap.", path, otherPath));
                }
            }
        }
    }

    private List<String> withAddition(String newPath) {
        ArrayList<String> out = new ArrayList<>();
        for (String path : canonicalPaths) {
            if (newPath.startsWith(path)) {
                throw new IllegalArgumentException(String.format(
                        "Can't add path %s: parent path %s is already checked out.", newPath, path));
            } else if (path.startsWith(newPath)) {
                continue;
            } else {
                out.add(path);
            }
        }
        out.add(newPath);
        Collections.sort(out);
        return out;
    }

    public List<String> toList() {
        return canonicalPaths;
    }

    public boolean isEmpty() {
        return (canonicalPaths.isEmpty());
    }

    @Override
    public String toString() {
        return TO_STRING_JOINER.join(canonicalPaths);
    }

}
