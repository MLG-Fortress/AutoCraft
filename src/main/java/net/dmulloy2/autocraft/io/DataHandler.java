package net.dmulloy2.autocraft.io;

import java.io.File;
import java.util.HashMap;
import java.util.Set;

import net.dmulloy2.autocraft.AutoCraft;
import net.dmulloy2.autocraft.types.ShipData;

public class DataHandler {
	private HashMap<String, ShipData> data;
	
	private final AutoCraft plugin;
	
	public DataHandler(AutoCraft plugin) {
		this.plugin = plugin;
		
		this.data = new HashMap<String, ShipData>();
		
		load();
	}
	
	public void load() {
		File shipsFolder = new File(plugin.getDataFolder(), "ships");
		if (! shipsFolder.exists()) {
			shipsFolder.mkdir();
		}
		
		File[] children = shipsFolder.listFiles();
		if (children.length == 0) {
			generateStockShips();
		}
		
		// Refresh
		children = shipsFolder.listFiles();
		
		int loadedShips = 0;
		for (File file : children) {
			ShipData shipData = FileSerialization.load(file, ShipData.class);
			shipData.setShipType(trimFileName(file));
			data.put(shipData.getShipType(), shipData);
			loadedShips++;
		}
		
		plugin.getLogHandler().log("Loaded {0} ships!", loadedShips);
	}

	public void generateStockShips() {
		plugin.getLogHandler().log("Generating stock ships!");
		
		String[] stocks = new String[] { "airship", "base", "battle", "dreadnought", "pirate", "stealth", "titan", "turret" };
		
		for (String stock : stocks) {
			plugin.saveResource("ships/" + stock + ".yml", false);
		}
	}
	
	private String trimFileName(File file) {
		return file.getName().replaceAll(".yml", "");
	}
	
	public ShipData getData(String key) {
		return data.get(key);
	}
	
	public boolean isValidShip(String key) {
		return getData(key) != null;
	}
	
	public void clearMemory() {
		data.clear();
	}
	
	public Set<String> getAvailableShips() {
		return data.keySet();
	}
}