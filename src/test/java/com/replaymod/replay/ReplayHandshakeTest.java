package com.replaymod.replay;

import cpw.mods.fml.client.FMLClientHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import org.junit.Test;
import sun.misc.Unsafe;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import static org.junit.Assert.*;

public class ReplayHandshakeTest {
    @Test(timeout=2000) public void synchronousRestartAppliesQueuedJoinGameBeforeForgeRegistrationWaits() throws Exception {
        CountDownLatch playClient=new CountDownLatch(1);
        FMLClientHandler forge=allocate(FMLClientHandler.class); set(forge,FMLClientHandler.class,"playClientBlock",playClient);
        ReplayHandler replay=allocate(ReplayHandler.class); Network network=allocate(Network.class);
        network.login=playClient::countDown; set(replay,ReplayHandler.class,"editorNetwork",network);
        Sender sender=allocate(Sender.class); sender.network=network; sender.forge=forge; sender.latch=playClient;
        set(sender,FullReplaySender.class,"replayHandler",replay); set(sender,FullReplaySender.class,"replaySpeed",1d);
        sender.ctx=(ChannelHandlerContext)Proxy.newProxyInstance(ChannelHandlerContext.class.getClassLoader(),new Class<?>[]{ChannelHandlerContext.class},(proxy,method,args)->null);
        sender.sendPacketsTill(100);
        assertEquals(2,sender.applied); assertEquals(2,network.drains); assertTrue(sender.registered);
        assertEquals(100,sender.currentTimeStamp());
    }
    private static class Network extends NetworkManager {
        Runnable login; boolean queued; int drains;
        Network() { super(true); }
        @Override public void processReceivedPackets() { drains++; if(queued) { queued=false; login.run(); } }
    }
    private static class Sender extends FullReplaySender {
        int read,applied; boolean registered; Network network; FMLClientHandler forge; CountDownLatch latch;
        Sender() throws IOException { super(null,null,false); }
        @Override protected PacketData readEditorSeekPacket() throws IOException {
            if(read==2) throw new EOFException(); return new PacketData(++read*10,new byte[]{(byte)read});
        }
        @Override public void channelRead(ChannelHandlerContext ctx,Object packet) {
            applied++;
            if(applied==1) network.queued=true;
            else {
                assertEquals("Queued JoinGame must run before REGISTER waits on the same thread",0,latch.getCount());
                forge.waitForPlayClient(); registered=true;
            }
        }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
    private static void set(Object object,Class<?> owner,String name,Object value) throws Exception { Field f=owner.getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
}
