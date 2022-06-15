package com.haberdashervcs.client.crawl;

import java.io.IOException;
import java.util.List;

import com.haberdashervcs.common.objects.HdFolderPath;


public interface LocalChangeHandler {

    void handleComparisons(
            HdFolderPath parentFolderPath,
            List<LocalComparisonToCommit> comparisons,
            LocalChangeCrawler.CrawlEntry crawlEntry)
            throws IOException;
}
