package com.replaymod.replay;

import com.replaymod.core.ReplayModLateMixins;
import com.replaymod.render.mixin.MixinCloudClock;
import com.replaymod.replay.mixin.MixinAngelicaCloudClock;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.spongepowered.asm.lib.*;
import org.spongepowered.asm.lib.tree.*;
import sun.misc.Unsafe;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class ReplayCloudClockTest {
    @Test public void cloudDriftIsContinuousAcrossTickBoundariesAndIndependentOfNativeCounterOrCapturePass() throws Exception {
        ReplayModReplay previous=ReplayModReplay.instance;
        try {
            ReplayModReplay module=allocate(ReplayModReplay.class);
            ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
            set(replay,ReplayHandler.class,"fullReplaySender",sender);
            set(module,ReplayModReplay.class,"replayHandler",replay); ReplayModReplay.instance=module;
            for(Object renderer:new Object[]{new Vanilla(),new Angelica()}) {
                Method hook=renderer.getClass().getSuperclass().getDeclaredMethod("replayMod$cloudTime",double.class); hook.setAccessible(true);
                for(int fps:new int[]{24,30,60,120}) {
                    double last=200;
                    for(int frame=0;frame<fps*2;frame++) {
                        sender.time=10000+frame*1000/fps;
                        // A stale integer tick combined with a wrapping fraction used to move backwards.
                        double nativeTicks=200+(frame%3)/3.0;
                        double time=(double)hook.invoke(renderer,nativeTicks);
                        assertEquals(sender.time/50.0,time,0);
                        assertTrue(time>=last); assertTrue(time-last<=1.0); last=time;
                        assertEquals(time,(double)hook.invoke(renderer,nativeTicks+500),0);
                    }
                }
                sender.time=31055; double frozen=(double)hook.invoke(renderer,100.0);
                for(int pass=0;pass<12;pass++) assertEquals(frozen,(double)hook.invoke(renderer,999.0+pass),0);
                sender.time=1000; assertEquals(20,(double)hook.invoke(renderer,999.0),0);
                ReplayModReplay.instance=null;
                assertEquals(321.75,(double)hook.invoke(renderer,321.75),0);
                ReplayModReplay.instance=module;
            }
        } finally { ReplayModReplay.instance=previous; }
    }
    @Test public void vanillaClockLocalAndLateAngelicaRegistrationMatchTheirRealRenderPaths() throws Exception {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("net/minecraft/client/renderer/RenderGlobal.class")) {
            ClassNode type=read(input);
            for(String name:new String[]{"renderClouds","renderCloudsFancy"}) {
                MethodNode method=find(type,name,"(F)V"); assertNotNull(method);
                assertEquals("First double local must still contain the vanilla cloud clock",Opcodes.F2D,firstDoubleStore(method).getOpcode());
            }
        }
        List<String> hooks=ReplayModLateMixins.clientMixins(Collections.singleton("angelica"));
        assertTrue(hooks.contains("MixinAngelicaCloudClock"));
        for(String hook:hooks) assertNotNull(getClass().getClassLoader().getResource("com/replaymod/replay/mixin/"+hook+".class"));
        assertFalse(ReplayModLateMixins.clientMixins(Collections.emptySet()).contains("MixinAngelicaCloudClock"));
    }
    @Test public void installedAngelicaBaseAndJava17RenderersExposeTheSameCloudClockHook() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_ANGELICA"); assumeNotNull(file);
        try(ZipFile zip=new ZipFile(file)) {
            for(String path:new String[]{"com/gtnewhorizons/angelica/render/CloudRenderer.class","META-INF/versions/17/com/gtnewhorizons/angelica/render/CloudRenderer.class"}) {
                try(InputStream input=zip.getInputStream(zip.getEntry(path))) {
                    ClassNode type=read(input); MethodNode render=find(type,"render","(IF)Z"); assertNotNull(path,render);
                    assertEquals(path+" first double must still be ticks + partialTicks",Opcodes.DADD,firstDoubleStore(render).getOpcode());
                }
            }
        }
    }
    private static AbstractInsnNode firstDoubleStore(MethodNode method) {
        for(AbstractInsnNode node:method.instructions.toArray()) if(node.getOpcode()==Opcodes.DSTORE) {
            AbstractInsnNode previous=node.getPrevious(); while(previous.getOpcode()<0) previous=previous.getPrevious(); return previous;
        }
        throw new AssertionError("No double clock local in "+method.name);
    }
    private static ClassNode read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while((count=input.read(buffer))>=0) bytes.write(buffer,0,count);
        byte[] data=bytes.toByteArray();
        // Inspect Java 17 bytecode with the project's older ASM. This changes only
        // the parser's version header, never the installed class or instructions.
        data[6]=0; data[7]=52;
        ClassNode node=new ClassNode(); new ClassReader(data).accept(node,ClassReader.SKIP_FRAMES); return node;
    }
    private static MethodNode find(ClassNode type,String name,String desc) { for(MethodNode method:type.methods) if(method.name.equals(name)&&method.desc.equals(desc)) return method; return null; }
    private static class Vanilla extends MixinCloudClock { }
    private static class Angelica extends MixinAngelicaCloudClock { }
    private static class Sender extends FullReplaySender {
        int time;
        Sender() throws IOException { super(null,null,false); }
        @Override public int currentTimeStamp() { return time; }
    }
    private static <T> T allocate(Class<T> type) throws Exception { Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((Unsafe)f.get(null)).allocateInstance(type)); }
    private static void set(Object target,Class<?> type,String name,Object value) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value); }
}
