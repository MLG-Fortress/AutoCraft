package com.minesworn.autocraft.commands;

import org.bukkit.ChatColor;

import com.minesworn.autocraft.Autocraft;
import com.minesworn.autocraft.ships.ACBaseShip;

public class CmdPilot extends ACCommand {

	public CmdPilot() {
		this.name = "pilot";
		this.description = "Use to pilot ships";
		this.mustBePlayer = true;
		this.requiredArgs.add("ship type");
		this.aliases.add("p");
	}
	
	@Override
	public void perform() {
		if (Autocraft.shipmanager.ships.containsKey(player.getName()))
			errorMessage("You are already piloting a ship, please type " + ChatColor.WHITE + "/ac dismount");
		
		if (Autocraft.propertiesmanager.getACProperties(args[0]) != null) {
			new ACBaseShip(player, Autocraft.propertiesmanager.getACProperties(args[0]));
		}
	}
}
