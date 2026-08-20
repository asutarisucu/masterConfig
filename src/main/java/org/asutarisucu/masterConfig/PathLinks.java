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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Filesystem link primitives.
 *
 * <p>Windows specifics that the rest of the mod relies on, all verified on Windows 11 + JDK 25:
 * <ul>
 *     <li>a junction is not a symbolic link to java, it shows up as {@code isOther()}</li>
 *     <li>{@code Path#toRealPath} resolves both symlinks and junctions to their target</li>
 *     <li>{@code Files#delete} on a symlink or a junction removes the link only, never the target</li>
 *     <li>creating a symlink needs a privilege, creating a junction does not, but a junction only works for directories</li>
 * </ul>
 */
public final class PathLinks
{
	public static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private PathLinks()
	{
	}

	/**
	 * @return true if the path itself exists, without following it if it is a link
	 */
	public static boolean exists(Path path)
	{
		return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean isLink(Path path)
	{
		try
		{
			BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			return attributes.isSymbolicLink() || attributes.isOther();
		}
		catch (IOException e)
		{
			return false;
		}
	}

	/**
	 * @return where the given link points at, or null if it dangles or cannot be read
	 */
	public static Path resolveLink(Path link)
	{
		try
		{
			return link.toRealPath();
		}
		catch (IOException e)
		{
			return null;
		}
	}

	/**
	 * Creates a symlink, falling back to a windows junction when the symlink privilege is missing.
	 * The junction fallback only applies to directories, which is why files are never linked by this mod
	 */
	public static void createLink(Path link, Path target) throws IOException
	{
		IOException failure;
		try
		{
			Files.createSymbolicLink(link, target);
			return;
		}
		catch (IOException e)
		{
			failure = e;
		}
		catch (UnsupportedOperationException e)
		{
			failure = new IOException(e);
		}

		if (WINDOWS && Files.isDirectory(target) && createJunction(link, target))
		{
			return;
		}
		throw failure;
	}

	private static boolean createJunction(Path link, Path target)
	{
		try
		{
			Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).
					redirectErrorStream(true).
					start();
			if (!process.waitFor(30, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0 && isLink(link);
		}
		catch (IOException e)
		{
			return false;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Moves a file or a whole directory, falling back to copy-then-delete when the two ends
	 * are on different volumes. Never deletes anything before the copy has succeeded
	 */
	public static void move(Path from, Path to) throws IOException
	{
		if (exists(to))
		{
			// the fallback below would otherwise merge into it and then delete the source,
			// which is a silent overwrite of data this mod promised never to destroy
			throw new FileAlreadyExistsException(to.toString());
		}
		Path parent = to.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		try
		{
			Files.move(from, to);
			return;
		}
		catch (IOException e)
		{
			// different volume, or a non-empty directory that cannot be moved in one go
		}
		copyRecursively(from, to);
		deleteRecursively(from);
	}

	public static void copyRecursively(Path from, Path to) throws IOException
	{
		Files.walkFileTree(from, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
			{
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public static void deleteRecursively(Path path) throws IOException
	{
		if (!exists(path))
		{
			return;
		}
		if (isLink(path) || !Files.isDirectory(path))
		{
			Files.delete(path);
			return;
		}
		Files.walkFileTree(path, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
			{
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
