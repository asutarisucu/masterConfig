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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.asutarisucu.masterConfig.MasterConfigMod.LOGGER;

/**
 * Holds the resolved state of the mod. Initialized from the preLaunch entrypoint,
 * i.e. before any other mod touches its config directory, and before Minecraft is loaded
 */
public final class MasterConfigService
{
	public static final String SETTINGS_FILE_NAME = "masterconfig.json";
	private static final String PROPERTY_DISABLE = "masterconfig.disable";
	private static final String PROPERTY_ROOT = "masterconfig.root";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Settings settings = new Settings();
	private static Path gameDir;
	private static Path masterRoot;
	private static boolean active = false;
	private static boolean optionsFreshlyMigrated = false;

	private MasterConfigService()
	{
	}

	public static void init()
	{
		gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		settings = loadOrCreateSettings(gameDir.resolve(SETTINGS_FILE_NAME));

		if (Boolean.getBoolean(PROPERTY_DISABLE))
		{
			LOGGER.info("Disabled by -D{}=true, doing nothing", PROPERTY_DISABLE);
			return;
		}
		if (!settings.enabled)
		{
			LOGGER.info("Disabled by \"enabled\": false in {}, doing nothing", SETTINGS_FILE_NAME);
			return;
		}

		String rootValue = System.getProperty(PROPERTY_ROOT, settings.masterRoot);
		if (rootValue == null || rootValue.trim().isEmpty())
		{
			LOGGER.warn("\"masterRoot\" is not set in {}, doing nothing. Set it to the folder you want to share", gameDir.resolve(SETTINGS_FILE_NAME));
			return;
		}

		Path root;
		try
		{
			root = Paths.get(rootValue.trim()).toAbsolutePath().normalize();
		}
		catch (Exception e)
		{
			LOGGER.error("\"masterRoot\" is not a valid path: {}", rootValue, e);
			return;
		}
		if (root.startsWith(gameDir))
		{
			LOGGER.error("\"masterRoot\" ({}) is inside the game directory ({}), which would link a folder into itself. Doing nothing", root, gameDir);
			return;
		}
		try
		{
			Files.createDirectories(root);
		}
		catch (IOException e)
		{
			LOGGER.error("Failed to create the master root {}", root, e);
			return;
		}

		masterRoot = root;
		active = true;
		LOGGER.info("Game directory: {}", gameDir);
		LOGGER.info("Master root: {}", masterRoot);
	}

	private static Settings loadOrCreateSettings(Path file)
	{
		if (Files.isRegularFile(file))
		{
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				Settings loaded = GSON.fromJson(reader, Settings.class);
				if (loaded != null)
				{
					loaded.fillMissing();
					return loaded;
				}
				LOGGER.warn("{} is empty, falling back to the defaults", file);
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to read {}, falling back to the defaults", file, e);
			}
			return new Settings();
		}

		Settings created = new Settings();
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
		{
			GSON.toJson(created, writer);
			LOGGER.info("Created {}", file);
		}
		catch (IOException e)
		{
			LOGGER.error("Failed to create {}", file, e);
		}
		return created;
	}

	public static boolean isActive()
	{
		return active;
	}

	/**
	 * True only on the very first launch after installing the mod, when an existing options.txt was moved
	 * into the master root. That file does come from a single known version, so its DataFixer must still run
	 */
	public static boolean isOptionsFreshlyMigrated()
	{
		return optionsFreshlyMigrated;
	}

	static void setOptionsFreshlyMigrated(boolean value)
	{
		optionsFreshlyMigrated = value;
	}

	public static Settings getSettings()
	{
		return settings;
	}

	public static Path getGameDir()
	{
		return gameDir;
	}

	public static Path getMasterRoot()
	{
		return masterRoot;
	}

	/**
	 * @return the path of the given entry inside the master root
	 * @throws IllegalStateException if the mod is not active
	 */
	public static Path resolve(String name)
	{
		if (!active)
		{
			throw new IllegalStateException("MasterConfig is not active");
		}
		return masterRoot.resolve(name);
	}

	/**
	 * The form the mixins use: it never throws, and it honours the per target switch
	 *
	 * @return the path inside the master root, or null when the mod is inactive or this target is turned off
	 */
	public static Path resolveTarget(String name)
	{
		if (!active || !settings.isTargetEnabled(name))
		{
			return null;
		}
		return masterRoot.resolve(name);
	}
}
