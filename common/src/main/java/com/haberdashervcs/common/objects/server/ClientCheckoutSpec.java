package com.haberdashervcs.common.objects.server;

import java.util.Collection;
import java.util.Set;

import com.google.common.collect.ImmutableSet;


public class ClientCheckoutSpec {

    public static ClientCheckoutSpec withFilesClientNeeds(Collection<String> fileIds) {
        return new ClientCheckoutSpec(fileIds);
    }


    private final Set<String> fileIdsClientNeeds;

    private ClientCheckoutSpec(Collection<String> fileIdsClientNeeds) {
        this.fileIdsClientNeeds = ImmutableSet.copyOf(fileIdsClientNeeds);
    }

    public Set<String> getFileIdsClientNeeds() {
        return fileIdsClientNeeds;
    }
}
