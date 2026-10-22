package com.replaymod.replay;

import com.google.gson.Gson;
import com.replaymod.core.ReplayModLateMixins;
import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.replay.mixin.MixinAngelicaShaderHand;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.replay.compat.ReplayShaderClock;
import de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class ReplayShaderClockTest {
    @Test public void animationsFollowSourceTimeWhileTemporalFiltersFollowOutputRate() {
        for (int fps : new int[]{30, 60, 120}) {
            Export export = new Export(fps);
            for (int frame = 0; frame < fps * 2; frame++) {
                export.done = frame + 1;
                long source = 10000 + frame * 500 / fps; // half-speed replay
                assertEquals(source / 1000.0F, ReplayShaderClock.animationSeconds(source), 0);
                assertEquals(1.0F / fps, ReplayShaderClock.frameSeconds(export, source, source - 8), 0);
                assertEquals(frame, ReplayShaderClock.frameCounter(export));
                // GPU/terrain cost and repeated passes cannot advance the clock or TAA jitter.
                for (int retry = 0; retry < 8; retry++) {
                    assertEquals(frame, ReplayShaderClock.frameCounter(export));
                    assertEquals(1.0F / fps, ReplayShaderClock.frameSeconds(export, source, source), 0);
                }
            }
        }
    }

    @Test public void freezesSeeksAndHourWrapRemainFiniteAndDeterministic() {
        Export export = new Export(60);
        for (long source : new long[]{0, 32155, 3599999, 3600000, 7200016}) {
            float phase = ReplayShaderClock.animationSeconds(source);
            assertTrue(phase >= 0 && phase < 3600);
            for (int frame = 0; frame < 60; frame++) {
                export.done = frame + 1;
                assertEquals(phase, ReplayShaderClock.animationSeconds(source), 0);
                assertEquals(1.0F / 60, ReplayShaderClock.frameSeconds(export, source, source), 0);
            }
        }
        assertEquals(.016F, ReplayShaderClock.animationSeconds(7200016), .00001F);
        assertEquals(.017F, ReplayShaderClock.frameSeconds(null, 10017, 10000), 0);
        for (long previous : new long[]{-1, 10000, 50000, 0}) {
            float delta = ReplayShaderClock.frameSeconds(null, 10000, previous);
            assertTrue(delta > 0 && delta <= .1F);
        }
        export.done = 720721;
        assertEquals(0, ReplayShaderClock.frameCounter(export));
        export.done = 1;
        assertEquals(0, ReplayShaderClock.frameCounter(export));
    }

    @Test public void installedAngelicaJava8AndJava17ExposeTheExactInjectedTimerContract() throws Exception {
        String file = System.getenv("REPLAYMOD_TEST_ANGELICA"); assumeNotNull(file);
        try (ZipFile zip = new ZipFile(file)) {
            for (String prefix : new String[]{"", "META-INF/versions/17/"}) {
                ClassNode timer = read(zip, prefix + "net/coderbot/iris/uniforms/SystemTimeUniforms$Timer.class");
                method(timer, "beginFrame", "(J)V"); method(timer, "reset", "()V");
                field(timer, "frameTimeCounter", "F"); field(timer, "lastFrameTime", "F");
                field(timer, "lastStartTime", "Ljava/util/OptionalLong;");
                ClassNode counter = read(zip, prefix + "net/coderbot/iris/uniforms/SystemTimeUniforms$FrameCounter.class");
                method(counter, "beginFrame", "()V"); field(counter, "count", "I");
            }
            ClassNode render = read(zip, "com/gtnewhorizons/angelica/mixins/early/shaders/MixinEntityRenderer.class");
            boolean begin = false, finish = false;
            for (MethodNode m : render.methods) for (AbstractInsnNode i : m.instructions.toArray()) {
                if (!(i instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)i;
                if (call.owner.equals("net/coderbot/iris/uniforms/SystemTimeUniforms$Timer") && call.name.equals("beginFrame")) begin = true;
                if (call.owner.equals("net/coderbot/iris/pipeline/WorldRenderingPipeline") && call.name.equals("finalizeLevelRendering")) finish = true;
            }
            assertTrue("Direct renderWorld captures must update shader time", begin);
            assertTrue("Direct renderWorld captures must run shader postprocessing", finish);
            ClassNode hand = read(zip, "net/coderbot/iris/pipeline/HandRenderer.class");
            method(hand, "canRender", "(Lcom/gtnewhorizons/angelica/compat/mojang/Camera;Lnet/minecraft/client/renderer/RenderGlobal;)Z");
            for (String renderHand : new String[]{"renderSolid", "renderTranslucent"}) {
                boolean guarded = false;
                for (MethodNode m : hand.methods) if (m.name.equals(renderHand)) for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals(hand.name)
                            && ((MethodInsnNode)i).name.equals("canRender")) guarded = true;
                }
                assertTrue(renderHand + " must obey hand suppression", guarded);
            }
        }
    }

    @Test public void installedAngelicaTerrainHooksFinishBothMainAndShadowPreparation() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_ANGELICA"); assumeNotNull(file);
        try (ZipFile zip=new ZipFile(file)) {
            ClassNode renderer=read(zip,"org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.class");
            String prepare="(Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;Z)V";
            method(renderer,"prepareFrame",prepare);
            for (String name:new String[]{"setupTerrain","setupShadowTerrain"}) {
                boolean calls=false;
                for (MethodNode m:renderer.methods) if (m.name.equals(name)) for (AbstractInsnNode i:m.instructions.toArray()) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode)i).name.equals("prepareFrame") && ((MethodInsnNode)i).desc.equals(prepare)) calls=true;
                }
                assertTrue(name+" must use the synchronous preparation hook",calls);
            }
            for (String prefix:new String[]{"","META-INF/versions/17/"}) {
                ClassNode manager=read(zip,prefix+"org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.class");
                method(manager,"collectChunkBuildResults","()Ljava/util/ArrayList;");
                method(manager,"updateChunks","(Z)V");
            }
            List<String> hooks=ReplayModLateMixins.clientMixins(Collections.singleton("angelica"));
            assertTrue(hooks.contains("MixinAngelicaTerrain")); assertTrue(hooks.contains("MixinAngelicaTerrainUploads"));
            assertFalse(ReplayModLateMixins.clientMixins(Collections.emptySet()).contains("MixinAngelicaTerrain"));
        }
    }

    @Test public void installedAngelicaVisibilityAndShadowCacheHooksControlTheActualDrawPath() throws Exception {
        String file=System.getenv("REPLAYMOD_TEST_ANGELICA"); assumeNotNull(file);
        try (ZipFile zip=new ZipFile(file)) {
            ClassNode lists=read(zip,"org/embeddedt/embeddium/impl/render/chunk/lists/RenderListManager.class");
            method(lists,"submitSearch","(IIILorg/embeddedt/embeddium/impl/render/viewport/Viewport;Ljava/util/function/Function;)V");
            method(lists,"finishPreviousGraphUpdate","()V");
            for (String prefix:new String[]{"","META-INF/versions/17/"}) {
                ClassNode shadow=read(zip,prefix+"net/coderbot/iris/pipeline/ShadowRenderer.class");
                method(shadow,"shadowTerrainAlwaysRelays","()Z");
                boolean controlsDraw=false;
                for (MethodNode m:shadow.methods) if (m.name.equals("renderShadows")) for (AbstractInsnNode i:m.instructions.toArray()) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode)i).name.equals("shadowTerrainAlwaysRelays")) controlsDraw=true;
                }
                assertTrue("Export must bypass the cache gate used by the real shadow draw",controlsDraw);
            }
            List<String> hooks=ReplayModLateMixins.clientMixins(Collections.singleton("angelica"));
            assertTrue(hooks.contains("MixinAngelicaVisibility")); assertTrue(hooks.contains("MixinAngelicaShadow"));
            assertFalse(ReplayModLateMixins.clientMixins(Collections.emptySet()).contains("MixinAngelicaShadow"));
        }
    }

    @Test public void shaderHooksLoadOnlyWhenAngelicaIsPresent() {
        List<String> hooks = ReplayModLateMixins.clientMixins(Collections.singleton("angelica"));
        for (String hook : new String[]{"MixinAngelicaShaderTimer", "MixinAngelicaShaderFrameCounter", "MixinAngelicaShaderHand"}) {
            assertTrue(hooks.contains(hook));
            assertFalse(ReplayModLateMixins.clientMixins(Collections.emptySet()).contains(hook));
            assertNotNull(getClass().getClassLoader().getResource("com/replaymod/replay/mixin/" + hook + ".class"));
        }
    }

    @Test public void angelicaHandPassRespectsReplayCameraCancellationAndLeavesNormalHandsAvailable() throws Exception {
        MixinAngelicaShaderHand hook = new MixinAngelicaShaderHand() { };
        java.lang.reflect.Method invoke = MixinAngelicaShaderHand.class.getDeclaredMethod("replayMod$hideCameraHand", CallbackInfoReturnable.class);
        invoke.setAccessible(true);
        // EventRegistrations initializes Minecraft's mixed-in accessor; use the
        // event directly in this headless test instead of starting a client.
        Object event = PreRenderHandCallback.EVENT;
        java.lang.reflect.Method register = event.getClass().getDeclaredMethod("register", Object.class);
        java.lang.reflect.Method unregister = event.getClass().getDeclaredMethod("unregister", Object.class);
        register.setAccessible(true); unregister.setAccessible(true);
        PreRenderHandCallback cancel = () -> true;
        register.invoke(event, cancel);
        try {
            CallbackInfoReturnable<Boolean> result = new CallbackInfoReturnable<>("canRender", true);
            invoke.invoke(hook, result);
            assertTrue(result.isCancelled()); assertFalse(result.getReturnValue());
        } finally { unregister.invoke(event, cancel); }
        CallbackInfoReturnable<Boolean> result = new CallbackInfoReturnable<>("canRender", true);
        invoke.invoke(hook, result);
        assertFalse(result.isCancelled());
    }

    private static void method(ClassNode type, String name, String desc) {
        for (MethodNode method : type.methods) if (method.name.equals(name) && method.desc.equals(desc)) return;
        fail(type.name + " missing " + name + desc);
    }
    private static void field(ClassNode type, String name, String desc) {
        for (FieldNode field : type.fields) if (field.name.equals(name) && field.desc.equals(desc)) return;
        fail(type.name + " missing " + name + ":" + desc);
    }
    private static ClassNode read(ZipFile zip, String path) throws IOException {
        assertNotNull(path, zip.getEntry(path));
        try (InputStream input = zip.getInputStream(zip.getEntry(path)); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            byte[] data = bytes.toByteArray(); data[6] = 0; data[7] = 52;
            ClassNode node = new ClassNode(); new ClassReader(data).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
    private static final class Export implements RenderInfo {
        int done;
        final RenderSettings settings;
        Export(int fps) { settings = new Gson().fromJson("{\"framesPerSecond\":" + fps + "}", RenderSettings.class); }
        @Override public ReadableDimension getFrameSize() { return null; }
        @Override public int getFramesDone() { return done; }
        @Override public int getTotalFrames() { return 240; }
        @Override public float updateForNextFrame() { return 0; }
        @Override public RenderSettings getRenderSettings() { return settings; }
    }
}
