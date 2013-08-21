package net.dmulloy2.autocraft.listeners;

import net.dmulloy2.autocraft.AutoCraft;
import net.dmulloy2.autocraft.util.FormatUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

public class PlayerListener implements Listener {
	private final AutoCraft plugin;
	
	public PlayerListener(AutoCraft plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		onPlayerDisconnect(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerKick(PlayerKickEvent event) {
		if (! event.isCancelled()) {
			onPlayerDisconnect(event.getPlayer());
		}
	}
	
	public void onPlayerDisconnect(Player player) {
		plugin.getShipManager().ships.remove(player.getName());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (plugin.getShipManager().ships.containsKey(player.getName())) {
			if (! plugin.getShipManager().ships.get(player.getName()).isPassenger(player)) {
				plugin.getShipManager().ships.remove(player.getName());
				player.sendMessage(plugin.getPrefix() +
						FormatUtil.format("&7You have stepped off your ship, you have been unpiloted."));
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
			Player player = event.getPlayer();
			if (plugin.getShipManager().ships.containsKey(player.getName())) {
				Vector dir = player.getLocation().getDirection();
				
				if (event.hasBlock()) {
					Material clickedType = event.getClickedBlock().getType();
					if (action == Action.RIGHT_CLICK_BLOCK && (
							clickedType == Material.DISPENSER
							|| clickedType == Material.CHEST
							|| clickedType == Material.FURNACE
							|| clickedType == Material.LEVER
							|| clickedType == Material.STONE_BUTTON
							|| clickedType == Material.WOOD_BUTTON
							|| clickedType == Material.WORKBENCH)) {
							return;
					}
				}
				
				plugin.getShipManager().ships.get(player.getName()).move(
						(int) Math.round(dir.getX()), 
						(int) Math.round(dir.getY()), 
						(int) Math.round(dir.getZ()));
				
				event.setCancelled(true);
			}
		}
	}
}