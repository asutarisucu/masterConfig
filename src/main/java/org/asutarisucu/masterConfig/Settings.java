/*
 * This file is part of the MasterConfig project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  asutarisucu and contributors
 *
 * MasterConfig is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MasterConfig is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MasterConfig.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.asutarisucu.masterConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The on-disk shape of {@code <gamedir>/masterconfig.json}.
 * Field names are the json keys, so keep them stable.
 */
public class Settings
{
	public static final String TARGET_CONFIG = "config";
	public static final String TARGET_SCHEMATICS = "schematics";
	public static final String TARGET_RESOURCEPACKS = "resourcepacks";
	public static final String TARGET_SHADERPACKS = "shaderpacks";
	public static final String TARGET_OPTIONS = "options.txt";
	public static final String TARGET_HOTBAR = "hotbar.nbt";

	public boolean enabled = true;
	public String masterRoot = "";
	public Map<String, Boolean> targets = defaultTargets();
	public List<ExtraLink> extraLinks = new ArrayList<>();
	public boolean preserveUnknownOptions = true;
	public String conflictPolicy = "MASTER_WINS";
	public boolean warnOnConcurrentLaunch = true;

	public static class ExtraLink
	{
		/** path relative to the game directory */
		public String game;
		/** path relative to the master root */
		public String master;
	}

	public static Map<String, Boolean> defaultTargets()
	{
		Map<String, Boolean> map = new LinkedHashMap<>();
		map.put(TARGET_CONFIG, true);
		map.put(TARGET_SCHEMATICS, true);
		map.put(TARGET_RESOURCEPACKS, true);
		map.put(TARGET_SHADERPACKS, true);
		map.put(TARGET_OPTIONS, true);
		map.put(TARGET_HOTBAR, true);
		return map;
	}

	/**
	 * Gson replaces the whole map / list when the key exists in the json,
	 * and leaves the field null when the key is absent, so restore what is missing by hand
	 */
	public void fillMissing()
	{
		if (this.targets == null)
		{
			this.targets = defaultTargets();
		}
		else
		{
			defaultTargets().forEach(this.targets::putIfAbsent);
		}
		if (this.extraLinks == null)
		{
			this.extraLinks = new ArrayList<>();
		}
		this.extraLinks.removeIf(link -> link == null || link.game == null || link.master == null);
		if (this.masterRoot == null)
		{
			this.masterRoot = "";
		}
		if (this.conflictPolicy == null)
		{
			this.conflictPolicy = "MASTER_WINS";
		}
	}

	public boolean isTargetEnabled(String name)
	{
		return Boolean.TRUE.equals(this.targets.get(name));
	}
}
