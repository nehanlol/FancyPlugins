package com.fancyinnovations.fancyholograms.hologram;

import com.fancyinnovations.fancyholograms.api.FancyHolograms;
import com.fancyinnovations.fancyholograms.api.data.BlockHologramData;
import com.fancyinnovations.fancyholograms.api.data.HologramData;
import com.fancyinnovations.fancyholograms.api.data.ItemHologramData;
import com.fancyinnovations.fancyholograms.api.data.TextHologramData;
import com.fancyinnovations.fancyholograms.api.data.property.CustomComponentProviderTrait;
import com.fancyinnovations.fancyholograms.api.data.property.HologramRotation;
import com.fancyinnovations.fancyholograms.api.events.HologramDespawnEvent;
import com.fancyinnovations.fancyholograms.api.events.HologramSpawnEvent;
import com.fancyinnovations.fancyholograms.api.hologram.Hologram;
import com.fancyinnovations.fancyholograms.main.FancyHologramsPlugin;
import com.fancyinnovations.fancyholograms.storage.json.JsonAdapter;
import com.fancyinnovations.fancyholograms.api.data.DisplayHologramData;
import com.fancyinnovations.fancyholograms.util.PluginUtils;
import com.viaversion.viaversion.api.Via;
import de.oliver.fancysitula.api.entities.*;
import de.oliver.fancysitula.factories.FancySitula;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramImpl extends Hologram {

    private final Set<UUID> viewersBehind = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Component> lastRenderedTextPerPlayer = new ConcurrentHashMap<>();
    private FS_Display fsDisplay;

    public HologramImpl(@NotNull final HologramData data) {
        super(data);

        createFsDisplay();
    }

    private void createFsDisplay() {
        final var location = data.getLocation();
        if (!location.isWorldLoaded()) {
            return;
        }

        switch (data.getType()) {
            case TEXT -> this.fsDisplay = new FS_TextDisplay();
            case ITEM -> this.fsDisplay = new FS_ItemDisplay();
            case BLOCK -> this.fsDisplay = new FS_BlockDisplay();
        }
    }

    @Override
    public void spawnTo(@NotNull final Player player) {
        if (!new HologramSpawnEvent(this, player).callEvent()) {
            return;
        }

        if (fsDisplay == null) {
            createFsDisplay();
            if (fsDisplay == null) {
                return; // could not be created, nothing to show
            }
        }

        // this implies that the world is loaded
        if (!data.getWorldName().equals(player.getLocation().getWorld().getName())) {
            this.viewers.remove(player.getUniqueId());
            return;
        }

        if (FancyHologramsPlugin.get().getFHConfiguration().isHologramsForOldClientsEnabled()) {
            final var protocolVersion = PluginUtils.isViaVersionEnabled() ? Via.getAPI().getPlayerVersion(player.getUniqueId()) : MINIMUM_PROTOCOL_VERSION;
            if (protocolVersion < MINIMUM_PROTOCOL_VERSION) {
                FancyHolograms.get().getFancyLogger().debug("Player " + player.getName() + " is using an outdated protocol version (" + protocolVersion + "). Hologram will not be shown.");
                return;
            }
        }

        boolean isBehind = false;
        if (data instanceof DisplayHologramData displayData && displayData.getBillboard() == Display.Billboard.FIXED) {
            isBehind = isPlayerBehind(player);
            if (isBehind) {
                this.viewersBehind.add(player.getUniqueId());
            } else {
                this.viewersBehind.remove(player.getUniqueId());
            }
        }

        syncWithData(isBehind);

        FS_RealPlayer fsPlayer = new FS_RealPlayer(player);
        FancySitula.ENTITY_FACTORY.spawnEntityFor(fsPlayer, fsDisplay);

        this.viewers.add(player.getUniqueId());
        updateFor(player);
    }

    @Override
    public void despawnFrom(@NotNull final Player player) {
        if (fsDisplay == null) {
            return; // doesn't exist, nothing to hide
        }

        if (!data.getWorldName().equals(player.getLocation().getWorld().getName())) {
            this.viewers.remove(player.getUniqueId());
            this.viewersBehind.remove(player.getUniqueId());
            this.lastRenderedTextPerPlayer.remove(player.getUniqueId());
            return;
        }

        if (!new HologramDespawnEvent(this, player).callEvent()) {
            return;
        }

        FS_RealPlayer fsPlayer = new FS_RealPlayer(player);
        FancySitula.ENTITY_FACTORY.despawnEntityFor(fsPlayer, fsDisplay);

        this.viewers.remove(player.getUniqueId());
        this.viewersBehind.remove(player.getUniqueId());
        this.lastRenderedTextPerPlayer.remove(player.getUniqueId());
    }

    @Override
    public void updateFor(@NotNull final Player player) {
        if (fsDisplay == null) {
            return; // doesn't exist, nothing to refresh
        }

        if (!data.getWorldName().equals(player.getLocation().getWorld().getName())) {
            this.viewers.remove(player.getUniqueId());
            this.viewersBehind.remove(player.getUniqueId());
            this.lastRenderedTextPerPlayer.remove(player.getUniqueId());
            return;
        }

        if (!isViewer(player)) {
            return;
        }

        boolean isBehind = false;
        if (data instanceof DisplayHologramData displayData && displayData.getBillboard() == Display.Billboard.FIXED) {
            isBehind = isPlayerBehind(player);
            if (isBehind) {
                this.viewersBehind.add(player.getUniqueId());
            } else {
                this.viewersBehind.remove(player.getUniqueId());
            }
        }

        syncWithData(isBehind);

        FS_RealPlayer fsPlayer = new FS_RealPlayer(player);

        float yaw = data.getLocation().getYaw();
        float pitch = data.getLocation().getPitch();
        if (isBehind) {
            yaw = (yaw + 180f) % 360f;
            pitch = -pitch;
        }

        FancySitula.PACKET_FACTORY.createTeleportEntityPacket(
                        fsDisplay.getId(),
                        data.getLocation().x(),
                        data.getLocation().y(),
                        data.getLocation().z(),
                        yaw,
                        pitch,
                        true)
                .send(fsPlayer);


        if (fsDisplay instanceof FS_TextDisplay textDisplay) {
            Component component;
            // use custom component provider if available
            if (data.getTraitTrait().isTraitAttached(CustomComponentProviderTrait.class)) {
                CustomComponentProviderTrait customComponentProvider = data.getTraitTrait().getTrait(CustomComponentProviderTrait.class);
                component = (customComponentProvider != null) ? customComponentProvider.getComponentForPlayer(player) : getShownText(player);
            } else {
                component = getShownText(player);
            }
            lastRenderedTextPerPlayer.put(player.getUniqueId(), component);
            textDisplay.setText(component);
        }

        FancySitula.ENTITY_FACTORY.setEntityDataFor(fsPlayer, fsDisplay);
    }

    @Override
    public void updateTextFor(@NotNull final Player player) {
        if (fsDisplay == null || !isViewer(player)) {
            return;
        }

        if (!data.getWorldName().equals(player.getLocation().getWorld().getName())) {
            this.viewers.remove(player.getUniqueId());
            this.viewersBehind.remove(player.getUniqueId());
            this.lastRenderedTextPerPlayer.remove(player.getUniqueId());
            return;
        }

        if (fsDisplay instanceof FS_TextDisplay textDisplay) {
            Component component;
            if (data.getTraitTrait().isTraitAttached(CustomComponentProviderTrait.class)) {
                CustomComponentProviderTrait customComponentProvider = data.getTraitTrait().getTrait(CustomComponentProviderTrait.class);
                component = (customComponentProvider != null) ? customComponentProvider.getComponentForPlayer(player) : getShownText(player);
            } else {
                component = getShownText(player);
            }

            // Optimization: avoid resending entity data packet if text hasn't changed
            Component prev = lastRenderedTextPerPlayer.get(player.getUniqueId());
            if (prev != null && prev.equals(component)) {
                return;
            }
            lastRenderedTextPerPlayer.put(player.getUniqueId(), component);

            textDisplay.setText(component);
            FS_RealPlayer fsPlayer = new FS_RealPlayer(player);
            FancySitula.ENTITY_FACTORY.setEntityDataFor(fsPlayer, fsDisplay);
        }
    }

    @Override
    public void clearTextCache() {
        super.clearTextCache();
        lastRenderedTextPerPlayer.clear();
    }

    public boolean isPlayerBehind(@NotNull final Player player) {
        final Location holoLoc = data.getLocation();
        final Location playerLoc = player.getLocation();
        if (!data.getWorldName().equals(playerLoc.getWorld().getName())) {
            return false;
        }

        double dx = playerLoc.getX() - holoLoc.getX();
        double dz = playerLoc.getZ() - holoLoc.getZ();

        double yawRad = Math.toRadians(holoLoc.getYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        double dot = dx * dirX + dz * dirZ;
        return dot < 0;
    }

    public void checkOrientationUpdate(@NotNull final Player player) {
        if (!(data instanceof DisplayHologramData displayData) || displayData.getBillboard() != Display.Billboard.FIXED) {
            return;
        }

        boolean isBehind = isPlayerBehind(player);
        boolean wasBehind = viewersBehind.contains(player.getUniqueId());

        if (isBehind != wasBehind) {
            if (isBehind) {
                viewersBehind.add(player.getUniqueId());
            } else {
                viewersBehind.remove(player.getUniqueId());
            }
            sendOrientationPacket(player, isBehind);
        }
    }

    private void sendOrientationPacket(@NotNull final Player player, boolean isBehind) {
        if (fsDisplay == null) {
            return;
        }

        FS_RealPlayer fsPlayer = new FS_RealPlayer(player);
        float baseYaw = data.getLocation().getYaw();
        float basePitch = data.getLocation().getPitch();
        float targetYaw = isBehind ? ((baseYaw + 180f) % 360f) : baseYaw;
        float targetPitch = isBehind ? -basePitch : basePitch;

        if (FancyHologramsPlugin.get().getFHConfiguration().isHologramRotationImprovementEnabled()) {
            HologramRotation rot = new HologramRotation(targetYaw + 180, targetPitch, 0);
            fsDisplay.setLeftRotation(rot.getQuaternion());
            FancySitula.ENTITY_FACTORY.setEntityDataFor(fsPlayer, fsDisplay);
        } else {
            FancySitula.PACKET_FACTORY.createTeleportEntityPacket(
                    fsDisplay.getId(),
                    data.getLocation().x(),
                    data.getLocation().y(),
                    data.getLocation().z(),
                    targetYaw,
                    targetPitch,
                    true
            ).send(fsPlayer);
        }
    }

    @Override
    public Object toJson() {
        return switch (data.getType()) {
            case TEXT -> JsonAdapter.toUnion((TextHologramData) data);
            case ITEM -> JsonAdapter.toUnion((ItemHologramData) data);
            case BLOCK -> JsonAdapter.toUnion((BlockHologramData) data);
        };
    }

    private void syncWithData() {
        syncWithData(false);
    }

    private void syncWithData(boolean isBehind) {
        if (fsDisplay == null) {
            return;
        }

        // location data
        final Location location = data.getLocation();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        if (isBehind) {
            yaw = (yaw + 180f) % 360f;
            pitch = -pitch;
        }

        if (FancyHologramsPlugin.get().getFHConfiguration().isHologramRotationImprovementEnabled()) {
            fsDisplay.setLocation(location.clone().setRotation(0, 0));

            // set yaw and pitch
            HologramRotation rot = new HologramRotation(yaw + 180, pitch, 0);
            fsDisplay.setLeftRotation(rot.getQuaternion());
        } else {
            Location loc = location.clone();
            loc.setYaw(yaw);
            loc.setPitch(pitch);
            fsDisplay.setLocation(loc);
        }


        if (fsDisplay instanceof FS_TextDisplay textDisplay && data instanceof com.fancyinnovations.fancyholograms.api.data.TextHologramData textData) {
            // line width
            textDisplay.setLineWidth(Hologram.LINE_WIDTH);

            // opacity
            textDisplay.setTextOpacity(textData.getTextOpacity());

            // background
            final var background = textData.getBackground();
            if (background == null) {
                textDisplay.setBackground(1073741824); // default background
            } else if (background == Hologram.TRANSPARENT) {
                textDisplay.setBackground(0);
            } else {
                textDisplay.setBackground(background.asARGB());
            }

            textDisplay.setStyleFlags((byte) 0);
            textDisplay.setShadow(textData.hasTextShadow());
            textDisplay.setSeeThrough(textData.isSeeThrough());

            switch (textData.getTextAlignment()) {
                case LEFT -> textDisplay.setAlignLeft(true);
                case RIGHT -> textDisplay.setAlignRight(true);
                case CENTER -> {
                    textDisplay.setAlignLeft(false);
                    textDisplay.setAlignRight(false);
                }
            }
        } else if (fsDisplay instanceof FS_ItemDisplay itemDisplay && data instanceof com.fancyinnovations.fancyholograms.api.data.ItemHologramData itemData) {
            // item
            itemDisplay.setItem(itemData.getItemStack());
        } else if (fsDisplay instanceof FS_BlockDisplay blockDisplay && data instanceof com.fancyinnovations.fancyholograms.api.data.BlockHologramData blockData) {
            // block

//            BlockType blockType = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK).get(blockData.getBlock().getKey());
            if (blockData.getBlockData() != null) {
                blockDisplay.setBlock(blockData.getBlockData().createBlockState());
            }
        }

        if (data instanceof com.fancyinnovations.fancyholograms.api.data.DisplayHologramData displayData) {
            // interpolation
            fsDisplay.setTransformationInterpolationDuration(displayData.getInterpolationDuration());
            fsDisplay.setTransformationInterpolationStartDeltaTicks(0);

            // billboard data
            fsDisplay.setBillboard(FS_Display.Billboard.valueOf(displayData.getBillboard().name()));

            // brightness
            if (displayData.getBrightness() != null) {
                fsDisplay.setBrightnessOverride(displayData.getBrightness().getBlockLight() << 4 | displayData.getBrightness().getSkyLight() << 20);
            }

            // entity transformation
            fsDisplay.setTranslation(displayData.getTranslation());
            fsDisplay.setScale(displayData.getScale());
            if (!FancyHologramsPlugin.get().getFHConfiguration().isHologramRotationImprovementEnabled()) {
                fsDisplay.setLeftRotation(new Quaternionf());
                fsDisplay.setRightRotation(new Quaternionf());
            }

            // entity shadow
            fsDisplay.setShadowRadius(displayData.getShadowRadius());
            fsDisplay.setShadowStrength(displayData.getShadowStrength());

            // glowing
            if (displayData.getGlowingColor() != null && displayData.getGlowingColor() != de.oliver.fancylib.colors.GlowingColor.DISABLED && displayData.getGlowingColor().getColor() != null) {
                byte currentFlags = 0;
                try {
                    currentFlags = fsDisplay.getSharedFlags();
                } catch (NullPointerException e) {

                }
                fsDisplay.setSharedFlags((byte) (currentFlags | 0x40));

                fsDisplay.setGlowColorOverride(displayData.getGlowingColor().getColor().value());
            } else {
                try {
                    byte currentFlags = fsDisplay.getSharedFlags();
                    fsDisplay.setSharedFlags((byte) (currentFlags & ~0x40));
                } catch (NullPointerException e) {

                }

                fsDisplay.setGlowColorOverride(-1);
            }

            fsDisplay.setViewRange(displayData.getVisibilityDistance());
        }
    }

}
