package com.replaymod.replay;

import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;
import sun.misc.Unsafe;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class EditorSeekPacketSliceTest {
    @Test public void sliceHasAHardPacketCapAndDrainsMinecraftHandlersInOrder() throws Exception {
        Sender sender=sender(100);
        assertFalse(sender.sendPacketsTillSlice(1000,Long.MAX_VALUE,sender::drain));
        assertEquals(64,sender.applied.size()); assertEquals(64,sender.drained); assertEquals(630,sender.lastTimeStamp);
        assertTrue(sender.sendPacketsTillSlice(1000,Long.MAX_VALUE,sender::drain));
        assertEquals(100,sender.applied.size()); assertEquals(100,sender.drained); assertEquals(1000,sender.lastTimeStamp);
    }
    @Test public void exhaustedTimeBudgetYieldsAfterOnePacketAndResumesWithoutDuplication() throws Exception {
        Sender sender=sender(3);
        assertFalse(sender.sendPacketsTillSlice(20,0,sender::drain)); assertEquals(Arrays.asList(0),sender.applied);
        assertFalse(sender.sendPacketsTillSlice(20,0,sender::drain)); assertEquals(Arrays.asList(0,1),sender.applied);
        assertTrue(sender.sendPacketsTillSlice(20,Long.MAX_VALUE,sender::drain)); assertEquals(Arrays.asList(0,1,2),sender.applied);
    }
    @Test public void inclusiveTargetKeepsTheNextPacketForTheNextSeek() throws Exception {
        Sender sender=sender(4);
        assertTrue(sender.sendPacketsTillSlice(10,Long.MAX_VALUE,sender::drain)); assertEquals(Arrays.asList(0,1),sender.applied);
        assertTrue(sender.sendPacketsTillSlice(20,Long.MAX_VALUE,sender::drain)); assertEquals(Arrays.asList(0,1,2),sender.applied);
    }
    @Test public void backwardsTargetRestartsOnceThenResumesTheSameBoundedRead() throws Exception {
        Sender sender=sender(5);
        assertTrue(sender.sendPacketsTillSlice(30,Long.MAX_VALUE,sender::drain));
        assertTrue(sender.sendPacketsTillSlice(10,Long.MAX_VALUE,sender::drain));
        assertEquals(1,sender.rewinds); assertEquals(Arrays.asList(0,1,2,3,0,1),sender.applied);
        assertTrue(sender.sendPacketsTillSlice(20,Long.MAX_VALUE,sender::drain)); assertEquals(1,sender.rewinds);
    }
    @Test public void scrubbingToZeroFinishesInitialLoginWithoutRepeatedlyRewinding() throws Exception {
        Sender sender=sender(5); sender.hasWorldLoaded=false; sender.loadAfter=3;
        assertFalse(sender.sendPacketsTillSlice(0,0,sender::drain));
        assertFalse(sender.sendPacketsTillSlice(0,0,sender::drain));
        assertTrue(sender.sendPacketsTillSlice(0,Long.MAX_VALUE,sender::drain));
        assertEquals(Arrays.asList(0,1,2),sender.applied); assertEquals(0,sender.rewinds); assertEquals(0,sender.lastTimeStamp);
    }
    private static Sender sender(int count) throws Exception {
        Field field=Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Sender sender=(Sender)((Unsafe)field.get(null)).allocateInstance(Sender.class);
        sender.count=count; sender.applied=new ArrayList<>(); sender.hasWorldLoaded=true;
        sender.ctx=(ChannelHandlerContext)Proxy.newProxyInstance(ChannelHandlerContext.class.getClassLoader(),new Class<?>[]{ChannelHandlerContext.class},(proxy,method,args) -> null);
        return sender;
    }
    private static class Sender extends FullReplaySender {
        int count,index,rewinds,drained,loadAfter; List<Integer> applied;
        private Sender() throws IOException { super(null,null,false); }
        @Override protected PacketData readEditorSeekPacket() throws IOException {
            if(index==count) throw new EOFException();
            int next=index++; return new PacketData(next*10,new byte[]{(byte)next});
        }
        @Override protected void rewindEditorSeek() { index=0; rewinds++; lastTimeStamp=0; nextPacket=null; }
        @Override public void channelRead(ChannelHandlerContext ctx,Object packet) { applied.add(((byte[])packet)[0]&255); if(loadAfter>0 && applied.size()>=loadAfter) hasWorldLoaded=true; }
        void drain() { drained++; assertEquals(applied.size(),drained); }
    }
}
