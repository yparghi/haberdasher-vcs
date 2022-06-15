package com.haberdashervcs.common.objects;


public final class RepoEntry {

    public static RepoEntry of(String org, String repo) {
        return new RepoEntry(org, repo);
    }


    private final String org;
    private final String repo;

    private RepoEntry(String org, String repo) {
        this.org = org;
        this.repo = repo;
    }

    public String getOrg() {
        return org;
    }

    public String getRepoName() {
        return repo;
    }
}
