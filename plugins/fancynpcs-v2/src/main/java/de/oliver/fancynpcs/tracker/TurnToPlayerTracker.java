package de.oliver.fancynpcs.tracker;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.events.NpcStartLookingEvent;
import de.oliver.fancynpcs.api.events.NpcStopLookingEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;

public class TurnToPlayerTracker implements Runnable {

    @Override
    public void run() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            return;
        }

        Collection<Npc> npcs = FancyNpcs.getInstance().getNpcManagerImpl().getAllNpcs();
        if (npcs.isEmpty()) {
            return;
        }

        int defaultTurnToPlayerDistance = FancyNpcs.getInstance().getFancyNpcConfig().getTurnToPlayerDistance();
        int defaultTurnDistSq = defaultTurnToPlayerDistance * defaultTurnToPlayerDistance;

        for (Player player : onlinePlayers) {
            Location playerLocation = null;

            for (Npc npc : npcs) {
                NpcData npcData = npc.getData();
                if (!npcData.isTurnToPlayer()) {
                    continue;
                }

                Location npcLocation = npcData.getLocation();
                if (npcLocation == null || player.getWorld() != npcLocation.getWorld()) {
                    continue;
                }

                if (playerLocation == null) {
                    playerLocation = player.getLocation();
                }

                double dx = playerLocation.getX() - npcLocation.getX();
                double dy = playerLocation.getY() - npcLocation.getY();
                double dz = playerLocation.getZ() - npcLocation.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;

                // Get NPC-specific turn distance or fall back to default
                int npcTurnDistance = npcData.getTurnToPlayerDistance();
                int effectiveTurnDistSq = (npcTurnDistance == -1) ? defaultTurnDistSq : (npcTurnDistance * npcTurnDistance);

                if (distSq < effectiveTurnDistSq) {
                    // Calculate the base eye height for the entity type
                    double baseEyeHeight = getEntityEyeHeight(npcData.getType());
                    double npcEyeY = npcLocation.getY() + (baseEyeHeight * npcData.getScale());
                    double playerEyeY = playerLocation.getY() + player.getEyeHeight();

                    double eyeDx = dx;
                    double eyeDy = playerEyeY - npcEyeY;
                    double eyeDz = dz;

                    float yaw;
                    float pitch;
                    if (eyeDx == 0 && eyeDz == 0) {
                        yaw = 0.0f;
                        pitch = eyeDy > 0 ? -90.0f : 90.0f;
                    } else {
                        double theta = Math.atan2(-eyeDx, eyeDz);
                        yaw = (float) Math.toDegrees((theta + 2 * Math.PI) % (2 * Math.PI));
                        double xz = Math.sqrt(eyeDx * eyeDx + eyeDz * eyeDz);
                        pitch = (float) Math.toDegrees(Math.atan2(-eyeDy, xz));
                    }

                    npc.lookAt(player, new Location(null, 0, 0, 0, yaw, pitch));
                    // Setting NPC to be looking at the player and getting the value previously stored (or not) inside a map.
                    Boolean wasPreviouslyLooking = npc.getIsLookingAtPlayer().put(player.getUniqueId(), true);
                    // Comparing the previous state with current state to prevent event from being called continuously.
                    if (wasPreviouslyLooking == null || !wasPreviouslyLooking) {
                        // Calling NpcStartLookingEvent from the main thread.
                        FancyNpcs.getInstance().getScheduler().runTask(null, () -> {
                            new NpcStartLookingEvent(npc, player).callEvent();
                        });
                    }
                    // Updating state if changed.
                } else if (npcData.isTurnToPlayer() && npc.getIsLookingAtPlayer().getOrDefault(player.getUniqueId(), false)) {
                    npc.getIsLookingAtPlayer().put(player.getUniqueId(), false);
                    // Resetting to initial direction, if configured.
                    if (FancyNpcs.getInstance().getFancyNpcConfig().isTurnToPlayerResetToInitialDirection()) {
                        npc.move(player, false);
                    }
                    // Calling NpcStopLookingEvent from the main thread.
                    FancyNpcs.getInstance().getScheduler().runTask(null, () -> {
                        new NpcStopLookingEvent(npc, player).callEvent();
                    });
                }
            }
        }
    }

    /**
     * Gets the base eye height for different entity types.
     * Based on Minecraft's default entity eye heights.
     *
     * @param type The entity type
     * @return The base eye height in blocks
     */
    private double getEntityEyeHeight(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case PLAYER -> 1.62;
            case ZOMBIE, SKELETON, STRAY, HUSK, DROWNED, WITHER_SKELETON -> 1.74;
            case CREEPER -> 1.7;
            case ENDERMAN -> 2.55;
            case SPIDER, CAVE_SPIDER -> 0.5;
            case PIG -> 0.6;
            case SHEEP -> 0.65;
            case COW, MOOSHROOM -> 1.3;
            case CHICKEN -> 0.4;
            case HORSE, DONKEY, MULE -> 1.52;
            case VILLAGER, ZOMBIE_VILLAGER -> 1.62;
            case IRON_GOLEM -> 2.7;
            case WOLF -> 0.68;
            case CAT, OCELOT -> 0.35;
            case RABBIT -> 0.3;
            case BAT -> 0.45;
            case SQUID, GLOW_SQUID -> 0.4;
            case SILVERFISH -> 0.13;
            case ENDERMITE -> 0.13;
            case BLAZE -> 1.7;
            case GHAST -> 2.0;
            case SLIME, MAGMA_CUBE -> 0.5;
            case WITCH -> 1.62;
            case EVOKER, VINDICATOR, ILLUSIONER, PILLAGER -> 1.62;
            case VEX -> 0.8;
            case GUARDIAN, ELDER_GUARDIAN -> 0.425;
            case SHULKER -> 0.5;
            case PHANTOM -> 0.5;
            case BEE -> 0.3;
            case FOX -> 0.4;
            case PANDA -> 1.13;
            case STRIDER -> 1.7;
            case HOGLIN, ZOGLIN -> 1.4;
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN -> 1.62;
            case AXOLOTL -> 0.3;
            case GOAT -> 0.9;
            case ALLAY -> 0.6;
            case FROG -> 0.25;
            case TADPOLE -> 0.13;
            case WARDEN -> 2.5;
            case CAMEL -> 2.275;
            case SNIFFER -> 1.0;
            case BREEZE -> 1.4;
            case ARMADILLO -> 0.26;
            case BOGGED -> 1.74;
            case ARMOR_STAND -> 1.975;
            case TEXT_DISPLAY, ITEM_DISPLAY, BLOCK_DISPLAY, INTERACTION -> 0.5;
            default -> 1.62;
        };
    }
}
