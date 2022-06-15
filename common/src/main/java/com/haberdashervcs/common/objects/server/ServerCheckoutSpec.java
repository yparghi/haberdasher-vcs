package com.haberdashervcs.common.objects.server;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;


public final class ServerCheckoutSpec {

    public static ServerCheckoutSpec withServerFileIds(Collection<String> fileIds) {
        return new ServerCheckoutSpec(fileIds);
    }


    private final List<String> allFileIdsFromServer;

    private ServerCheckoutSpec(Collection<String> allFileIdsFromServer) {
        this.allFileIdsFromServer = ImmutableList.copyOf(allFileIdsFromServer);
    }

    public List<String> getAllFileIdsFromServer() {
        return allFileIdsFromServer;
    }
}
