package com.minesworn.autocraft.ships;

import com.minesworn.autocraft.core.io.EFactory;

public class ACFactory implements EFactory<ACProperties> {

	public ACProperties newEntity() {
		return new ACProperties();
	}
	
}
