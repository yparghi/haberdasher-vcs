package com.haberdashervcs.common.rules;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.CharMatcher;


public final class HdNameRules {

    private HdNameRules() {
        throw new UnsupportedOperationException();
    }


    private static final int MAX_NAME_LENGTH = 20;

    private static final CharMatcher REPO_NAME_MATCHER = CharMatcher
            .inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("-_"));


    /**
     * Returns a list of validation errors. The list is empty if the repo name is valid.
     */
    public static List<String> validateRepoName(String repoName) {
        ArrayList<String> errorMessages = new ArrayList<>();
        if (repoName.length() < 4) {
            errorMessages.add("Repo names must be at least 4 characters long.");
        } else if (!REPO_NAME_MATCHER.matchesAllOf(repoName)) {
            errorMessages.add("Repo names must consist of a-z, A-Z, 0-9, hyphens or underscores.");
        } else if (repoName.length() > MAX_NAME_LENGTH) {
            errorMessages.add("Repo name is too long. (Max " + MAX_NAME_LENGTH + " characters)");
        }
        return errorMessages;
    }

}
