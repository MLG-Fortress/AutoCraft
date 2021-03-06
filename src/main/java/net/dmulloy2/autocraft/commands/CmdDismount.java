package net.dmulloy2.autocraft.commands;

import net.dmulloy2.autocraft.AutoCraft;
import net.dmulloy2.autocraft.types.Permission;

/**
 * @author dmulloy2
 */

public class CmdDismount extends AutoCraftCommand {

	public CmdDismount(AutoCraft plugin) {
		super(plugin);
		this.name = "dismount";
		this.aliases.add("x");
		this.description = "Dismount your airship.";
		this.permission = Permission.CMD_DISMOUNT;
		this.mustBePlayer = true;
	}

	@Override
	public void perform() {
		if (! isPiloting()) {
			err("You must be piloting a ship to do this!");
			return;
		}

		plugin.getShipHandler().unpilotShip(player);
		sendpMessage("&7You have stopped piloting this ship.");
	}
}