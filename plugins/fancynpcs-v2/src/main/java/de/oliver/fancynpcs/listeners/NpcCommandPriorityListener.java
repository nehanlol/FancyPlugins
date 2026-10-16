package de.oliver.fancynpcs.listeners;

import de.oliver.fancynpcs.FancyNpcs;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;

public class NpcCommandPriorityListener implements Listener {

    private final @NotNull FancyNpcs plugin;

    public NpcCommandPriorityListener(final @NotNull FancyNpcs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandLowest(final @NotNull PlayerCommandPreprocessEvent event) {
        redirectPlayerCommand(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandHighest(final @NotNull PlayerCommandPreprocessEvent event) {
        redirectPlayerCommand(event);
    }

    private void redirectPlayerCommand(final @NotNull PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null) {
            return;
        }

        if (msg.equalsIgnoreCase("/npc") || msg.toLowerCase().startsWith("/npc ")) {
            event.setMessage("/fancynpcs:npc" + msg.substring(4));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommandLowest(final @NotNull ServerCommandEvent event) {
        redirectServerCommand(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommandHighest(final @NotNull ServerCommandEvent event) {
        redirectServerCommand(event);
    }

    private void redirectServerCommand(final @NotNull ServerCommandEvent event) {
        String cmd = event.getCommand();
        if (cmd == null) {
            return;
        }

        if (cmd.equalsIgnoreCase("npc") || cmd.toLowerCase().startsWith("npc ")) {
            event.setCommand("fancynpcs:npc" + cmd.substring(3));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(final @NotNull ServerLoadEvent event) {
        plugin.ensureNpcCommandPriority();
    }
}
