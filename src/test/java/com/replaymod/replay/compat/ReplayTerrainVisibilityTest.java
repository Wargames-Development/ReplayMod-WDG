package com.replaymod.replay.compat;

import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class ReplayTerrainVisibilityTest {
    @Test public void installedDhPublicApiCopiesOnlyOpaqueSolidColumnsAndKeepsMissingColumnsUnknown() throws Exception {
        String jar=System.getenv("REPLAYMOD_TEST_DH"); org.junit.Assume.assumeNotNull(jar);
        try(java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{new java.io.File(jar).toURI().toURL()},getClass().getClassLoader())) {
            Class<?> repoType=loader.loadClass("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo"),
                cacheType=loader.loadClass("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache"),
                levelType=loader.loadClass("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper"),
                blockType=loader.loadClass("com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper"),
                pointType=loader.loadClass("com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint"),
                biomeType=loader.loadClass("com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper"),
                resultType=loader.loadClass("com.seibel.distanthorizons.api.objects.DhApiResult");
            AtomicInteger opacity=new AtomicInteger(16); AtomicBoolean air=new AtomicBoolean(),liquid=new AtomicBoolean(),closed=new AtomicBoolean();
            Object block=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{blockType},(p,m,a) -> {
                switch(m.getName()) { case "isAir":return air.get(); case "isSolid":return true; case "isLiquid":return liquid.get(); case "getOpacity":return opacity.get(); default:return null; }
            });
            Object point=pointType.getMethod("create",byte.class,int.class,int.class,int.class,int.class,blockType,biomeType)
                .invoke(null,(byte)0,0,15,64,120,block,null);
            Object[][][] data=(Object[][][])java.lang.reflect.Array.newInstance(pointType,16,16,1);
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) data[x][z][0]=point;
            Object apiCache=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{cacheType},(p,m,a) -> { if(m.getName().equals("close")) closed.set(true); return null; });
            Object repo=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{repoType},(p,m,a) -> m.getName().equals("createSoftCache")?apiCache:
                resultType.getMethod("createSuccess",Object.class).invoke(null,(Object)data));
            Object level=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{levelType},(p,m,a) -> null);
            ReplayTerrainVisibility.DhColumns reader=new ReplayTerrainVisibility.DhColumns(repo,level,loader);
            try {
                assertArrayEquals(new int[]{64,120},reader.read(0,0).columns[0]);
                opacity.set(1); assertEquals(0,reader.read(0,0).columns[0].length);
                opacity.set(16); liquid.set(true); assertEquals(0,reader.read(0,0).columns[0].length);
                liquid.set(false); air.set(true); assertEquals(0,reader.read(0,0).columns[0].length);
                data[0][0]=(Object[])java.lang.reflect.Array.newInstance(pointType,0);
                ReplayTerrainVisibility.Chunk missing=reader.read(0,0); assertNull(missing.columns[0]); assertFalse(missing.complete);
            } finally { reader.close(); }
            assertTrue(closed.get());
        }
    }
    @Test public void databaseReadsNeverBlockTheCallerAndClosingCancelsAnOldWorld() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),closed=new CountDownLatch(1);
        Thread caller=Thread.currentThread();
        ReplayTerrainVisibility.Cache cache=new ReplayTerrainVisibility.Cache(new ReplayTerrainVisibility.Reader() {
            public ReplayTerrainVisibility.Chunk read(int x,int z) throws Exception {
                assertNotSame(caller,Thread.currentThread()); entered.countDown(); release.await(); return chunk(64,120);
            }
            public void close() { closed.countDown(); }
        });
        try {
            assertNull(cache.get(5,8)); assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertFalse(cache.ready(cache.generation));
            assertNull(cache.get(5,8)); // The worker remains deliberately blocked.
            cache.close(); release.countDown(); assertTrue(closed.await(2,TimeUnit.SECONDS));
            assertNull(cache.get(5,8));
        } finally { release.countDown(); cache.close(); }
    }
    @Test public void residentColumnsAreReusedAndPublishingChangesTheTerrainGeneration() throws Exception {
        AtomicInteger reads=new AtomicInteger(); CountDownLatch closed=new CountDownLatch(1);
        ReplayTerrainVisibility.Cache cache=new ReplayTerrainVisibility.Cache(new ReplayTerrainVisibility.Reader() {
            public ReplayTerrainVisibility.Chunk read(int x,int z) { reads.incrementAndGet(); return chunk(64,120); }
            public void close() { closed.countDown(); }
        });
        try {
            assertNull(cache.get(-3,7)); ReplayTerrainVisibility.Chunk ready=await(cache,-3,7);
            assertEquals(1,reads.get()); assertTrue(cache.generation>0);
            assertFalse("An export must redraw after a terrain result arrives",cache.ready(0));
            assertTrue(cache.ready(cache.generation));
            assertEquals(TerrainVisibility.Result.BLOCKED,TerrainVisibility.intervals(ready.columns[0],80,80));
            for(int i=0;i<100;i++) assertSame(ready,cache.get(-3,7));
            assertEquals(1,reads.get());
        } finally { cache.close(); assertTrue(closed.await(2,TimeUnit.SECONDS)); }
    }
    @Test public void failedReadsStayUnknownAndRetryInsteadOfBecomingClearTerrain() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        ReplayTerrainVisibility.Cache cache=new ReplayTerrainVisibility.Cache(new ReplayTerrainVisibility.Reader() {
            public ReplayTerrainVisibility.Chunk read(int x,int z) { reads.incrementAndGet(); throw new IllegalStateException("Data still loading"); }
            public void close() { }
        });
        try {
            cache.get(1,2); long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(cache.generation==0 && System.nanoTime()<deadline) Thread.yield();
            assertTrue(cache.generation>0); assertNull(cache.get(1,2)); assertEquals(1,reads.get());
        } finally { cache.close(); }
    }
    private static ReplayTerrainVisibility.Chunk await(ReplayTerrainVisibility.Cache cache,int x,int z) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2); ReplayTerrainVisibility.Chunk result;
        while((result=cache.get(x,z))==null && System.nanoTime()<deadline) Thread.yield();
        assertNotNull(result); return result;
    }
    private static ReplayTerrainVisibility.Chunk chunk(int bottom,int top) {
        int[][] columns=new int[256][]; java.util.Arrays.fill(columns,new int[]{bottom,top}); return new ReplayTerrainVisibility.Chunk(columns);
    }
}
