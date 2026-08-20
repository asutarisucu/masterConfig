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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.asutarisucu.masterConfig.MasterConfigMod.LOGGER;

/**
 * Replaces the directories listed in the settings with links into the master root.
 *
 * <p>Only directories are linked. Files ({@code options.txt}, {@code hotbar.nbt}) are handled by mixins instead,
 * because a windows junction cannot point at a file and a file symlink needs a privilege that a normal user lacks
 */
public final class Linker
{
	public static final String BACKUP_DIR_NAME = "masterconfig_backup";

	private static final List<String> LINKED_TARGETS = Arrays.asList(
			Settings.TARGET_CONFIG,
			Settings.TARGET_SCHEMATICS,
			Settings.TARGET_RESOURCEPACKS,
			Settings.TARGET_SHADERPACKS
	);

	public enum Outcome
	{
		/** the link was already in place, the usual result of every launch after the first one */
		ALREADY_LINKED,
		/** a link pointing somewhere else was replaced */
		RELINKED,
		/** local content was moved into the master root, which had nothing yet */
		MIGRATED,
		/** both sides had content, the local one was moved into the backup directory */
		BACKED_UP,
		/** there was nothing local, only the link was created */
		LINKED,
		/** nothing to do, e.g. a file entry that exists on neither side */
		SKIPPED,
		FAILED,
	}

	private Linker()
	{
	}

	public static void applyAll()
	{
		if (!MasterConfigService.isActive())
		{
			return;
		}

		Settings settings = MasterConfigService.getSettings();
		Path gameDir = MasterConfigService.getGameDir();
		Path masterRoot = MasterConfigService.getMasterRoot();
		Path backupDir = gameDir.
				resolve(BACKUP_DIR_NAME).
				resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));

		for (String name : LINKED_TARGETS)
		{
			if (!settings.isTargetEnabled(name))
			{
				continue;
			}
			Outcome outcome = apply(name, gameDir.resolve(name), masterRoot.resolve(name), backupDir, true);
			if (outcome == Outcome.FAILED && Settings.TARGET_CONFIG.equals(name))
			{
				ConfigDirFallback.apply(masterRoot.resolve(name));
			}
		}

		if (settings.isTargetEnabled(Settings.TARGET_OPTIONS))
		{
			Outcome outcome = migrateFile(Settings.TARGET_OPTIONS, gameDir, masterRoot, backupDir);
			MasterConfigService.setOptionsFreshlyMigrated(outcome == Outcome.MIGRATED);
		}

		for (Settings.ExtraLink link : settings.extraLinks)
		{
			applyExtraLink(link, gameDir, masterRoot, backupDir);
		}
	}

	/**
	 * Files are not linked, a mixin points the game at the master copy instead. But an instance that already
	 * had one needs its content carried over once, with the same MASTER_WINS rule the directories use
	 */
	static Outcome migrateFile(String name, Path gameDir, Path masterRoot, Path backupDir)
	{
		Path local = gameDir.resolve(name);
		Path master = masterRoot.resolve(name);
		try
		{
			if (!PathLinks.exists(local))
			{
				return Outcome.SKIPPED;
			}
			if (PathLinks.exists(master))
			{
				PathLinks.move(local, backupDir.resolve(name));
				LOGGER.info("The master root already has {}, the local one was moved to {}", name, backupDir.resolve(name));
				return Outcome.BACKED_UP;
			}
			PathLinks.move(local, master);
			LOGGER.info("Moved the existing {} into the master root", name);
			return Outcome.MIGRATED;
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to move {} into the master root", name, e);
			return Outcome.FAILED;
		}
	}

	private static void applyExtraLink(Settings.ExtraLink link, Path gameDir, Path masterRoot, Path backupDir)
	{
		Path local = gameDir.resolve(link.game).normalize();
		Path master = masterRoot.resolve(link.master).normalize();
		if (!local.startsWith(gameDir) || !master.startsWith(masterRoot))
		{
			LOGGER.error("Ignoring extra link {} -> {}, it escapes the game directory or the master root", link.game, link.master);
			return;
		}
		if (local.equals(gameDir) || master.equals(masterRoot))
		{
			// "" and "." land here, and linking a root to a root would move a whole instance into its backup
			LOGGER.error("Ignoring extra link {} -> {}, neither end may be the root itself", link.game, link.master);
			return;
		}
		if (local.equals(gameDir.resolve(BACKUP_DIR_NAME)) || local.equals(gameDir.resolve(MasterConfigService.SETTINGS_FILE_NAME)))
		{
			LOGGER.error("Ignoring extra link {} -> {}, that is this own backup directory or settings file", link.game, link.master);
			return;
		}

		boolean directory;
		if (PathLinks.exists(master))
		{
			directory = Files.isDirectory(master);
		}
		else if (PathLinks.exists(local) && !PathLinks.isLink(local))
		{
			directory = Files.isDirectory(local);
		}
		else
		{
			LOGGER.warn("Skipping extra link {} -> {}, it exists on neither side so its kind is unknown", link.game, link.master);
			return;
		}

		if (!directory)
		{
			LOGGER.warn("Skipping extra link {} -> {}, linking a single file is not supported", link.game, link.master);
			return;
		}
		apply(link.game, local, master, backupDir, true);
	}

	private static Outcome apply(String name, Path local, Path master, Path backupDir, boolean directory)
	{
		Outcome outcome;
		try
		{
			outcome = link(local, master, backupDir, directory);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to link {} to {}", local, master, e);
			return Outcome.FAILED;
		}

		switch (outcome)
		{
			case ALREADY_LINKED:
				LOGGER.debug("{} is already linked to {}", name, master);
				break;
			case MIGRATED:
				LOGGER.info("Moved the existing {} into the master root and linked it back", name);
				break;
			case BACKED_UP:
				LOGGER.info("The master root already has {}, the local one was moved to {}", name, backupDir.resolve(local.getFileName().toString()));
				break;
			case SKIPPED:
				LOGGER.debug("Nothing to link for {}", name);
				break;
			default:
				LOGGER.info("Linked {} to {} ({})", name, master, outcome);
				break;
		}
		return outcome;
	}

	/**
	 * The state machine described in DESIGN.md section 8. Package private so the tests can drive it directly
	 */
	static Outcome link(Path local, Path master, Path backupDir, boolean directory) throws IOException
	{
		Outcome outcome;

		if (PathLinks.isLink(local))
		{
			Path current = PathLinks.resolveLink(local);
			Path wanted = PathLinks.exists(master) ? master.toRealPath() : master;
			if (wanted.equals(current))
			{
				return Outcome.ALREADY_LINKED;
			}
			Files.delete(local);  // removes the link only, never what it points at
			outcome = Outcome.RELINKED;
		}
		else if (PathLinks.exists(local))
		{
			if (PathLinks.exists(master))
			{
				PathLinks.move(local, backupDir.resolve(local.getFileName().toString()));
				outcome = Outcome.BACKED_UP;
			}
			else
			{
				PathLinks.move(local, master);
				outcome = Outcome.MIGRATED;
			}
		}
		else
		{
			outcome = Outcome.LINKED;
		}

		if (!PathLinks.exists(master))
		{
			if (!directory)
			{
				return Outcome.SKIPPED;
			}
			Files.createDirectories(master);
		}

		PathLinks.createLink(local, master);
		return outcome;
	}
}
