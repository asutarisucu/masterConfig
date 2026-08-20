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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and appends to {@code options.txt} without going through minecraft.
 *
 * <p>Everything here works on ISO-8859-1 on purpose. That charset maps every byte to exactly one char and back,
 * so lines survive a read-append round trip byte for byte no matter which charset the game itself used
 * ({@code Options#save} uses a plain {@code FileWriter}, i.e. the platform default, in the older versions).
 * Keys are ascii, so comparing them still works
 */
public final class OptionsTextFile
{
	private static final byte LF = 10;
	private static final byte CR = 13;

	private OptionsTextFile()
	{
	}

	/**
	 * @return key to raw line, in file order. Lines without a {@code :} are ignored, like the game does
	 */
	public static Map<String, String> read(Path file)
	{
		Map<String, String> entries = new LinkedHashMap<>();
		if (!Files.isRegularFile(file))
		{
			return entries;
		}
		List<String> lines;
		try
		{
			lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
		}
		catch (IOException e)
		{
			return entries;
		}
		for (String line : lines)
		{
			// only the first colon separates, values contain colons too:
			// "key_key.attack:key.mouse.left", "resourcePacks:[\"file/a:b\"]"
			int colon = line.indexOf(':');
			if (colon <= 0)
			{
				continue;
			}
			entries.put(line.substring(0, colon), line);
		}
		return entries;
	}

	/**
	 * @return the whole lines of every key that was in {@code before} but is gone from {@code after},
	 * that is, every key this minecraft version does not know about
	 */
	public static List<String> droppedLines(Map<String, String> before, Map<String, String> after)
	{
		List<String> dropped = new ArrayList<>();
		before.forEach((key, line) -> {
			if (!after.containsKey(key))
			{
				dropped.add(line);
			}
		});
		return dropped;
	}

	public static void append(Path file, List<String> lines) throws IOException
	{
		if (lines.isEmpty())
		{
			return;
		}
		StringBuilder builder = new StringBuilder();
		if (!endsWithNewLine(file))
		{
			// the game writes every key with println so it normally does end with one, but a file
			// truncated by a crash would otherwise get its last line glued to the first appended one
			builder.append(System.lineSeparator());
		}
		for (String line : lines)
		{
			builder.append(line).append(System.lineSeparator());
		}
		Files.write(file, builder.toString().getBytes(StandardCharsets.ISO_8859_1), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	private static boolean endsWithNewLine(Path file)
	{
		try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ))
		{
			long size = channel.size();
			if (size == 0)
			{
				return true;  // nothing to glue onto
			}
			ByteBuffer buffer = ByteBuffer.allocate(1);
			channel.position(size - 1).read(buffer);
			byte last = buffer.array()[0];
			return last == LF || last == CR;
		}
		catch (IOException e)
		{
			return true;  // if it cannot be read it cannot be appended to either, let the append report the failure
		}
	}
}
