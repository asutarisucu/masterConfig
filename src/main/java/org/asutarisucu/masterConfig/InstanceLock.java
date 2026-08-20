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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.asutarisucu.masterConfig.MasterConfigMod.LOGGER;

/**
 * Detects other instances sharing the same master root.
 *
 * <p>Two instances writing the same {@code options.txt} or {@code hotbars.dat} is last writer wins, so the
 * second one silently loses whatever the first one changed. There is no safe way to merge that after the
 * fact, and refusing to start would be worse than the problem, so this only warns.
 *
 * <p>The lock file holds one line per live instance. Stale lines, left behind by a crash, are recognised
 * because the process id they name is gone
 */
public final class InstanceLock
{
	public static final String LOCK_FILE_NAME = ".masterconfig_lock";
	private static final String SEPARATOR = "\t";

	private static Path lockFile;
	private static String ownLine;

	private InstanceLock()
	{
	}

	/**
	 * @return the game directories of the other live instances, empty when this is the only one
	 */
	public static List<String> acquire()
	{
		List<String> others = new ArrayList<>();
		if (!MasterConfigService.isActive() || !MasterConfigService.getSettings().warnOnConcurrentLaunch)
		{
			return others;
		}
		try
		{
			lockFile = MasterConfigService.getMasterRoot().resolve(LOCK_FILE_NAME);
			long pid = currentPid();
			ownLine = pid + SEPARATOR + MasterConfigService.getGameDir();

			List<String> kept = new ArrayList<>();
			for (String line : readLines(lockFile))
			{
				int tab = line.indexOf(SEPARATOR.charAt(0));
				if (tab <= 0)
				{
					continue;
				}
				long otherPid;
				try
				{
					otherPid = Long.parseLong(line.substring(0, tab));
				}
				catch (NumberFormatException e)
				{
					continue;  // not ours to interpret, drop it
				}
				if (otherPid == pid || !isAlive(otherPid))
				{
					continue;  // ourselves from a previous run, or a line left behind by a crash
				}
				kept.add(line);
				others.add(line.substring(tab + 1));
			}
			kept.add(ownLine);
			Files.write(lockFile, String.join(System.lineSeparator(), kept).getBytes(StandardCharsets.UTF_8));

			if (!others.isEmpty())
			{
				LOGGER.warn("Another instance is already using this master root: {}", String.join(", ", others));
				LOGGER.warn("Whichever instance saves last wins, so settings changed in one of them can be lost");
			}
			Runtime.getRuntime().addShutdownHook(new Thread(InstanceLock::release, "MasterConfig lock release"));
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to register in {}", LOCK_FILE_NAME, e);
		}
		return others;
	}

	static void release()
	{
		if (lockFile == null || ownLine == null)
		{
			return;
		}
		try
		{
			List<String> kept = new ArrayList<>();
			for (String line : readLines(lockFile))
			{
				if (!line.equals(ownLine))
				{
					kept.add(line);
				}
			}
			if (kept.isEmpty())
			{
				Files.deleteIfExists(lockFile);
			}
			else
			{
				Files.write(lockFile, String.join(System.lineSeparator(), kept).getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (Exception e)
		{
			// nothing useful to do while shutting down, and a leftover line is recognised as stale next time
		}
	}

	private static List<String> readLines(Path file)
	{
		try
		{
			return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new ArrayList<>();
		}
		catch (IOException e)
		{
			return new ArrayList<>();
		}
	}

	private static long currentPid()
	{
		// ProcessHandle is java 9, and the oldest version this mod supports already needs java 17
		return ProcessHandle.current().pid();
	}

	private static boolean isAlive(long pid)
	{
		return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
	}
}
