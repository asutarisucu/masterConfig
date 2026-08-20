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

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.asutarisucu.masterConfig.MasterConfigMod.LOGGER;

/**
 * Degraded mode for when no link can be created at all.
 *
 * <p>Fabric loader has no system property for the config directory, it is simply
 * {@code configDir = gameDir.resolve("config")} stored in a private, non final field of FabricLoaderImpl.
 * Overwriting that field redirects every mod that asks the loader for its config directory,
 * but does nothing for mods that build their paths from the game directory themselves
 */
public final class ConfigDirFallback
{
	private ConfigDirFallback()
	{
	}

	public static boolean apply(Path configDir)
	{
		try
		{
			Files.createDirectories(configDir);
			Object loader = FabricLoader.getInstance();
			Field field = loader.getClass().getDeclaredField("configDir");
			field.setAccessible(true);
			field.set(loader, configDir);
			LOGGER.warn("Could not link the config directory, redirected FabricLoader#getConfigDir to {} instead. " +
					"Mods that build their own paths from the game directory will not be shared", configDir);
			return true;
		}
		catch (Throwable t)
		{
			LOGGER.error("Could not link the config directory, and redirecting FabricLoader#getConfigDir failed too", t);
			return false;
		}
	}
}
