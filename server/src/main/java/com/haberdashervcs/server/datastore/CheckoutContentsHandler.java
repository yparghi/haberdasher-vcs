package com.haberdashervcs.server.datastore;

import java.io.IOException;

import com.haberdashervcs.common.objects.FolderListing;


public interface CheckoutContentsHandler {

    void sendFolder(FolderListing folder) throws IOException;

    void sendFile(String fileId) throws IOException;
}
