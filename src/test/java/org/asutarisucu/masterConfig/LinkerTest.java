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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives every branch of the state machine in DESIGN.md section 8
 */
class LinkerTest
{
	@TempDir
	Path tmp;

	private Path gameDir;
	private Path masterRoot;
	private Path backupDir;

	@BeforeEach
	void setUp() throws IOException
	{
		this.gameDir = Files.createDirectories(this.tmp.resolve("game"));
		this.masterRoot = Files.createDirectories(this.tmp.resolve("master"));
		this.backupDir = this.gameDir.resolve(Linker.BACKUP_DIR_NAME).resolve("20260820-120000");
	}

	private Path local(String name)
	{
		return this.gameDir.resolve(name);
	}

	private Path master(String name)
	{
		return this.masterRoot.resolve(name);
	}

	private Linker.Outcome link(String name) throws IOException
	{
		return Linker.link(local(name), master(name), this.backupDir, true);
	}

	private static void write(Path file, String content) throws IOException
	{
		Files.createDirectories(file.getParent());
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(Path file) throws IOException
	{
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	// case 4: nothing on either side
	@Test
	void createsTheLinkWhenNothingExistsYet() throws IOException
	{
		assertEquals(Linker.Outcome.LINKED, link("config"));

		assertTrue(PathLinks.isLink(local("config")));
		assertEquals(master("config").toRealPath(), PathLinks.resolveLink(local("config")));
		assertTrue(Files.isDirectory(master("config")));
	}

	// case 3a: first install, the local content becomes the shared content
	@Test
	void movesLocalContentIntoAnEmptyMaster() throws IOException
	{
		write(local("config").resolve("some_mod.json"), "local");

		assertEquals(Linker.Outcome.MIGRATED, link("config"));

		assertTrue(PathLinks.isLink(local("config")));
		assertEquals("local", read(master("config").resolve("some_mod.json")));
		// and it is still reachable through the link
		assertEquals("local", read(local("config").resolve("some_mod.json")));
	}

	// case 3b: MASTER_WINS, the local content is kept aside instead of being deleted
	@Test
	void backsUpLocalContentWhenTheMasterAlreadyHasSome() throws IOException
	{
		write(local("config").resolve("some_mod.json"), "local");
		write(master("config").resolve("some_mod.json"), "shared");

		assertEquals(Linker.Outcome.BACKED_UP, link("config"));

		assertEquals("shared", read(local("config").resolve("some_mod.json")));
		assertEquals("local", read(this.backupDir.resolve("config").resolve("some_mod.json")));
	}

	// case 1: every launch after the first one
	@Test
	void doesNothingWhenTheLinkIsAlreadyCorrect() throws IOException
	{
		link("config");
		write(master("config").resolve("some_mod.json"), "shared");

		assertEquals(Linker.Outcome.ALREADY_LINKED, link("config"));

		assertEquals("shared", read(local("config").resolve("some_mod.json")));
		assertFalse(Files.exists(this.backupDir));
	}

	// case 2: the master root was moved, the stale link must be replaced
	@Test
	void replacesALinkThatPointsSomewhereElse() throws IOException
	{
		Path elsewhere = Files.createDirectories(this.tmp.resolve("elsewhere").resolve("config"));
		write(elsewhere.resolve("some_mod.json"), "old shared");
		PathLinks.createLink(local("config"), elsewhere);
		write(master("config").resolve("some_mod.json"), "new shared");

		assertEquals(Linker.Outcome.RELINKED, link("config"));

		assertEquals("new shared", read(local("config").resolve("some_mod.json")));
		// the directory the stale link pointed at is untouched
		assertEquals("old shared", read(elsewhere.resolve("some_mod.json")));
	}

	// a dangling link, e.g. the master root was deleted while the game was closed
	@Test
	void replacesADanglingLink() throws IOException
	{
		Path gone = this.tmp.resolve("gone").resolve("config");
		Files.createDirectories(gone);
		PathLinks.createLink(local("config"), gone);
		PathLinks.deleteRecursively(gone);

		assertEquals(Linker.Outcome.RELINKED, link("config"));

		assertTrue(PathLinks.isLink(local("config")));
		assertEquals(master("config").toRealPath(), PathLinks.resolveLink(local("config")));
	}

	// files are never linked, so an entry that exists nowhere is simply skipped
	@Test
	void skipsAFileEntryThatExistsOnNeitherSide() throws IOException
	{
		assertEquals(Linker.Outcome.SKIPPED, Linker.link(local("options.txt"), master("options.txt"), this.backupDir, false));

		assertFalse(PathLinks.exists(local("options.txt")));
		assertFalse(PathLinks.exists(master("options.txt")));
	}

	@Test
	void deletingTheLinkNeverTouchesTheTarget() throws IOException
	{
		write(master("config").resolve("some_mod.json"), "shared");
		link("config");

		Files.delete(local("config"));

		assertFalse(PathLinks.exists(local("config")));
		assertEquals("shared", read(master("config").resolve("some_mod.json")));
	}

	/**
	 * The copy-then-delete fallback exists for cross volume moves. It must never be reached because the
	 * destination is already there, or a second backup would swallow the first one and delete the source
	 */
	@Test
	void movingOntoSomethingThatExistsIsRefusedInsteadOfOverwritingIt() throws IOException
	{
		write(this.tmp.resolve("source").resolve("data.txt"), "new");
		write(this.tmp.resolve("destination").resolve("data.txt"), "already here");

		assertThrows(FileAlreadyExistsException.class,
				() -> PathLinks.move(this.tmp.resolve("source"), this.tmp.resolve("destination")));

		assertEquals("already here", read(this.tmp.resolve("destination").resolve("data.txt")));
		assertEquals("new", read(this.tmp.resolve("source").resolve("data.txt")), "the source must still be there");
	}

	@Test
	void aBackupThatWouldCollideFailsThatOneTargetOnly() throws IOException
	{
		write(local("config").resolve("some_mod.json"), "local");
		write(master("config").resolve("some_mod.json"), "shared");
		write(this.backupDir.resolve("config").resolve("some_mod.json"), "an earlier backup");

		assertThrows(FileAlreadyExistsException.class, () -> link("config"));

		// nothing was touched
		assertEquals("local", read(local("config").resolve("some_mod.json")));
		assertEquals("an earlier backup", read(this.backupDir.resolve("config").resolve("some_mod.json")));
	}

	@Test
	void movesNonEmptyNestedDirectories() throws IOException
	{
		write(local("config").resolve("a").resolve("b").resolve("c.json"), "deep");

		assertEquals(Linker.Outcome.MIGRATED, link("config"));

		assertEquals("deep", read(master("config").resolve("a").resolve("b").resolve("c.json")));
	}
}
