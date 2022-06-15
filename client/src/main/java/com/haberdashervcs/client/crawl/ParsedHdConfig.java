package com.haberdashervcs.client.crawl;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;


public final class ParsedHdConfig {

    public static ParsedHdConfig fromYaml(String yamlContents) {
        Yaml yaml = new Yaml(new Constructor(ParsedYaml.class));
        ParsedYaml parsed = yaml.load(yamlContents);
        return new ParsedHdConfig(parsed);
    }


    // This might seem redundant, but I want to separate the Yaml parsing result from the fields/construction of this
    // class.
    private static final class ParsedYaml {
        public List<String> ignoredPaths;
    }


    private final List<Pattern> matchers;

    private ParsedHdConfig(ParsedYaml parsed) {
        ImmutableList.Builder<Pattern> matchersB = ImmutableList.builder();
        FileSystem fs = FileSystems.getDefault();
        for (String pathPattern : parsed.ignoredPaths) {
            matchersB.add(toPattern(pathPattern));
        }
        this.matchers = matchersB.build();
    }


    private Pattern toPattern(String patternStr) {
        StringBuilder out = new StringBuilder();
        out.append('^');

        for (char c : patternStr.toCharArray()) {
            switch (c) {
                case '*':
                    out.append(".*");
                    break;
                case '.':
                    out.append("\\.");
                    break;
                default:
                    out.append(c);
            }
        }

        out.append('$');
        return Pattern.compile(out.toString());
    }


    public boolean shouldIgnore(String pathStartingWithSlash) {
        Preconditions.checkArgument(pathStartingWithSlash.startsWith("/"));
        for (Pattern pattern : matchers) {
            Matcher matcher = pattern.matcher(pathStartingWithSlash);
            if (matcher.matches()) {
                return true;
            }
        }

        return false;
    }

}
