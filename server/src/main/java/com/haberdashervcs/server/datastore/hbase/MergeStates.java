package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.MergeLock;


final class MergeStates {

    private static final HdLogger LOG = HdLoggers.create(MergeStates.class);


    static MergeStates fromPastSeconds(
            long nowTs, long secondsAgo, HBaseRawHelper helper, HBaseRowKeyer rowKeyer)
            throws IOException {
        long ago = nowTs - (TimeUnit.SECONDS.toMillis(secondsAgo));
        byte[] rowNow = rowKeyer.prefixForMergeLocksAtTimestamp(nowTs);
        byte[] rowAgo = rowKeyer.prefixForMergeLocksAtTimestamp(ago);
        List<MergeLock> currentMerges = helper.getMerges(rowAgo, rowNow);
        return new MergeStates(currentMerges, helper, rowKeyer);
    }


    private final ImmutableList<MergeLock> currentMerges;
    private final HBaseRawHelper helper;
    private final HBaseRowKeyer rowKeyer;

    private final Map<String, MergeLock> cachedFromDb;

    private MergeStates(List<MergeLock> currentMerges, HBaseRawHelper helper, HBaseRowKeyer rowKeyer) {
        this.currentMerges = ImmutableList.copyOf(currentMerges);
        this.helper = helper;
        this.rowKeyer = rowKeyer;

        this.cachedFromDb = new HashMap<>();
    }


    MergeLock forMergeLockId(String mergeLockId) throws IOException {
        LOG.debug("TEMP: comparing merge lock id's in list: %s", currentMerges);
        for (MergeLock merge : currentMerges) {
            if (merge.getId().equals(mergeLockId)) {
                return merge;
            }
        }

        if (cachedFromDb.containsKey(mergeLockId)) {
            return cachedFromDb.get(mergeLockId);
        }

        // If the merge isn't recent, we have to look it up by id.
        Optional<MergeLock> byId = helper.getMergeById(rowKeyer, mergeLockId);
        if (byId.isPresent()) {
            cachedFromDb.put(mergeLockId, byId.get());
            return byId.get();

        } else {
            throw new IllegalStateException("Merge not found: " + mergeLockId);
        }
    }
}
