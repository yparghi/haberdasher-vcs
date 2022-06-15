package com.haberdashervcs.server.browser;

import java.io.IOException;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;


public interface FileBrowser {

    RandomAccessBytes getWholeContents() throws IOException;
}
