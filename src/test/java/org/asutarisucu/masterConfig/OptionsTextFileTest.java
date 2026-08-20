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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsTextFileTest
{
	@TempDir
	Path tmp;

	private Path file;

	@BeforeEach
	void setUp()
	{
		this.file = this.tmp.resolve("options.txt");
	}

	private void write(String... lines) throws IOException
	{
		Files.write(this.file, String.join("\n", lines).getBytes(StandardCharsets.ISO_8859_1));
	}

	@Test
	void splitsOnTheFirstColonOnly() throws IOException
	{
		write(
				"fov:0.5",
				"key_key.attack:key.mouse.left",
				"resourcePacks:[\"vanilla\",\"file/a:b\"]"
		);

		Map<String, String> entries = OptionsTextFile.read(this.file);

		assertEquals(Arrays.asList("fov", "key_key.attack", "resourcePacks"), new java.util.ArrayList<>(entries.keySet()));
		assertEquals("resourcePacks:[\"vanilla\",\"file/a:b\"]", entries.get("resourcePacks"));
	}

	@Test
	void ignoresLinesWithoutAColon() throws IOException
	{
		write("fov:0.5", "", "garbage", ":novalue");

		assertEquals(1, OptionsTextFile.read(this.file).size());
	}

	@Test
	void readsAMissingFileAsEmpty()
	{
		assertTrue(OptionsTextFile.read(this.tmp.resolve("nope.txt")).isEmpty());
	}

	@Test
	void reportsExactlyTheKeysThatDisappeared() throws IOException
	{
		write("version:2975", "fov:0.5", "menuBackgroundBlurriness:5", "someFutureKey:x");
		Map<String, String> before = OptionsTextFile.read(this.file);

		// what an older version would leave behind: it only writes the keys it knows
		write("version:2975", "fov:0.7");
		Map<String, String> after = OptionsTextFile.read(this.file);

		assertEquals(
				Arrays.asList("menuBackgroundBlurriness:5", "someFutureKey:x"),
				OptionsTextFile.droppedLines(before, after)
		);
	}

	@Test
	void reportsNothingWhenEveryKeySurvived() throws IOException
	{
		write("fov:0.5", "guiScale:3");
		Map<String, String> before = OptionsTextFile.read(this.file);
		write("fov:0.7", "guiScale:2");

		assertTrue(OptionsTextFile.droppedLines(before, OptionsTextFile.read(this.file)).isEmpty());
	}

	@Test
	void appendingRestoresTheDroppedKeysAndIsStableOnASecondRound() throws IOException
	{
		write("version:2975", "fov:0.5", "someFutureKey:x");
		Map<String, String> before = OptionsTextFile.read(this.file);
		write("version:2975", "fov:0.7");

		List<String> dropped = OptionsTextFile.droppedLines(before, OptionsTextFile.read(this.file));
		OptionsTextFile.append(this.file, dropped);

		Map<String, String> restored = OptionsTextFile.read(this.file);
		assertEquals("someFutureKey:x", restored.get("someFutureKey"));
		assertEquals("fov:0.7", restored.get("fov"));

		// saving again must not duplicate anything
		Map<String, String> secondBefore = OptionsTextFile.read(this.file);
		write("version:2975", "fov:0.9");
		OptionsTextFile.append(this.file, OptionsTextFile.droppedLines(secondBefore, OptionsTextFile.read(this.file)));

		assertEquals(3, OptionsTextFile.read(this.file).size());
		assertEquals("someFutureKey:x", OptionsTextFile.read(this.file).get("someFutureKey"));
	}

	@Test
	void keepsNonAsciiBytesUntouched() throws IOException
	{
		byte[] raw = "resourcePacks:[\"file/\u00e6\u00f8\u00e5.zip\"]\nfov:0.5".getBytes(StandardCharsets.UTF_8);
		Files.write(this.file, raw);
		Map<String, String> before = OptionsTextFile.read(this.file);

		Files.write(this.file, "fov:0.5".getBytes(StandardCharsets.ISO_8859_1));
		OptionsTextFile.append(this.file, OptionsTextFile.droppedLines(before, OptionsTextFile.read(this.file)));

		String restored = new String(Files.readAllBytes(this.file), StandardCharsets.UTF_8);
		assertTrue(restored.contains("file/\u00e6\u00f8\u00e5.zip"), restored);
	}
}
