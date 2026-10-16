package de.oliver.fancynpcs.tracker;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VisibilityTracker implements Runnable {

    private final Set<UUID> joinDelayPlayers;

    public VisibilityTracker() {
        this.joinDelayPlayers = ConcurrentHashMap.newKeySet();
    }

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

        for (Player player : onlinePlayers) {
            if (joinDelayPlayers.contains(player.getUniqueId())) {
                continue;
            }

            for (Npc npc : npcs) {
                npc.checkAndUpdateVisibilityDirect(player);
            }
        }
    }

    public void addJoinDelayPlayer(UUID player) {
        joinDelayPlayers.add(player);
    }

    public void removeJoinDelayPlayer(UUID player) {
        joinDelayPlayers.remove(player);
    }
}
