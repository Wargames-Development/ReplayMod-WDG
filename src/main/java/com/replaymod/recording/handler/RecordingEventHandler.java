package com.replaymod.recording.handler;

import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.recording.mixin.IntegratedServerAccessor;
import com.replaymod.recording.packet.PacketListener;
import de.johni0702.minecraft.gui.utils.EventRegistrations;
import de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemPickupEvent;

import net.minecraft.network.play.server.S0APacketUseBed;

import net.minecraft.util.MathHelper;

import java.util.Objects;

import static com.replaymod.core.versions.MCVer.*;

public class RecordingEventHandler extends EventRegistrations {

    private final Minecraft mc = getMinecraft();
    private final PacketListener packetListener;

    private Double lastX, lastY, lastZ;
    private ItemStack[] playerItems = new ItemStack[6];
    private int ticksSinceLastCorrection;
    private boolean wasSleeping;
    private int lastRiding = -1;
    private Integer rotationYawHeadBefore;

    public RecordingEventHandler(PacketListener packetListener) {
        this.packetListener = packetListener;
    }

    @Override
    public void register() {
        super.register();
        ((RecordingEventSender) mc.renderGlobal).setRecordingEventHandler(this);
    }

    @Override
    public void unregister() {
        super.unregister();
        RecordingEventSender recordingEventSender = ((RecordingEventSender) mc.renderGlobal);
        if (recordingEventSender.getRecordingEventHandler() == this) {
            recordingEventSender.setRecordingEventHandler(null);
        }
    }

    public void spawnRecordingPlayer() {
        try {
            EntityPlayerSP player = mc.thePlayer;
            assert player != null;
            packetListener.save(new S0CPacketSpawnPlayer(player));
            lastX = lastY = lastZ = null;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    { on(PreTickCallback.EVENT, this::onPlayerTick); }
    private void onPlayerTick() {
        if (mc.thePlayer == null) return;
        EntityPlayerSP player = mc.thePlayer;
        try {

            boolean force = false;
            if(lastX == null || lastY == null || lastZ == null) {
                force = true;
                lastX = player.posX;
                lastY = player.posY;
                lastZ = player.posZ;
            }

            ticksSinceLastCorrection++;
            if(ticksSinceLastCorrection >= 100) {
                ticksSinceLastCorrection = 0;
                force = true;
            }

            double dx = player.posX - lastX;
            double dy = player.posY - lastY;
            double dz = player.posZ - lastZ;

            lastX = player.posX;
            lastY = player.posY;
            lastZ = player.posZ;

            Packet packet;
            if (force || Math.abs(dx) > 8.0 || Math.abs(dy) > 8.0 || Math.abs(dz) > 8.0) {
                // In 1.7.10 the client player entity has its posY at eye height
                // but for all other entities it's at their feet (as it should be).
                // So, to correctly position the player, we teleport them to their feet (i.a. directly after spawn).
                // Note: this leaves the lastY value offset by the eye height but because it's only used for relative
                //       movement, that doesn't matter.
                S18PacketEntityTeleport teleportPacket = new S18PacketEntityTeleport(player);
                packet = new S18PacketEntityTeleport(
                        teleportPacket.func_149451_c(),
                        teleportPacket.func_149449_d(),
                        MathHelper.floor_double(player.boundingBox.minY * 32),
                        teleportPacket.func_149446_f(),
                        teleportPacket.func_149450_g(),
                        teleportPacket.func_149447_h()
                );
            } else {
                byte newYaw = (byte) ((int) (player.rotationYaw * 256.0F / 360.0F));
                byte newPitch = (byte) ((int) (player.rotationPitch * 256.0F / 360.0F));
                packet = new S14PacketEntity.S17PacketEntityLookMove(
                        player.getEntityId(),
                        (byte) Math.round(dx * 32), (byte) Math.round(dy * 32), (byte) Math.round(dz * 32),
                        newYaw, newPitch
                );
            }

            packetListener.save(packet);

            //HEAD POS
            int rotationYawHead = ((int)(player.rotationYawHead * 256.0F / 360.0F));

            if(!Objects.equals(rotationYawHead, rotationYawHeadBefore)) {
                packetListener.save(new S19PacketEntityHeadLook(player, (byte) rotationYawHead));
                rotationYawHeadBefore = rotationYawHead;
            }

            packetListener.save(new S12PacketEntityVelocity(player.getEntityId(), player.motionX, player.motionY, player.motionZ));

            //Animation Packets
            //Swing Animation
            if (player.isSwingInProgress && player.swingProgressInt == 0) {
                packetListener.save(new S0BPacketAnimation(player, 0));
            }

			/*
        //Potion Effect Handling
		List<Integer> found = new ArrayList<Integer>();
		for(PotionEffect pe : (Collection<PotionEffect>)player.getActivePotionEffects()) {
			found.add(pe.getPotionID());
			if(lastEffects.contains(found)) continue;
			S1DPacketEntityEffect pee = new S1DPacketEntityEffect(entityID, pe);
			packetListener.save(pee);
		}

		for(int id : lastEffects) {
			if(!found.contains(id)) {
				S1EPacketRemoveEntityEffect pre = new S1EPacketRemoveEntityEffect(entityID, new PotionEffect(id, 0));
				packetListener.save(pre);
			}
		}

		lastEffects = found;
			 */

            //Inventory Handling
            if(playerItems[0] != mc.thePlayer.getHeldItem()) {
                playerItems[0] = mc.thePlayer.getHeldItem();
                S04PacketEntityEquipment pee = new S04PacketEntityEquipment(player.getEntityId(), 0, playerItems[0]);
                packetListener.save(pee);
            }

            if(playerItems[1] != mc.thePlayer.inventory.armorInventory[0]) {
                playerItems[1] = mc.thePlayer.inventory.armorInventory[0];
                S04PacketEntityEquipment pee = new S04PacketEntityEquipment(player.getEntityId(), 1, playerItems[1]);
                packetListener.save(pee);
            }

            if(playerItems[2] != mc.thePlayer.inventory.armorInventory[1]) {
                playerItems[2] = mc.thePlayer.inventory.armorInventory[1];
                S04PacketEntityEquipment pee = new S04PacketEntityEquipment(player.getEntityId(), 2, playerItems[2]);
                packetListener.save(pee);
            }

            if(playerItems[3] != mc.thePlayer.inventory.armorInventory[2]) {
                playerItems[3] = mc.thePlayer.inventory.armorInventory[2];
                S04PacketEntityEquipment pee = new S04PacketEntityEquipment(player.getEntityId(), 3, playerItems[3]);
                packetListener.save(pee);
            }

            if(playerItems[4] != mc.thePlayer.inventory.armorInventory[3]) {
                playerItems[4] = mc.thePlayer.inventory.armorInventory[3];
                S04PacketEntityEquipment pee = new S04PacketEntityEquipment(player.getEntityId(), 4, playerItems[4]);
                packetListener.save(pee);
            }

            //Leaving Ride

            Entity vehicle = player.ridingEntity;
            int vehicleId = vehicle == null ? -1 : vehicle.getEntityId();
            if (lastRiding != vehicleId) {
                lastRiding = vehicleId;
                packetListener.save(new S1BPacketEntityAttach(0, player, vehicle));
            }

            //Sleeping
            if(!player.isPlayerSleeping() && wasSleeping) {
                packetListener.save(new S0BPacketAnimation(player, 2));
                wasSleeping = false;
            }

        } catch(Exception e1) {
            e1.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onPickupItem(ItemPickupEvent event) {
        try {
            packetListener.save(new S0DPacketCollectItem(event.pickedUp.getEntityId(), event.player.getEntityId()));
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onSleep(PlayerSleepInBedEvent event) {
        try {
            if (event.entityPlayer != mc.thePlayer) {
                return;
            }
            packetListener.save(new S0APacketUseBed(event.entityPlayer, event.x, event.y, event.z));
            wasSleeping = true;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void onBlockBreakAnim(int breakerId, int x, int y, int z, int progress) {
        EntityPlayer thePlayer = mc.thePlayer;
        if (thePlayer != null && breakerId == thePlayer.getEntityId()) {
            packetListener.save(new S25PacketBlockBreakAnim(breakerId, x, y, z, progress));
        }
    }

    { on(PreRenderCallback.EVENT, this::checkForGamePaused); }
    private void checkForGamePaused() {
        if (mc.isSingleplayer()) {
            IntegratedServer server =  mc.getIntegratedServer();
            if (server != null && ((IntegratedServerAccessor) server).isGamePaused()) {
                packetListener.setServerWasPaused();
            }
        }
    }

    public interface RecordingEventSender {
        void setRecordingEventHandler(RecordingEventHandler recordingEventHandler);
        RecordingEventHandler getRecordingEventHandler();
    }
}
