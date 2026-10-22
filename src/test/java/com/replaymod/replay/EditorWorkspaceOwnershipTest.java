package com.replaymod.replay;

import com.replaymod.studio.CinematicEditor;
import net.minecraft.client.Minecraft;
import org.junit.Test;
import sun.misc.Unsafe;
import java.io.IOException;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class EditorWorkspaceOwnershipTest {
    @Test public void loadingIsCoveredDuringPlaybackRestartEvenWithNoScrubPending() throws Exception {
        Object previous=get(null,ReplayHandler.class,"mc");
        try {
            ReplayHandler replay=allocate(ReplayHandler.class); Minecraft mc=allocate(Minecraft.class);
            CinematicEditor editor=allocate(CinematicEditor.class);
            set(editor,CinematicEditor.class,"replay",replay); mc.currentScreen=editor;
            set(null,ReplayHandler.class,"mc",mc);
            assertFalse(replay.isEditorSeeking()); assertTrue(replay.keepEditorVisible());
            set(editor,CinematicEditor.class,"leavingWorkspace",true); assertFalse(replay.keepEditorVisible());
            set(editor,CinematicEditor.class,"leavingWorkspace",false); set(replay,ReplayHandler.class,"closing",true);
            assertFalse(replay.keepEditorVisible());
        } finally { set(null,ReplayHandler.class,"mc",previous); }
    }
    @Test public void leavingEditorRestoresTheAsyncWorkerOnlyOnce() throws Exception {
        ReplayHandler replay=allocate(ReplayHandler.class); Sender sender=allocate(Sender.class);
        set(replay,ReplayHandler.class,"fullReplaySender",sender);
        set(replay,ReplayHandler.class,"editorWasAsync",true);
        replay.releaseEditorSender(); replay.releaseEditorSender();
        assertEquals(1,sender.starts);
        set(replay,ReplayHandler.class,"editorWasAsync",true); set(replay,ReplayHandler.class,"closing",true);
        replay.releaseEditorSender(); assertEquals(1,sender.starts);
    }
    private static class Sender extends FullReplaySender {
        int starts;
        private Sender() throws IOException { super(null,null,false); }
        @Override public void setAsyncMode(boolean async) { if(async) starts++; }
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field=Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static Object get(Object object,Class<?> owner,String name) throws Exception { Field field=owner.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void set(Object object,Class<?> owner,String name,Object value) throws Exception { Field field=owner.getDeclaredField(name); field.setAccessible(true); field.set(object,value); }
}
