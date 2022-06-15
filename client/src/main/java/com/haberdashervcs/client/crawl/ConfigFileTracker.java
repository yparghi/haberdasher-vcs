package com.haberdashervcs.client.crawl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.objects.HdFolderPath;


// IDEA: One day we could go to the server and ask for the higher-up config file for each checked-out path, if it's
// not here locally.
final class ConfigFileTracker {

    private final Map<HdFolderPath, ParsedHdConfig> configsByContainingFolder;

    ConfigFileTracker() {
        this.configsByContainingFolder = new HashMap<>();
    }


    void addConfig(HdFolderPath path, Path configFilePath) throws IOException {
        Preconditions.checkState(!configsByContainingFolder.containsKey(path));
        String yamlContents = new String(Files.readAllBytes(configFilePath), StandardCharsets.UTF_8);
        configsByContainingFolder.put(path, ParsedHdConfig.fromYaml(yamlContents));
    }


    // The nearest matching config *overrides* others, rather than using any kind of inheritance.
    boolean shouldIgnorePath(String pathStartingWithSlash) {
        Preconditions.checkArgument(pathStartingWithSlash.startsWith("/"));

        int nearestMatchingPathLength = 0;
        ParsedHdConfig nearestMatchingConfig = null;
        for (Map.Entry<HdFolderPath, ParsedHdConfig> entry : configsByContainingFolder.entrySet()) {
            String folderPathStr = entry.getKey().forFolderListing();
            if (pathStartingWithSlash.startsWith(folderPathStr)) {
                if (folderPathStr.length() > nearestMatchingPathLength) {
                    nearestMatchingPathLength = folderPathStr.length();
                    nearestMatchingConfig = entry.getValue();
                }
            }
        }

        if (nearestMatchingConfig == null) {
            return false;
        } else {
            return nearestMatchingConfig.shouldIgnore(pathStartingWithSlash);
        }
    }

}
