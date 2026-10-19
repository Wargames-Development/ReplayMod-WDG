package com.replaymod.replay.camera;

import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.events.KeyBindingEventCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.core.events.SettingsChangedCallback;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.events.RenderHotbarCallback;
import com.replaymod.replay.events.RenderSpectatorCrosshairCallback;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import com.replaymod.core.utils.Utils;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.Setting;
import com.replaymod.replay.mixin.FirstPersonRendererAccessor;
import com.replaymod.replaystudio.util.Location;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.AxisAlignedBB;

import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import com.replaymod.replay.events.ReplayChatMessageEvent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.Session;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.replaymod.core.versions.MCVer.*;

/**
 * The camera entity used as the main player entity during replay viewing.
 * During a replay the player should be an instance of this class.
 * Camera movement is controlled by a separate {@link CameraController}.
 */
@SuppressWarnings("EntityConstructor")
public class CameraEntity extends EntityClientPlayerMP {
    private static final UUID CAMERA_UUID = UUID.nameUUIDFromBytes("ReplayModCamera".getBytes(StandardCharsets.UTF_8));

    /**
     * Roll of this camera in degrees.
     */
    public float roll;

    private CameraController cameraController;

    private long lastControllerUpdate = System.currentTimeMillis();

    /**
     * The entity whose hand was the last one rendered.
     */
    private Entity lastHandRendered = null;

    /**
     * The hashCode and equals methods of Entity are not stable.
     * Therefore we cannot register any event handlers directly in the CameraEntity class and
     * instead have this inner class.
     */
    private EventHandler eventHandler = new EventHandler();

    public CameraEntity(Minecraft mcIn, World worldIn, Session session, NetHandlerPlayClient netHandlerPlayClient, StatFileWriter statisticsManager) {
        super(mcIn, worldIn, session, netHandlerPlayClient, statisticsManager);
        entityUniqueID = CAMERA_UUID;
        eventHandler.register();
        if (ReplayModReplay.instance.getReplayHandler().getSpectatedUUID() == null) {
            cameraController = ReplayModReplay.instance.createCameraController(this);
        } else {
            cameraController = new SpectatorCameraController(this);
        }
    }

    public CameraController getCameraController() {
        return cameraController;
    }

    public void setCameraController(CameraController cameraController) {
        this.cameraController = cameraController;
    }

    /**
     * Moves the camera by the specified delta.
     * @param x Delta in X direction
     * @param y Delta in Y direction
     * @param z Delta in Z direction
     */
    public void moveCamera(double x, double y, double z) {
        setCameraPosition(this.posX + x, this.posY + y, this.posZ + z);
    }

    /**
     * Set the camera position.
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void setCameraPosition(double x, double y, double z) {
        this.lastTickPosX = this.prevPosX = x;
        this.lastTickPosY = this.prevPosY = y;
        this.lastTickPosZ = this.prevPosZ = z;
        { net.minecraft.entity.Entity self = this; self.posX = x; self.posY = y; self.posZ = z; }
        updateBoundingBox();
    }

    /**
     * Sets the camera rotation.
     * @param yaw Yaw in degrees
     * @param pitch Pitch in degrees
     * @param roll Roll in degrees
     */
    public void setCameraRotation(float yaw, float pitch, float roll) {
        this.prevRotationYaw = this.rotationYaw = yaw;
        this.prevRotationPitch = this.rotationPitch = pitch;
        this.roll = roll;
    }

    /**
     * Sets the camera position and rotation to that of the specified AdvancedPosition
     * @param pos The position and rotation to set
     */
    public void setCameraPosRot(Location pos) {
        setCameraRotation(pos.getYaw(), pos.getPitch(), roll);
        setCameraPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Sets the camera position and rotation to that of the specified entity.
     * @param to The entity whose position to copy
     */
    public void setCameraPosRot(Entity to) {
        if (to == this) return;
        float yOffset = 1.62f; // Magic value (eye height) from EntityRenderer#orientCamera
        this.prevPosX = to.prevPosX;
        this.prevPosY = to.prevPosY + yOffset;
        this.prevPosZ = to.prevPosZ;
        this.prevRotationYaw = to.prevRotationYaw;
        this.prevRotationPitch = to.prevRotationPitch;
        { net.minecraft.entity.Entity self = this; self.posX = to.posX; self.posY = to.posY; self.posZ = to.posZ; }
        this.rotationYaw = to.rotationYaw;
        this.rotationPitch = to.rotationPitch;
        this.lastTickPosX = to.lastTickPosX;
        this.lastTickPosY = to.lastTickPosY + yOffset;
        this.lastTickPosZ = to.lastTickPosZ;
        updateBoundingBox();
    }

    private void updateBoundingBox() {
        this.boundingBox.setBB(AxisAlignedBB.getBoundingBox(
                this.posX - width / 2, this.posY, this.posZ - width / 2,
                this.posX + width / 2, this.posY + height, this.posZ + width / 2));
    }

    @Override
    public void onUpdate() {
        refreshSpectatedView();
    }

    @Override
    public void mountEntity(Entity entityIn) {
        // Replay mounts belong to recorded entities. Mounting the observer would
        // let vehicle mods treat camera input as live pilot/gunner controls.
    }

    /**
     * Re-acquires the stored spectated player after replay restarts/seeks and keeps the
     * camera at their position. This must also be callable from the render path because
     * replay seeking commonly leaves playback paused, in which case normal world ticks
     * do not run until the user presses Play again.
     */
    private void refreshSpectatedView() {
        if (this.worldObj == null) {
            return;
        }

        EntityLivingBase view = this.mc.renderViewEntity;
        UUID spectating = ReplayModReplay.instance.getReplayHandler().getSpectatedUUID();

        if (spectating == null && !(cameraController instanceof SpectatorCameraController)) {
            // A mod's cockpit/chase camera is not an explicit spectator selection.
            // Keep free-camera ownership even when that mod's dummy is in the world.
            this.mc.renderViewEntity = this;
            return;
        }

        if (spectating != null) {
            boolean wrongPlayer = view == null || !spectating.equals(view.getUniqueID());
            boolean wrongWorld = view != null && view.worldObj != this.worldObj;
            boolean missingFromWorld = view != null && view != this
                    && this.worldObj.getEntityByID(view.getEntityId()) != view;

            if (wrongPlayer || wrongWorld || missingFromWorld) {
                EntityPlayer player = this.worldObj.getPlayerEntityByUUID(spectating);
                if (player != null) {
                    this.mc.renderViewEntity = player;
                    view = player;
                } else {
                    // Keep the UUID so a later render/tick can re-acquire the player as
                    // soon as the seek has replayed their spawn packet.
                    this.mc.renderViewEntity = this;
                    view = this;
                }
            }
        } else if (view != null && view != this
                && (view.worldObj != this.worldObj
                || this.worldObj.getEntityByID(view.getEntityId()) != view)) {
            // A non-player view entity disappeared. Return to the free camera.
            ReplayModReplay.instance.getReplayHandler().spectateEntity(this);
            view = this;
        }

        // Move camera to their position so when we exit first-person view we do not
        // jump back to where spectating started.
        if (view != null && view != this) {
            setCameraPosRot(view);
        }
    }

    @Override
    public void preparePlayerToSpawn() {
        // Make sure our world is up-to-date in case of world changes
        if (this.mc.theWorld != null) {
            this.worldObj = this.mc.theWorld;
        }
        super.preparePlayerToSpawn();
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        if (this.mc.renderViewEntity == this) {
            // Only update camera rotation when the camera is the view
            super.setRotation(yaw, pitch);
        }
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return falseUnlessSpectating(Entity::isEntityInsideOpaqueBlock); // Make sure no suffocation overlay is rendered
    }

    @Override
    public boolean isInsideOfMaterial(Material materialIn) {
        return falseUnlessSpectating(e -> e.isInsideOfMaterial(materialIn)); // Make sure no overlays are rendered
    }

    @Override
    public boolean handleLavaMovement() {
        return falseUnlessSpectating(Entity::handleLavaMovement); // Make sure no lava overlay is rendered
    }


    @Override
    public boolean isInWater() {
        return falseUnlessSpectating(Entity::isInWater); // Make sure no water overlay is rendered
    }

    @Override
    public boolean isBurning() {
        return falseUnlessSpectating(Entity::isBurning); // Make sure no fire overlay is rendered
    }

    private boolean falseUnlessSpectating(Function<Entity, Boolean> property) {
        Entity view = this.mc.renderViewEntity;
        if (view != null && view != this) {
            return property.apply(view);
        }
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false; // We are in full control of ourselves
    }

    @Override
    public boolean canBeCollidedWith() {
        return false; // We are a camera, we cannot collide
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        // Never render the camera
        // This is necessary to hide the player head in third person mode and to not
        // cause any unwanted shadows when rendering with shaders.
        return false;
    }

    @Override
    public float getFOVMultiplier() {
        return 1;
    }

    @Override
    public boolean isInvisible() {
        Entity view = this.mc.renderViewEntity;
        if (view != this) {
            return view.isInvisible();
        }
        return super.isInvisible();
    }

    @Override
    public ResourceLocation getLocationSkin() {
        Entity view = this.mc.renderViewEntity;
        if (view != this && view instanceof EntityPlayer) {
            return Utils.getResourceLocationForPlayerUUID(view.getUniqueID());
        }
        return super.getLocationSkin();
    }

    @Override
    public float getSwingProgress(float renderPartialTicks) {
        Entity view = this.mc.renderViewEntity;
        if (view != this && view instanceof EntityPlayer) {
            return ((EntityPlayer) view).getSwingProgress(renderPartialTicks);
        }
        return 0;
    }

    @Override
    public MovingObjectPosition rayTrace(double p_174822_1_, float p_174822_3_) {
        MovingObjectPosition pos = super.rayTrace(p_174822_1_, 1f);

        // Make sure we can never look at blocks (-> no outline)
        if(pos != null && pos.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            pos.typeOfHit = MovingObjectPosition.MovingObjectType.MISS;
        }

        return pos;
    }

    @Override
    public void openGui(Object mod, int modGuiId, World world, int x, int y, int z) {
        // Do not open any block GUIs for the camera entities
        // Note: Vanilla GUIs are filtered out on a packet level, this only applies to mod GUIs
    }

    @Override
    public void setDead() {
        super.setDead();
        if (eventHandler != null) {
            eventHandler.unregister();
            eventHandler = null;
        }
    }

    private void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != this.worldObj) {
            if (eventHandler != null) {
                eventHandler.unregister();
                eventHandler = null;
            }
            return;
        }

        // PreRender continues to run while replay playback is paused, unlike onUpdate.
        // Rebind the spectated player here so timeline seeks immediately follow them
        // without requiring the user to press Play first.
        refreshSpectatedView();

        long now = System.currentTimeMillis();
        long timePassed = now - lastControllerUpdate;
        if (!(mc.currentScreen instanceof com.replaymod.studio.StudioScreen)) {
            cameraController.update(timePassed / 50f);
        }
        lastControllerUpdate = now;

        handleInputEvents();

        Map<String, KeyBindingRegistry.Binding> keyBindings = ReplayMod.instance.getKeyBindingRegistry().getBindings();
        if (keyBindings.get("replaymod.input.rollclockwise").keyBinding.getIsKeyPressed()) {
            roll += Utils.isCtrlDown() ? 0.2 : 1;
        }
        if (keyBindings.get("replaymod.input.rollcounterclockwise").keyBinding.getIsKeyPressed()) {
            roll -= Utils.isCtrlDown() ? 0.2 : 1;
        }
    }

    private void handleInputEvents() {
        if (this.mc.gameSettings.keyBindSneak.getIsKeyPressed()) {
            return; // Do not re-enter spectating during the same frame as leaving it.
        }
        if (this.mc.gameSettings.keyBindAttack.isPressed() || this.mc.gameSettings.keyBindUseItem.isPressed()) {
            if (this.mc.currentScreen == null && canSpectate(this.mc.pointedEntity)) {
                ReplayModReplay.instance.getReplayHandler().spectateEntity((EntityLivingBase) this.mc.pointedEntity);
                // Make sure we don't exit right away
                //noinspection StatementWithEmptyBody
                while (this.mc.gameSettings.keyBindSneak.isPressed());
            }
        }
    }

    private void updateArmYawAndPitch() {
        this.prevRenderArmYaw = this.renderArmYaw;
        this.prevRenderArmPitch = this.renderArmPitch;
        this.renderArmPitch = this.renderArmPitch +  (this.rotationPitch - this.renderArmPitch) * 0.5f;
        this.renderArmYaw = this.renderArmYaw +  (this.rotationYaw - this.renderArmYaw) * 0.5f;
    }

    public boolean canSpectate(Entity e) {
        // Mod camera dummies are also EntityPlayers, but are not recorded players.
        return e instanceof EntityOtherPlayerMP
                && !e.isInvisible();
    }

    @Override
    public void addChatMessage(IChatComponent message) {
        if (MinecraftForge.EVENT_BUS.post(new ReplayChatMessageEvent(this))) return;
        super.addChatMessage(message);
    }

    // All event handlers need to be public in 1.7.10
    public class EventHandler extends EventRegistrations {
        private final Minecraft mc = getMinecraft();

        private EventHandler() {}

        { on(PreTickCallback.EVENT, this::onPreClientTick); }
        private void onPreClientTick() {
            updateArmYawAndPitch();
        }

        { on(PreRenderCallback.EVENT, this::onRenderUpdate); }
        private void onRenderUpdate() {
            update();
        }

        { on(KeyBindingEventCallback.EVENT, CameraEntity.this::handleInputEvents); }

        { on(RenderSpectatorCrosshairCallback.EVENT, this::shouldRenderSpectatorCrosshair); }
        private Boolean shouldRenderSpectatorCrosshair() {
            return canSpectate(mc.pointedEntity);
        }

        { on(RenderHotbarCallback.EVENT, this::shouldRenderHotbar); }
        private Boolean shouldRenderHotbar() {
            return false;
        }

        { on(SettingsChangedCallback.EVENT, this::onSettingsChanged); }
        private void onSettingsChanged(SettingsRegistry registry, SettingsRegistry.SettingKey<?> key) {
            if (key == Setting.CAMERA) {
                if (ReplayModReplay.instance.getReplayHandler().getSpectatedUUID() == null) {
                    cameraController = ReplayModReplay.instance.createCameraController(CameraEntity.this);
                } else {
                    cameraController = new SpectatorCameraController(CameraEntity.this);
                }
            }
        }

        { on(PreRenderHandCallback.EVENT, this::onRenderHand); }
        private boolean onRenderHand() {
            Entity view = mc.renderViewEntity;
            if (view == CameraEntity.this || !(view instanceof EntityPlayer)) {
                return true; // cancel hand rendering
            } else {
                EntityPlayer player = (EntityPlayer) view;
                // When the spectated player has changed, force equip their items to prevent the equip animation
                if (lastHandRendered != player) {
                    lastHandRendered = player;

                    FirstPersonRendererAccessor acc = (FirstPersonRendererAccessor) mc.entityRenderer.itemRenderer;
                    acc.setPrevEquippedProgress(1);
                    acc.setEquippedProgress(1);
                    acc.setItemToRender(player.inventory.getCurrentItem());
                    acc.setEquippedItemSlot(player.inventory.currentItem);

                    mc.thePlayer.renderArmYaw = mc.thePlayer.prevRenderArmYaw = player.rotationYaw;
                    mc.thePlayer.renderArmPitch = mc.thePlayer.prevRenderArmPitch = player.rotationPitch;
                }
                return false;
            }
        }

        private boolean heldItemTooltipsWasTrue;

        @SubscribeEvent
        public void preRenderGameOverlay(RenderGameOverlayEvent.Pre event) {
            switch (event.type) {
                case ALL:
                    heldItemTooltipsWasTrue = mc.gameSettings.heldItemTooltips;
                    mc.gameSettings.heldItemTooltips = false;
                    break;
                case ARMOR:
                case HEALTH:
                case FOOD:
                case AIR:
                case HOTBAR:
                case EXPERIENCE:
                case HEALTHMOUNT:
                case JUMPBAR:
                    event.setCanceled(true);
                    break;
                case HELMET:
                case PORTAL:
                case CROSSHAIRS:
                case BOSSHEALTH:
                case TEXT:
                case CHAT:
                case PLAYER_LIST:
                case DEBUG:
                    break;
            }
        }

        @SubscribeEvent
        public void postRenderGameOverlay(RenderGameOverlayEvent.Post event) {
            if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
            mc.gameSettings.heldItemTooltips = heldItemTooltipsWasTrue;
        }
    }
}
