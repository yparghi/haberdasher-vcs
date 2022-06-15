package com.haberdashervcs.client.crawl;

import com.google.common.base.Joiner;
import junit.framework.TestCase;


public class ParsedHdConfigTest extends TestCase {

    public void testExpressions() throws Exception {
        String yaml = Joiner.on('\n').join(
                "---",
                "ignoredPaths:",
                "- '*.txt'",
                "- '*/build/*'");

        ParsedHdConfig config = ParsedHdConfig.fromYaml(yaml);

        assertTrue(config.shouldIgnore("/bluh.txt"));
        assertTrue(config.shouldIgnore("/folder/bluh.txt"));

        assertTrue(config.shouldIgnore("/build/bluh.jar"));
        assertTrue(config.shouldIgnore("/frontend/build/bluh.jar"));
        assertTrue(config.shouldIgnore("/frontend/build/"));

        assertFalse(config.shouldIgnore("/something.bin"));
        assertFalse(config.shouldIgnore("/txt"));
        assertFalse(config.shouldIgnore("/folder/txt"));
        assertFalse(config.shouldIgnore("/*.txtyyy"));
    }

}