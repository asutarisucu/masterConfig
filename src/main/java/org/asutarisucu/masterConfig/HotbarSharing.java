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

import org.asutarisucu.masterConfig.mchf.HotbarStore;
import org.asutarisucu.masterConfig.mchf.Mchf;
import org.asutarisucu.masterConfig.nbt.NbtCompound;
import org.asutarisucu.masterConfig.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.asutarisucu.masterConfig.MasterConfigMod.LOGGER;

/**
 * Wires the shared {@code hotbars.dat} to the per instance {@code hotbar.nbt}.
 *
 * <p>The game keeps reading and writing its own {@code hotbar.nbt} as always. This class only refills that
 * file before the game reads it and takes the result back afterwards, which keeps every ItemStack and
 * DataComponent api out of the picture and leaves the DataFixer work to the vanilla loader
 */
public final class HotbarSharing
{
	private HotbarSharing()
	{
	}

	private static boolean enabled()
	{
		return MasterConfigService.resolveTarget(Settings.TARGET_HOTBAR) != null;
	}

	private static Path storeFile()
	{
		return MasterConfigService.getMasterRoot().resolve(Mchf.STORE_FILE_NAME);
	}

	private static Path nativeFile()
	{
		return MasterConfigService.getGameDir().resolve(Mchf.NATIVE_FILE_NAME);
	}

	/** called right before the vanilla HotbarManager reads hotbar.nbt */
	public static void beforeLoad(int dataVersion)
	{
		if (!enabled())
		{
			return;
		}
		try
		{
			Path store = storeFile();
			Path local = nativeFile();

			if (Files.isRegularFile(local) && !isGeneratedByUs(local))
			{
				// a real hotbar.nbt this instance had before the mod was installed
				if (Files.isRegularFile(store))
				{
					Path backup = MasterConfigService.getGameDir().
							resolve(Linker.BACKUP_DIR_NAME).
							resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())).
							resolve(Mchf.NATIVE_FILE_NAME);
					PathLinks.move(local, backup);
					LOGGER.info("The master root already has shared hotbars, the local hotbar.nbt was moved to {}", backup);
				}
				else
				{
					NbtCompound existing = NbtIo.read(local);
					HotbarStore seeded = new HotbarStore();
					seeded.absorbNativeHotbarFile(existing, existing.getInt("DataVersion", dataVersion));
					seeded.write(store);
					LOGGER.info("Seeded the shared hotbars from the existing hotbar.nbt");
				}
			}

			NbtCompound generated = HotbarStore.read(store).toNativeHotbarFile(dataVersion);
			if (generated == null)
			{
				Files.deleteIfExists(local);  // nothing is shared yet, so start this instance empty too
				return;
			}
			generated.putByte(Mchf.MARKER_KEY, (byte) 1);
			NbtIo.write(local, generated, false);
			LOGGER.info("Loaded the shared hotbars, written for DataVersion {}", generated.getInt("DataVersion", -1));
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to hand the shared hotbars to the game, it will use its own hotbar.nbt", e);
		}
	}

	/** called right after the vanilla HotbarManager wrote hotbar.nbt */
	public static void afterSave(int dataVersion)
	{
		if (!enabled())
		{
			return;
		}
		try
		{
			Path local = nativeFile();
			if (!Files.isRegularFile(local))
			{
				return;
			}
			NbtCompound written = NbtIo.read(local);
			Path store = storeFile();
			HotbarStore shared = HotbarStore.read(store);
			int replaced = shared.absorbNativeHotbarFile(written, written.getInt("DataVersion", dataVersion));

			// the game wrote the file itself, so it lost the marker. Put it back, otherwise the next launch
			// would take this for a hotbar.nbt that predates the mod and move it into the backup directory
			written.putByte(Mchf.MARKER_KEY, (byte) 1);
			NbtIo.write(local, written, false);

			if (replaced == 0)
			{
				return;
			}
			shared.write(store);
			LOGGER.info("Saved {} hotbar group(s) to the master root", replaced);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to save the hotbars to the master root", e);
		}
	}

	private static boolean isGeneratedByUs(Path file)
	{
		try
		{
			return NbtIo.read(file).getBoolean(Mchf.MARKER_KEY, false);
		}
		catch (Exception e)
		{
			return false;
		}
	}
}
