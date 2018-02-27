package net.dmulloy2.autocraft.ship;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.dmulloy2.autocraft.AutoCraft;
import net.dmulloy2.autocraft.Config;
import net.dmulloy2.autocraft.tasks.AutoPilotTask;
import net.dmulloy2.autocraft.tasks.SinkingTask;
import net.dmulloy2.autocraft.types.BlockData;
import net.dmulloy2.autocraft.types.Direction;
import net.dmulloy2.autocraft.types.RelativePosition;
import net.dmulloy2.autocraft.types.TurnDirection;
import net.dmulloy2.autocraft.weapons.Napalm;
import net.dmulloy2.autocraft.weapons.Torpedo;
import net.dmulloy2.util.FormatUtil;
import net.dmulloy2.util.MaterialUtil;
import net.dmulloy2.util.Util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.block.Jukebox;
import org.bukkit.block.NoteBlock;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Bed;
import org.bukkit.material.Diode;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PressurePlate;
import org.bukkit.material.Rails;
import org.bukkit.material.RedstoneWire;
import org.bukkit.material.SimpleAttachableMaterialData;
import org.bukkit.material.Stairs;
import org.bukkit.material.Tree;
import org.bukkit.material.Vine;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * @author dmulloy2
 */

public class Ship {
	private boolean isSinking = false;
	private boolean autoPilot = false;

	private ShipData data;

	// Changes to true if the ship has a problem creating.
	private boolean creationFailed = false;
	private Player player;

	// Used for checking if weapon cooldown is over.
	private long lastFired;

	// Total number of the main block on the ship.
	private int numMainBlocks = 0;

	// Used for checking if move cooldown is over.
	private long lastmove;

	// Hold all blocks part of this ship.
	private Block[] blocks;
	private Block[] specialBlocks;

	private BlockData[] largeShipBlocks;
	private BlockData[] largeShipSpecialBlocks;

	// Place holder for the block the player is standing on.
	private Block mainblock;

	private final AutoCraft plugin;

	public Ship(Player player, ShipData data, AutoCraft plugin) {
		this.player = player;
		this.data = data;
		this.plugin = plugin;

		plugin.getLogHandler().debug("{0} is attempting to fly {1} at: {2}", player.getName(), data.getShipType(),
				Util.locationToString(player.getLocation()));
		startShip();
	}

	// Starts the sinking process
	public void startSinking() {
		this.player = null;
		this.isSinking = true;
		new SinkingTask(this).runTaskTimer(plugin, 0, Config.sinkingInterval);
	}

	public boolean isSinking() {
		return isSinking;
	}

	public void startAutoPilot() {
		if (! autoPilot) {
			this.autoPilot = true;
			new AutoPilotTask(this).runTaskTimer(plugin, 0, Config.autoPilotInterval);
		}
	}

	public void stopAutoPilot() {
		this.autoPilot = false;
	}

	public boolean isAutoPilot() {
		return autoPilot;
	}

	public boolean creationFailed() {
		return creationFailed;
	}

	// Drop tnt bombs :D
	public void drop() {
		// Has player waited cooldown before trying to fire again?
		if ((System.currentTimeMillis() - lastFired) > (Config.weaponCooldownTime)) {
			plugin.getLogHandler().log("{0} is attempting to drop a bomb.", player.getName());
			// Can this airship drop bombs?
			if (data.isDropsBomb()) {
				if (plugin.isSwornNationsEnabled() &&
						! plugin.getSwornNationsHandler().canUseWeapon(player)) {
					return;
				}

				sendMessage("&6Attempting to drop TNT...");

				Block[] cannons = getCannons();
				int numfiredcannons = 0;
				for (int i = 0; i < cannons.length; i++) {
					if (cannons[i] != null && cannons[i].getRelative(0, -1, 0).getType().equals(Material.AIR)
							&& cannonHasTnt(cannons[i], Config.numTntToDropBomb)) {
						if (numfiredcannons < data.getMaxNumberOfCannons()) {
							numfiredcannons++;
							lastFired = System.currentTimeMillis();
							withdrawTnt(cannons[i], Config.numTntToDropBomb);

							// Spawn primed tnt firing downwards
							TNTPrimed tnt = cannons[i].getWorld().spawn(cannons[i].getLocation().clone().add(0, -1, 0), TNTPrimed.class);
							tnt.setVelocity(new Vector(0, -0.5, 0));

							// TODO: Sound effects for firing
						} else {
							// More cannons on ship than allowed. Not all fired
							// - can break out of loop now.
							sendMessage("&6Some cannons did not fire. Max cannon limit is: &b{0}", data.getMaxNumberOfCannons());
							break;
						}
					}
				}
			} else {
				sendMessage("&b{0} &6cannot drop TNT bombs!", data.getShipType());
			}
		} else {
			sendMessage("&6Cooling down for &b{0} &6more seconds", (Math.round(6 - (System.currentTimeMillis() - lastFired) / 1000)));
		}
	}

	// Fire tnt like a cannon :3
	public void fire() {
		// Has player waited cooldown before trying to fire again?
		if ((System.currentTimeMillis() - lastFired) > (Config.weaponCooldownTime)) {
			plugin.getLogHandler().log("{0} is attempting to fire TNT.", player.getName());
			// Can this airship drop bombs?
			if (data.isFiresTnt()) {
				if (plugin.isSwornNationsEnabled() &&
						! plugin.getSwornNationsHandler().canUseWeapon(player)) {
					return;
				}

				sendMessage("&6Attempting to fire TNT...");

				Block[] cannons = getCannons();
				int numfiredcannons = 0;
				for (int i = 0; i < cannons.length; i++) {
					if (cannons[i] != null && cannonHasTnt(cannons[i], Config.numTntToFireNormal)) {
						double dist = 0.4;
						BlockFace face = ((Directional) cannons[i].getState().getData()).getFacing();
						int x = face.getModX();
						int z = face.getModZ();
						dist *= getCannonLength(cannons[i], -x, -z);

						if (cannons[i].getRelative(x, 0, z).getType().equals(Material.AIR)) {
							if (numfiredcannons < data.getMaxNumberOfCannons()) {
								numfiredcannons++;
								lastFired = System.currentTimeMillis();
								withdrawTnt(cannons[i], Config.numTntToFireNormal);

								// Spawn primed tnt firing in the direction of
								// the dispenser
								TNTPrimed tnt = cannons[i].getWorld().spawn(cannons[i].getLocation().clone().add(x, 0, z), TNTPrimed.class);
								tnt.setVelocity(new Vector(x * dist, 0.5, z * dist));

								// TODO: Sound effects for firing
							} else {
								// More cannons on ship than allowed. Not all
								// fired - can break out of loop now.
								sendMessage("&6Some cannons did not fire. Max cannon limit is &b{0}", data.getMaxNumberOfCannons());
								break;
							}
						}
					}
				}
			} else {
				sendMessage("&b{0} &6cannot fire TNT!", data.getShipType());
			}
		} else {
			sendMessage("&6Cooling down for &b{0} &6more seconds!", (Math.round(6 - (System.currentTimeMillis() - lastFired) / 1000)));
		}
	}

	// Drop some napalms ;o
	public void dropNapalm() {
		// Has player waited cooldown before trying to fire again?
		if ((System.currentTimeMillis() - lastFired) > (Config.weaponCooldownTime)) {
			plugin.getLogHandler().log("{0} is attempting to drop napalm.", player.getName());
			// Can this airship drop napalm?
			if (data.isDropsNapalm()) {
				if (plugin.isSwornNationsEnabled() &&
						! plugin.getSwornNationsHandler().canUseWeapon(player)) {
					return;
				}

				sendMessage("&6Attempting to drop napalm...");

				Block[] cannons = getCannons();
				int numfiredcannons = 0;
				for (int i = 0; i < cannons.length; i++) {
					Block cannon = cannons[i];
					if (cannon != null && cannon.getRelative(0, -1, 0).getType().equals(Material.AIR)
							&& cannonHasTnt(cannon, Config.numTntToDropNapalm)) {
						Material missingMaterial = null;
						for (Material mat : Config.materialsNeededForNapalm) {
							if (! cannonHasItem(cannon, mat, 1)) {
								missingMaterial = mat;
								break;
							}
						}

						if (missingMaterial != null) {
							sendMessage("&6Cannon &b{0} &6is missing &b{1}&6!", i, FormatUtil.getFriendlyName(missingMaterial));
							continue;
						}

						if (numfiredcannons < data.getMaxNumberOfCannons()) {
							numfiredcannons++;
							lastFired = System.currentTimeMillis();
							withdrawTnt(cannon, Config.numTntToDropNapalm);
							for (Material mat : Config.materialsNeededForNapalm) {
								withdrawItem(cannon, mat, 1);
							}
							// Fire some napalms
							new Napalm(plugin, cannons[i]);

							// TODO: Sound effects for drop
						} else {
							// More cannons on ship than allowed. Not all fired
							// - can break out of loop now.
							sendMessage("&6Some napalm cannons did not fire. Max cannon limit is &b{0}", data.getMaxNumberOfCannons());
							break;
						}
					}
				}
			} else {
				sendMessage("&b{0} &6cannot drop napalm!", data.getShipType());
			}
		} else {
			sendMessage("&6Cooling down for &b{0} &6more seconds!", (Math.round(6 - (System.currentTimeMillis() - lastFired) / 1000)));
		}
	}

	// Fire some torpedoes c:
	public void fireTorpedo() {
		// Has player waited cooldown before trying to fire again?
		if ((System.currentTimeMillis() - lastFired) > (Config.weaponCooldownTime)) {
			plugin.getLogHandler().log("{0} is attempting to fire a torpedo", player.getName());
			// Can this airship fire torpedoes?
			if (data.isFiresTorpedo()) {
				if (plugin.isSwornNationsEnabled() &&
						! plugin.getSwornNationsHandler().canUseWeapon(player)) {
					return;
				}

				sendMessage("&6Attempting to fire a torpedo...");

				Block[] cannons = getCannons();
				int numfiredcannons = 0;
				for (int i = 0; i < cannons.length; i++) {
					Block cannon = cannons[i];
					if (cannon != null && cannonHasTnt(cannon, Config.numTntToFireTorpedo)) {
						BlockFace face = ((Directional) cannon.getState().getData()).getFacing();

						Material missingMaterial = null;
						for (Material mat : Config.materialsNeededForTorpedo) {
							if (! cannonHasItem(cannon, mat, 1)) {
								missingMaterial = mat;
								break;
							}
						}

						if (missingMaterial != null) {
							sendMessage("&6Cannon &b{0} &6is missing &b{1}&6!", i, FormatUtil.getFriendlyName(missingMaterial));
							continue;
						}

						if (! cannon.getRelative(face.getModX(), 0, face.getModZ()).getType().equals(Material.AIR)) {
							continue;
						}

						if (numfiredcannons < data.getMaxNumberOfCannons()) {
							numfiredcannons++;
							lastFired = System.currentTimeMillis();
							withdrawTnt(cannon, Config.numTntToFireTorpedo);
							for (Material mat : Config.materialsNeededForTorpedo) {
								withdrawItem(cannon, mat, 1);
							}

							// Fire some torpedoes
							new Torpedo(plugin, cannons[i], face);

							// TODO: Sound effects for firing
						} else {
							// More cannons on ship than allowed. Not all fired
							// - can break out of loop now.
							sendMessage("&6Some cannons did not fire. Max cannon limit is &b{0}", data.getMaxNumberOfCannons());
							break;
						}
					}
				}
			} else {
				sendMessage("&b{0} &6cannot fire torpedos!", data.getShipType());
			}
		} else {
			sendMessage("&6Cooling down for &b{0} &6more seconds!", (Math.round(6 - (System.currentTimeMillis() - lastFired) / 1000)));
		}
	}

	// Returns the length of cannon material behind the dispenser or maxCannonLength
	public double getCannonLength(Block b, int x, int z) {
		double ret = 1.0;
		for (int i = 1; i <= data.getMaxCannonLength(); i++) {
			Block bnext = b.getRelative(x * i, 0, z * i);
			if (bnext.getType() == MaterialUtil.getMaterial(data.getCannonMaterial())) {
				ret++;
			} else {
				break;
			}
		}

		return ret;
	}

	public boolean cannonHasTnt(Block b, int numtnt) {
		return cannonHasItem(b, Material.TNT, numtnt);
	}

	// Check if dispenser has any number of item. Only send dispenser block types here.
	public boolean cannonHasItem(Block b, Material mat, int num) {
		Dispenser dispenser = (Dispenser) b.getState();
		if (dispenser.getInventory() != null) {
			for (ItemStack item : dispenser.getInventory().getContents()) {
				if (item != null && item.getType() == mat) {
					if (item.getAmount() >= num) {
						num = 0;
					} else {
						num = num - item.getAmount();
					}
				}

				if (num <= 0) {
					return true;
				}
			}
		}

		return false;
	}

	public void withdrawTnt(Block b, int numtnt) {
		withdrawItem(b, Material.TNT, numtnt);
	}

	// Withdraw any item out of a dispenser. Check if dispenser has enough of
	// item first and only send Dispenser blocks here.
	public void withdrawItem(Block b, Material mat, int num) {
		Dispenser dispenser = (Dispenser) b.getState();
		if (dispenser.getInventory() != null) {
			for (int i = 0; i < dispenser.getInventory().getSize(); i++) {
				ItemStack item = dispenser.getInventory().getItem(i);
				if (item != null && item.getType() == mat) {
					if (item.getAmount() >= num) {
						if (item.getAmount() - num > 0) {
							item.setAmount(item.getAmount() - num);
						} else {
							dispenser.getInventory().setItem(i, null);
						}

						num = 0;
					} else {
						num = num - item.getAmount();
						item.setAmount(0);
					}
				}

				if (num <= 0) {
					return;
				}
			}
		}
	}

	// Returns all dispensers on the ship.
	public Block[] getCannons() {
		List<Block> cannons = new ArrayList<Block>();
		for (int i = 0; i < blocks.length; i++) {
			if (blocks[i].getType().equals(Material.DISPENSER))
				cannons.add(blocks[i]);
		}

		return cannons.toArray(new Block[0]);
	}

	public void startShip() {
		updateMainBlock();
		// Move recursion async
		new BukkitRunnable() {

			@Override
			public void run() {
				if (beginRecursion(mainblock)) {
					// Return to sync
					new BukkitRunnable() {

						@Override
						public void run() {
							if (areBlocksValid()) {
								if (isShipAlreadyPiloted()) {
									sendMessage("&cThis ship is already being piloted");
								} else {
									pilot();
								}
							}
						}

					}.runTask(plugin);
				}
			}

		}.runTaskAsynchronously(plugin);
	}

	public void pilot() {
		// Register the ship
		plugin.getShipHandler().putShip(player, this);

		// Tell the player they're in control
		sendMessage("&7You are in control of this ship");
		sendMessage("&7Use the right mouse to guide the ship");

		// Log it (only if it was successful)
		plugin.getLogHandler().log("{0} has started flying {1} at: {2}, {3}, {4} in world {5}", player.getName(),
				data.getShipType(), player.getLocation().getBlockX(), player.getLocation().getBlockY(),
				player.getLocation().getBlockZ(), player.getWorld().getName());

	}

	// This method doesn't actually move the ship, only prepares to before it
	// calls domove().
	public void move(int dx, int dy, int dz) {
		// If the airship has been stopped then remove it from the mapping.
		if (creationFailed) {
			plugin.getShipHandler().unpilotShip(player);
		} else {
			// Check that the ship hasn't already moved within the last second.
			if (System.currentTimeMillis() - lastmove > 1000L) {
				// Reduce the matrix given to identities only.
				if (Math.abs(dx) > 1)
					dx /= Math.abs(dx);
				if (Math.abs(dy) > 1)
					dy /= Math.abs(dy);
				if (Math.abs(dz) > 1)
					dz /= Math.abs(dz);

				if (System.currentTimeMillis() - lastmove < 1500L) {
					dx *= data.getMoveSpeed();
					dy *= data.getMoveSpeed();
					dz *= data.getMoveSpeed();
				}

				// Set last move to current time.
				this.lastmove = System.currentTimeMillis();
				boolean obstruction = false;

				Block[] blocks = this.blocks.clone();
				Block[] specialBlocks = this.specialBlocks.clone();

				// Check each block's new position for obstructions
				for (int i = 0; i < blocks.length; i++) {
					Block block = blocks[i].getRelative(dx, dy, dz);
					if (block.getLocation().getBlockY() + dy > data.getMaxAltitude()
							|| block.getLocation().getBlockY() + dy < data.getMinAltitude())
						obstruction = true;
					if (block.getType().equals(Material.AIR) || block.getType().equals(Material.SNOW) || blockBelongsToShip(block, blocks)
							|| blockBelongsToShip(block, specialBlocks))
						continue;
					obstruction = true;
				}

				// Can't move :/
				if (obstruction) {
					sendMessage("&eObstruction - &cCannot move any further in this direction.");
					isSinking = false; // Stop the ship from sinking
					autoPilot = false; // Disengage autopilot
					// TODO Move backwards?
				} else {
					// Lets move this thing :D
					doMove(dx, dy, dz);
				}
			}
		}
	}

	public void rotate(TurnDirection dir) {
		if (creationFailed) {
			plugin.getShipHandler().unpilotShip(player);
		} else {
			// Check that the ship hasn't moved within the last second.
			if (System.currentTimeMillis() - lastmove > 1000L) {
				this.lastmove = System.currentTimeMillis();
				boolean obstruction = false;

				Block[] blocks = this.blocks.clone();
				Block[] specialBlocks = this.specialBlocks.clone();

				updateMainBlock();
				// Check each block's new position for obstructions
				for (int i = 0; i < blocks.length; i++) {
					Vector v = getRotationVector(blocks[i].getLocation(), mainblock, dir);
					Block block = mainblock.getRelative(v.getBlockX(), v.getBlockY(), v.getBlockZ());
					if (block.getType().equals(Material.AIR)
							|| block.getType().equals(Material.SNOW)
							|| blockBelongsToShip(block, blocks)
							|| blockBelongsToShip(block, specialBlocks))
						continue;
					obstruction = true;
				}

				// Can't move :/
				if (obstruction) {
					sendMessage("&eObstruction - &cCannot move any further in this direction.");
				} else {
					doRotate(dir);
				}
			}
		}
	}

	// Rotate the ship and all passengers in the specified direction
	public void doRotate(TurnDirection dir) {
		List<Player> passengers = getPassengers();
		BlockData[] temp = new BlockData[blocks.length];
		BlockData[] special = new BlockData[blocks.length];

		plugin.getLogHandler().debug("Rotating ship {0}, dir = {1}", this, dir);

		for (int i = 0; i < specialBlocks.length; i++) {
			Block b = specialBlocks[i];

			// Store in blocks array
			special[i] = new BlockData(b);

			// Clear inventory (if applicable)
			BlockState state = b.getState();
			if (state instanceof InventoryHolder) {
				((InventoryHolder) state).getInventory().clear();
			}

			// Reset
			b.setType(Material.AIR);
		}

		// First remove all blocks from the scene
		for (int i = 0; i < blocks.length; i++) {
			Block b = blocks[i];

			// Store in blocks aray
			temp[i] = new BlockData(b);

			// Clear inventory (if applicable)
			BlockState state = b.getState();
			if (state instanceof InventoryHolder) {
				((InventoryHolder) state).getInventory().clear();
			}

			// Reset
			b.setType(Material.AIR);
		}

		updateMainBlock();

		// Make new blocks in their new respective positions
		for (int i = 0; i < blocks.length; i++) {
			// blocks[i].getLocation().distance(mainblock.getLocation());
			Vector v = getRotationVector(blocks[i].getLocation(), mainblock, dir);
			blocks[i] = mainblock.getRelative(v.getBlockX(), v.getBlockY(), v.getBlockZ());
			setBlock(blocks[i], temp[i], dir);
		}

		for (int i = 0; i < specialBlocks.length; i++) {
			Vector v = getRotationVector(specialBlocks[i].getLocation(), mainblock, dir);
			specialBlocks[i] = mainblock.getRelative(v.getBlockX(), v.getBlockY(), v.getBlockZ());
			setBlock(specialBlocks[i], special[i], dir);
		}

		for (Player p : passengers) {
			Location l = p.getLocation().clone().add(getRotationVector(p.getLocation(), mainblock, dir).toLocation(p.getWorld()));
			l.setYaw(l.getYaw() + ((dir == TurnDirection.LEFT) ? -90 : 90));
			p.teleport(l);
		}
	}

	// Returns the relative position from the main block that this vector is at
	// once rotated
	public Vector getRotationVector(Location l, Block main, TurnDirection dir) {
		Location n = l.clone().subtract(main.getLocation());
		int dz = n.getBlockX();
		int dx = n.getBlockZ();

		if (dir == TurnDirection.RIGHT)
			dx *= -1;
		else
			dz *= -1;

		return new Vector(dx, n.getBlockY(), dz);
	}

	// Move the ship and all passengers the specified distance
	public void doMove(int dx, int dy, int dz) {
		List<Player> passengers = getPassengers();

		boolean fastFly = data.getFastFlyAtSize() != 0 && data.getFastFlyAtSize() < (blocks.length + specialBlocks.length);
		if (fastFly) {
			if (largeShipSpecialBlocks == null || largeShipBlocks == null) {
				largeShipSpecialBlocks = new BlockData[specialBlocks.length];
				largeShipBlocks = new BlockData[blocks.length];

				// First remove all special blocks from the world
				for (int i = 0; i < specialBlocks.length; i++) {
					Block b = specialBlocks[i];

					// Clear inventory (if applicable)
					BlockState state = b.getState();
					if (state instanceof InventoryHolder) {
						((InventoryHolder) state).getInventory().clear();
					}

					// Store it
					largeShipSpecialBlocks[i] = new BlockData(b);

					// Remove it
					b.setType(Material.AIR);
				}

				// Then remove the rest of the blocks from the scene
				for (int i = 0; i < blocks.length; i++) {
					Block b = blocks[i];

					// Store it
					largeShipBlocks[i] = new BlockData(b);

					// Clear inventory (if applicable)
					BlockState state = b.getState();
					if (state instanceof InventoryHolder) {
						((InventoryHolder) state).getInventory().clear();
					}

					// Remove it
					b.setType(Material.AIR);
				}
			} else {
				for (int i = 0; i < blocks.length; i++) {
					Block b = blocks[i];
					if (b.getType() != Material.AIR) {
						BlockState state = b.getState();
						if (state instanceof InventoryHolder) {
							((InventoryHolder) state).getInventory().clear();
						}

						b.setType(Material.AIR);
					}
				}

				for (int i = 0; i < specialBlocks.length; i++) {
					Block b = specialBlocks[i];
					if (b.getType() != Material.AIR) {
						BlockState state = b.getState();
						if (state instanceof InventoryHolder) {
							((InventoryHolder) state).getInventory().clear();
						}

						b.setType(Material.AIR);
					}
				}
			}

			// Update block locations.
			for (int i = 0; i < blocks.length; i++) {
				blocks[i] = blocks[i].getRelative(dx, dy, dz);
			}

			for (int i = 0; i < specialBlocks.length; i++) {
				specialBlocks[i] = specialBlocks[i].getRelative(dx, dy, dz);
			}

			// Put players on glass for a sec
			for (Player p : passengers) {
				Block b = p.getWorld().getBlockAt(p.getLocation().subtract(0, 1, 0));
				b = b.getRelative(dx, dy, dz);
				b.setType(Material.GLASS);
			}

			new BukkitRunnable() {

				@Override
				public void run() {
					if (System.currentTimeMillis() - lastmove > 1500L) {
						for (int i = 0; i < blocks.length; i++) {
							setBlock(blocks[i], largeShipBlocks[i]);
						}

						for (int i = 0; i < specialBlocks.length; i++) {
							setBlock(specialBlocks[i], largeShipSpecialBlocks[i]);
						}
					}
				}

			}.runTaskLater(plugin, 40L);
		} else {
			BlockData[] temp = new BlockData[blocks.length];
			BlockData[] special = new BlockData[specialBlocks.length];

			// First remove all special blocks from the world
			for (int i = 0; i < specialBlocks.length; i++) {
				Block b = specialBlocks[i];

				// Clear inventory (if applicable)
				BlockState state = b.getState();
				if (state instanceof InventoryHolder) {
					((InventoryHolder) state).getInventory().clear();
				}

				// Store in special array
				special[i] = new BlockData(b);

				// Remove it
				b.setType(Material.AIR);
			}

			// Then remove the rest of the blocks from the scene
			for (int i = 0; i < blocks.length; i++) {
				Block b = blocks[i];

				// Store it
				temp[i] = new BlockData(b);

				// Clear inventory (if applicable)
				BlockState state = b.getState();
				if (state instanceof InventoryHolder) {
					((InventoryHolder) state).getInventory().clear();
				}

				// Remove it
				b.setType(Material.AIR);
			}


			// Make new blocks in their new respective positions
			for (int i = 0; i < blocks.length; i++) {
				blocks[i] = blocks[i].getRelative(dx, dy, dz);
				setBlock(blocks[i], temp[i]);
			}

			// Make special blocks
			for (int i = 0; i < specialBlocks.length; i++) {
				specialBlocks[i] = specialBlocks[i].getRelative(dx, dy, dz);
				setBlock(specialBlocks[i], special[i]);
			}
		}

		// Teleport players back on to the ship
		for (Player p : passengers) {
			p.teleport(p.getLocation().clone().add(dx, dy, dz));
		}
	}

	public boolean isSpecial(MaterialData data) {
		return data instanceof SimpleAttachableMaterialData
				|| data instanceof org.bukkit.material.Sign
				|| data instanceof Bed
				|| data instanceof PressurePlate
				|| data instanceof RedstoneWire
				|| data instanceof Rails
				|| data instanceof Diode
				|| data instanceof Vine;
	}

	public void setBlock(Block to, BlockData from, TurnDirection dir) {
		MaterialData data = from.getData();
		if (data instanceof Directional) {
			Directional directional = (Directional) data;
			directional.setFacingDirection(getRotatedBlockFace(dir, directional));
		}

		// Special case for logs
		if (data instanceof Tree) {
			Tree tree = (Tree) data;
			BlockFace direction = tree.getDirection();
			if (direction != BlockFace.UP && direction != BlockFace.DOWN && direction != BlockFace.SELF) {
				tree.setDirection(getRotatedBlockFace(dir, direction));
			}
		}

		setBlock(to, from);
	}

	public BlockFace getRotatedBlockFace(TurnDirection dir, Directional data) {
		BlockFace face;

		if (data instanceof Stairs) {
			face = ((Stairs) data).getAscendingDirection();
		} else {
			face = data.getFacing();
		}

		return getRotatedBlockFace(dir, face);
	}

	public BlockFace getRotatedBlockFace(TurnDirection dir, BlockFace face) {
		switch (face) {
			case EAST:
				return dir == TurnDirection.RIGHT ? BlockFace.SOUTH : BlockFace.NORTH;
			case NORTH:
				return dir == TurnDirection.RIGHT ? BlockFace.EAST : BlockFace.WEST;
			case SOUTH:
				return dir == TurnDirection.RIGHT ? BlockFace.WEST : BlockFace.EAST;
			case WEST:
				return dir == TurnDirection.RIGHT ? BlockFace.NORTH : BlockFace.SOUTH;
			default:
				return BlockFace.NORTH;
		}
	}

	public void setBlock(Block to, BlockData from) {
		BlockState state = to.getState();
		MaterialData data = from.getData();
		state.setType(data.getItemType());
		state.setData(data);
		state.update(true, false);

		if (from.getInventory() != null) {
			Inventory inv = ((InventoryHolder) to.getState()).getInventory();
			inv.setContents(from.getInventory());
		}

		if (to.getState() instanceof Sign) {
			Sign fromSign = (Sign) from.getState();
			Sign toSign = (Sign) to.getState();

			for (int l = 0; l < 4; l++) {
				toSign.setLine(l, fromSign.getLine(l));
			}
		}

		if (to.getState() instanceof CommandBlock) {
			CommandBlock fromCmd = (CommandBlock) from.getState();
			CommandBlock toCmd = (CommandBlock) to.getState();

			toCmd.setCommand(fromCmd.getCommand());
			toCmd.setName(fromCmd.getName());
		}

		if (to.getState() instanceof Jukebox) {
			Jukebox fromBox = (Jukebox) from.getState();
			Jukebox toBox = (Jukebox) to.getState();

			toBox.setPlaying(fromBox.getPlaying());
		}

		if (to.getState() instanceof NoteBlock) {
			NoteBlock fromBlock = (NoteBlock) from.getState();
			NoteBlock toBlock = (NoteBlock) to.getState();

			toBlock.setNote(fromBlock.getNote());
		}

		if (to.getState() instanceof Skull) {
			Skull fromSkull = (Skull) from.getState();
			Skull toSkull = (Skull) to.getState();

			toSkull.setSkullType(fromSkull.getSkullType());
			toSkull.setOwningPlayer(fromSkull.getOwningPlayer());
			toSkull.setRotation(fromSkull.getRotation());
		}

		if (to.getState() instanceof Furnace) {
			Furnace fromFurnace = (Furnace) from.getState();
			Furnace toFurnace = (Furnace) to.getState();

			toFurnace.setBurnTime(fromFurnace.getBurnTime());
			toFurnace.setCookTime(fromFurnace.getCookTime());
		}

		if (to.getState() instanceof Banner) {
			Banner fromBanner = (Banner) from.getState();
			Banner toBanner = (Banner) to.getState();

			toBanner.setBaseColor(fromBanner.getBaseColor());
			toBanner.setPatterns(fromBanner.getPatterns());
		}

		if (to.getState() instanceof CreatureSpawner) {
			CreatureSpawner fromSpawner = (CreatureSpawner) from.getState();
			CreatureSpawner toSpawner = (CreatureSpawner) to.getState();

			toSpawner.setSpawnedType(fromSpawner.getSpawnedType());
			toSpawner.setDelay(fromSpawner.getDelay());
		}

		// TODO Update whenever new BlockStates are added to Bukkit
		state.update(true, false);
	}

	// Update main block with which block the player is standing on.
	public void updateMainBlock() {
		this.mainblock = player.getWorld().getBlockAt(player.getLocation().add(0, -1, 0));
	}

	// Checks that the ship is within its size restraints as defined by its AC
	// data.
	public boolean areBlocksValid() {
		if (! data.isValidMaterial(mainblock)) {
			sendMessage("&cPlease stand on a valid block for this type of ship");
		} else {
			if (blocks.length > data.getMaxBlocks()) {
				sendMessage("&cYour ship has &e{0}&c/&e{1} &cblocks. Please remove some.",
						blocks.length, data.getMaxBlocks());
			} else if (numMainBlocks < data.getMinBlocks()) {
				sendMessage("&cYour ship has &e{0}&c/&e{1} {2} &cblocks. Please add more.",
						numMainBlocks, data.getMinBlocks(), getMainType());
			} else {
				return true;
			}
		}

		return false;
	}

	// Returns a string name for the main material of this ship.
	public String getMainType() {
		return MaterialUtil.getName(data.getMainType());
	}

	// Checks if ship is already being piloted
	public boolean isShipAlreadyPiloted() {
		for (Ship othership : plugin.getShipHandler().getShips()) {
			for (int i = 0; i < othership.blocks.length; i++) {
				if (blockBelongsToShip(othership.blocks[i], blocks)) {
					return true;
				}
			}
		}

		Iterator<Ship> iter = plugin.getShipHandler().getSinking().iterator();
		while (iter.hasNext()) {
			Ship sinking = iter.next();
			for (int i = 0; i < sinking.blocks.length; i++) {
				if (blockBelongsToShip(sinking.blocks[i], blocks)) {
					sinking.isSinking = false;
					iter.remove();
				}
			}
		}

		return false;
	}

	// Checks that the block being queried belongs to the block list for this
	// ship.
	public boolean blockBelongsToShip(Block block, Block[] blocks) {
		if (blocks == null) {
			blocks = this.blocks.clone();
		}

		for (Block b : blocks) {
			if (b.equals(block))
				return true;
		}

		return false;
	}

	// Get blocks array - useful for other classes trying to access blocks.
	public Block[] getBlocks() {
		return blocks;
	}

	// Checks all online players to find which are passengers on this ship.
	public List<Player> getPassengers() {
		List<Player> ret = new ArrayList<>();

		for (Player player : Util.getOnlinePlayers()) {
			if (isPassenger(player)) {
				ret.add(player);
			}
		}

		return ret;
	}

	// Checks if player is on any block on the ship and returns true if they are.
	public boolean isPassenger(Player player) {
		for (Block block : blocks) {
			if (! player.getWorld().equals(block.getWorld())) {
				return false;
			}

			if (block.getType() == Material.AIR) {
				continue;
			}

			Block blockon = player.getWorld().getBlockAt(player.getLocation().add(0, -1, 0));
			Block blockon2 = player.getWorld().getBlockAt(player.getLocation().add(0, -2, 0));
			if (block.equals(blockon) || block.equals(blockon2) || Util.coordsEqual(player.getLocation(), block.getLocation()))
				return true;
		}

		return false;
	}

	private long recursionStartTime;

	public boolean beginRecursion(Block block) {
		recursionStartTime = System.currentTimeMillis();
		List<Block> blockList = new ArrayList<Block>(data.getMaxBlocks());
		blockList = recurse(block, blockList, Config.recursionCap);

		if (blockList != null) {
			List<Block> specialBlockList = new ArrayList<Block>();
			for (Block b : blockList.toArray(new Block[blockList.size()])) {
				if (isSpecial(b.getState().getData())) {
					specialBlockList.add(b);
					blockList.remove(b);
				}
			}

			blocks = blockList.toArray(new Block[blockList.size()]);
			specialBlocks = specialBlockList.toArray(new Block[specialBlockList.size()]);
			return true;
		}

		return false;
	}

	// Recursively call this method for every block relative to the starting block.
	public List<Block> recurse(Block block, List<Block> blockList, boolean recursionCap) {
		if (recursionCap && (System.currentTimeMillis() - recursionStartTime) >= Config.maxRecursionTime) {
			plugin.getShipHandler().unpilotShip(player);
			sendMessage("&cRecursion took too long! This ship may be too big!");
			this.creationFailed = true;
			return null;
		}

		if (blockList != null && ! creationFailed) {
			if (blockList.size() <= data.getMaxBlocks()) {
				// If this new block to be checked doesn't already belong to the
				// ship and is a valid material, accept it.
				if (! blockBelongsToShip(block, blockList.toArray(new Block[blockList.size()])) && data.isValidMaterial(block)) {
					// If its material is same as main type than add to number
					// of main block count.
					if (block.getType() == MaterialUtil.getMaterial(data.getMainType()))
						this.numMainBlocks++;

					// Add current block to recursing block list.
					blockList.add(block);

					// Recurse for each block around this block and in turn each
					// block around them before eventually returning to
					// this method instance.
					for (RelativePosition dir : RelativePosition.values()) {
						blockList = recurse(block.getRelative(dir.getX(), dir.getY(), dir.getZ()), blockList, recursionCap);
					}
					// Otherwise if the block isn't a block that the ship is
					// allowed to touch then stop creating the ship.
				} else if (! data.isValidMaterial(block)
						&& ! block.getType().equals(Material.AIR)
						&& ! block.getType().equals(Material.SNOW)
						&& ! block.getType().equals(Material.BEDROCK)
						&& ! block.getType().equals(Material.WATER)
						&& ! block.getType().equals(Material.STATIONARY_WATER)
						&& ! data.isIgnoreAttachments()) {
					plugin.getShipHandler().unpilotShip(player);
					sendMessage("&cThis ship needs to be floating!");
					String str = FormatUtil.format("Problem at ({0}, {1}, {2}) it''s on {3}", block.getX(), block.getY(), block.getZ(),
							FormatUtil.getFriendlyName(block.getType()));
					sendMessage(str);
					plugin.getLogHandler().debug("{0} had a problem flying an airship: {1}", player.getName(), str);
					this.creationFailed = true;
					return null;
				}
			} else {
				// Ship is too large as defined by built in limit
				plugin.getShipHandler().unpilotShip(player);
				sendMessage("&7This ship has {0} blocks! Max: {1}", blockList.size(), data.getMaxBlocks());
				this.creationFailed = true;
				return null;
			}
		}

		if (blockList != null && blockList.isEmpty()) {
			// Check each direction for if the ship is larger than specified
			// ship dimensions.
			for (Direction dir : Direction.values()) {
				double min = block.getLocation().toVector().dot(dir.getVector());
				double max = min;
				for (Block b : blockList) {
					double bLoc = b.getLocation().toVector().dot(dir.getVector());
					if (bLoc < min)
						min = bLoc;
					if (bLoc > max)
						max = bLoc;
				}
				if (max - min > data.getMaxShipDimensions()) {
					plugin.getShipHandler().unpilotShip(player);
					sendMessage("&cThis ship is either too long, too tall or too wide!");
					this.creationFailed = true;
					return null;
				}
			}
		}

		return blockList;
	}

	public void sendMessage(String msg, Object... args) {
		if (player != null) {
			player.sendMessage(plugin.getPrefix() + FormatUtil.format(msg, args));
		}
	}

	public void playSound(Location location, Sound sound, float volume, float pitch) {
		if (player != null) {
			player.getWorld().playSound(location, sound, volume, pitch);
		}
	}

	public ShipData getData() {
		return data;
	}

	public final void debug(String msg, Object... args) {
		plugin.getLogHandler().debug(msg, args);
	}

	@Override
	public String toString() {
		if (player != null) {
			return "Ship[pilot=" + player.getName() + ", type=" + data.getShipType() + "]";
		} else {
			return "Ship[sinking=true, type=" + data.getShipType() + "]";
		}
	}

	public Player getPlayer() {
		return player;
	}
}