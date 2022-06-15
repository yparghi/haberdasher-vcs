package com.haberdashervcs.common.io.rab;

import java.util.Optional;

import junit.framework.TestCase;
import org.junit.Test;


public class BufferIndexerTest extends TestCase {

    @Test
    public void testInitialZero() throws Exception {
        BufferIndexer indexer = new BufferIndexer(200, 1000);
        Optional<BufferIndexer.BufferRange> initial = indexer.shouldRebufferFor(0);
        assertEquals(initial, Optional.of(new BufferIndexer.BufferRange(0, 200)));
    }


    @Test
    public void testInitialNonzero() throws Exception {
        BufferIndexer indexer = new BufferIndexer(200, 1000);
        Optional<BufferIndexer.BufferRange> initial = indexer.shouldRebufferFor(500);
        assertEquals(initial, Optional.of(new BufferIndexer.BufferRange(400, 600)));
    }


    @Test
    public void testInitialHighEnd() throws Exception {
        BufferIndexer indexer = new BufferIndexer(200, 1000);
        Optional<BufferIndexer.BufferRange> initial = indexer.shouldRebufferFor(950);
        assertEquals(initial, Optional.of(new BufferIndexer.BufferRange(800, 1000)));
    }


    @Test
    public void testNoRebufferNeeded() throws Exception {
        BufferIndexer indexer = new BufferIndexer(200, 1000);
        Optional<BufferIndexer.BufferRange> initial = indexer.shouldRebufferFor(950);
        assertEquals(initial, Optional.of(new BufferIndexer.BufferRange(800, 1000)));

        Optional<BufferIndexer.BufferRange> rebuffer = indexer.shouldRebufferFor(950);
        assertTrue(rebuffer.isEmpty());
    }


    @Test
    public void testRebufferNeeded_lower() throws Exception {
        BufferIndexer indexer = new BufferIndexer(200, 1000);
        Optional<BufferIndexer.BufferRange> initial = indexer.shouldRebufferFor(500);
        assertEquals(initial, Optional.of(new BufferIndexer.BufferRange(400, 600)));

        Optional<BufferIndexer.BufferRange> rebuffer = indexer.shouldRebufferFor(300);
        assertEquals(rebuffer, Optional.of(new BufferIndexer.BufferRange(200, 400)));
    }


    @Test
    public void testRebufferNeeded_higher() throws Exception {
        BufferIndexer indexer = new BufferIndexer(200, 1000);
        Optional<BufferIndexer.BufferRange> initial = indexer.shouldRebufferFor(500);
        assertEquals(initial, Optional.of(new BufferIndexer.BufferRange(400, 600)));

        Optional<BufferIndexer.BufferRange> rebuffer = indexer.shouldRebufferFor(950);
        assertEquals(rebuffer, Optional.of(new BufferIndexer.BufferRange(800, 1000)));
    }

}