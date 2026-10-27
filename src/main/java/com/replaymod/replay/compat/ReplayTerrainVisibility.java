package com.replaymod.replay.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Replay-only fallback when WGMap has no confirmed result. DH database reads stay off the render thread. */
public final class ReplayTerrainVisibility {
    private static World world;
    private static Cache cache;
    private static Cache passCache;
    private static long passGeneration;
    private static long nextBind,epoch;
    private static boolean warned;
    private static int columnsRemaining=65536;
    private ReplayTerrainVisibility() { }
    public static void close() {
        if(cache!=null) cache.close();
        cache=null; world=null; nextBind=0; epoch++;
    }
    public static long generation() {
        columnsRemaining=65536;
        synchronizeWorld(); return epoch+(cache==null?0:cache.generation);
    }
    public static void beginFrame() {
        synchronizeWorld(); passCache=cache; passGeneration=cache==null?0:cache.generation;
    }
    public static boolean afterPass() {
        return cache==passCache && (cache==null || cache.ready(passGeneration));
    }
    private static void synchronizeWorld() {
        World current=Minecraft.getMinecraft().theWorld;
        if(current!=world) { close(); world=current; }
        if(world==null || world.provider==null || cache!=null || !DistantHorizonsReplay.hasSource(world.provider.dimensionId) || System.currentTimeMillis()<nextBind) return;
        nextBind=System.currentTimeMillis()+500;
        try {
            ClassLoader loader=ReplayTerrainVisibility.class.getClassLoader();
            Class<?> delayed=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed",false,loader);
            Object proxy=delayed.getField("worldProxy").get(null),repo=delayed.getField("terrainRepo").get(null);
            if(proxy==null || repo==null) return;
            // Only the selected replay terrain source is eligible, never another loaded world.
            for(Object level:(Iterable<?>)proxy.getClass().getMethod("getAllLoadedLevelWrappers").invoke(proxy))
                if(level.getClass().getMethod("getWrappedMcObject").invoke(level)==world) {
                    DistantHorizonsReplay.protectTerrain();
                    cache=new Cache(new DhColumns(repo,level,loader));
                    com.replaymod.replay.ReplayModReplay.LOGGER.info("Replay terrain occlusion fallback bound to the selected DH cache");
                    break;
                }
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if(!warned) com.replaymod.replay.ReplayModReplay.LOGGER.warn("DH terrain occlusion API unavailable; retaining loaded-terrain and WGMap checks",unavailable);
            warned=true;
        }
    }
    public static TerrainVisibility.Result trace(int dimension,double x0,double y0,double z0,double x1,double y1,double z1) {
        synchronizeWorld();
        if(world==null || world.provider==null || world.provider.dimensionId!=dimension) return TerrainVisibility.Result.UNKNOWN;
        final World current=world; final Cache source=cache;
        return TerrainVisibility.trace(new TerrainVisibility.Columns() {
            int lastX=Integer.MIN_VALUE,lastZ; Chunk last;
            public boolean exhausted() { return columnsRemaining<=0; }
            public TerrainVisibility.Result test(int x,int z,double low,double high) {
                columnsRemaining--;
                if(low<0) return TerrainVisibility.Result.UNKNOWN;
                if(current.getChunkProvider().chunkExists(x>>4,z>>4)) {
                    for(int y=Math.max(0,(int)Math.floor(low));y<=Math.min(current.getHeight()-1,(int)Math.floor(high));y++)
                        if(current.getBlock(x,y,z).isOpaqueCube()) return TerrainVisibility.Result.BLOCKED;
                    return TerrainVisibility.Result.CLEAR;
                }
                if(source==null) return TerrainVisibility.Result.UNKNOWN;
                if(lastX!=(x>>4) || lastZ!=(z>>4)) { lastX=x>>4; lastZ=z>>4; last=source.get(lastX,lastZ); }
                return last==null?TerrainVisibility.Result.UNKNOWN:TerrainVisibility.intervals(last.columns[(x&15)*16+(z&15)],low,high);
            }
        },x0,y0,z0,x1,y1,z1);
    }
    interface Reader extends AutoCloseable {
        Chunk read(int x,int z) throws Exception;
        void close() throws Exception;
    }
    static final class Chunk {
        final int[][] columns;
        final long bytes;
        final boolean complete;
        Chunk(int[][] columns) {
            this.columns=columns; long bytes=2048; boolean complete=true;
            for(int[] column:columns) { if(column==null) complete=false; else bytes+=16L+column.length*4L; }
            this.bytes=bytes; this.complete=complete;
        }
    }
    static final class Cache {
        static final int MAX_CHUNKS=512,MAX_PENDING=256;
        static final long MAX_BYTES=32L*1024*1024;
        private final Reader reader;
        private final ArrayBlockingQueue<Long> requests=new ArrayBlockingQueue<>(MAX_PENDING);
        private final Set<Long> pending=new HashSet<>();
        private final LinkedHashMap<Long,Entry> chunks=new LinkedHashMap<>(16,.75f,true);
        private final Thread worker;
        private volatile boolean closed;
        volatile long generation;
        private long bytes;
        Cache(Reader reader) {
            this.reader=reader; worker=new Thread(this::run,"Replay terrain occlusion"); worker.setDaemon(true); worker.start();
        }
        synchronized Chunk get(int x,int z) {
            if(closed) return null;
            long key=((long)x<<32)^(z&0xffffffffL); Entry entry=chunks.get(key);
            if(entry!=null && (entry.chunk!=null && entry.chunk.complete || System.nanoTime()<entry.retry)) return entry.chunk;
            if(pending.size()<MAX_PENDING && !pending.contains(key) && requests.offer(key)) pending.add(key);
            return entry==null?null:entry.chunk;
        }
        private void run() {
            try {
                while(!closed) {
                    long key=requests.take(); Chunk chunk=null;
                    try { chunk=reader.read((int)(key>>32),(int)key); }
                    catch(InterruptedException stopped) { Thread.currentThread().interrupt(); break; }
                    catch(Exception | LinkageError unavailable) { /* Missing/loading data stays unknown and retries later. */ }
                    synchronized(this) {
                        pending.remove(key); if(closed) break;
                        Entry old=chunks.put(key,new Entry(chunk)); if(old!=null && old.chunk!=null) bytes-=old.chunk.bytes;
                        if(chunk!=null) bytes+=chunk.bytes;
                        Iterator<Entry> entries=chunks.values().iterator();
                        while(chunks.size()>MAX_CHUNKS || bytes>MAX_BYTES) {
                            Entry removed=entries.next(); if(removed.chunk!=null) bytes-=removed.chunk.bytes; entries.remove();
                        }
                        generation++;
                    }
                }
            } catch(InterruptedException stopped) { Thread.currentThread().interrupt(); }
            finally { try { reader.close(); } catch(Exception ignored) { } }
        }
        synchronized boolean ready(long renderedGeneration) { return pending.isEmpty() && generation==renderedGeneration; }
        synchronized void close() { closed=true; worker.interrupt(); requests.clear(); pending.clear(); chunks.clear(); bytes=0; generation++; }
        private static final class Entry {
            final Chunk chunk; final long retry=System.nanoTime()+2_000_000_000L;
            Entry(Chunk chunk) { this.chunk=chunk; }
        }
    }
    /** Uses only public DH API methods and copies returned columns into bounded primitive snapshots. */
    static final class DhColumns implements Reader {
        private final Object repo,level,dataCache;
        private final Method read,clear,close,isAir,isSolid,isLiquid,opacity;
        private final Field success,payload,bottom,top,state,detail;
        private int reads;
        DhColumns(Object repo,Object level,ClassLoader loader) throws ReflectiveOperationException {
            this.repo=repo; this.level=level;
            Class<?> api=Class.forName("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo",false,loader);
            Class<?> cacheType=Class.forName("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache",false,loader);
            Class<?> levelType=Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper",false,loader);
            Class<?> point=Class.forName("com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint",false,loader);
            Class<?> block=Class.forName("com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper",false,loader);
            Class<?> result=Class.forName("com.seibel.distanthorizons.api.objects.DhApiResult",false,loader);
            read=api.getMethod("getAllTerrainDataAtChunkPos",levelType,int.class,int.class,cacheType);
            clear=cacheType.getMethod("clear"); close=cacheType.getMethod("close");
            success=result.getField("success"); payload=result.getField("payload");
            bottom=point.getField("bottomYBlockPos"); top=point.getField("topYBlockPos"); state=point.getField("blockStateWrapper"); detail=point.getField("detailLevel");
            isAir=block.getMethod("isAir"); isSolid=block.getMethod("isSolid"); isLiquid=block.getMethod("isLiquid"); opacity=block.getMethod("getOpacity");
            dataCache=api.getMethod("createSoftCache").invoke(repo);
        }
        public Chunk read(int x,int z) throws Exception {
            if(++reads%16==0) clear.invoke(dataCache);
            Object result=read.invoke(repo,level,x,z,dataCache);
            if(!success.getBoolean(result)) return null;
            Object[][][] data=(Object[][][])payload.get(result);
            if(data==null || data.length!=16) return null;
            int[][] columns=new int[256][]; int total=0;
            for(int a=0;a<16;a++) {
                if(data[a]==null || data[a].length!=16) return null;
                for(int b=0;b<16;b++) {
                    Object[] column=data[a][b]; if(column==null || column.length==0) continue;
                    if(column.length>512 || (total+=column.length)>65536) return null;
                    int[] intervals=new int[column.length*2]; int n=0; boolean known=true;
                    for(Object point:column) {
                        if(point==null || detail.getByte(point)!=0) { known=false; break; }
                        Object block=state.get(point);
                        if(block==null) { known=false; break; }
                        if(Boolean.TRUE.equals(isAir.invoke(block))) continue;
                        if(Boolean.TRUE.equals(isSolid.invoke(block)) && !Boolean.TRUE.equals(isLiquid.invoke(block)) && ((Integer)opacity.invoke(block))>=16) {
                            int low=bottom.getInt(point),high=top.getInt(point);
                            if(low>=high) { known=false; break; }
                            intervals[n++]=low; intervals[n++]=high;
                        }
                    }
                    if(known) columns[a*16+b]=Arrays.copyOf(intervals,n);
                }
            }
            return new Chunk(columns);
        }
        public void close() throws Exception { close.invoke(dataCache); }
    }
}
