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

package org.asutarisucu.masterConfig.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The codec has to reproduce what minecraft writes exactly, because the file it produces is handed
 * straight to the vanilla loader
 */
class NbtIoTest
{
	@TempDir
	Path tmp;

	/** one tag of every type, including the containers */
	private static NbtCompound everyType()
	{
		NbtCompound nested = new NbtCompound();
		nested.putString("inner", "value");

		NbtList compounds = new NbtList();
		compounds.add(NbtTag.ofCompound(nested));
		compounds.add(NbtTag.ofCompound(new NbtCompound()));

		NbtCompound root = new NbtCompound();
		root.put("aByte", NbtTag.ofByte((byte) -128));
		root.put("aShort", NbtTag.ofShort((short) -32768));
		root.put("anInt", NbtTag.ofInt(Integer.MIN_VALUE));
		root.put("aLong", NbtTag.ofLong(Long.MIN_VALUE));
		root.put("aFloat", NbtTag.ofFloat(0.5f));
		root.put("aDouble", NbtTag.ofDouble(-0.25d));
		root.put("aByteArray", NbtTag.ofByteArray(new byte[]{1, 2, 3}));
		root.put("aString", NbtTag.ofString("hello æøå 日本語"));
		root.put("aList", NbtTag.ofList(compounds));
		root.put("anEmptyList", NbtTag.ofList(new NbtList()));
		root.put("aCompound", NbtTag.ofCompound(nested));
		root.put("anIntArray", NbtTag.ofIntArray(new int[]{4, 5, 6}));
		root.put("aLongArray", NbtTag.ofLongArray(new long[]{7L, 8L}));
		return root;
	}

	@Test
	void everyTagTypeSurvivesAWriteReadRoundTrip() throws IOException
	{
		Path file = this.tmp.resolve("all.nbt");
		NbtCompound original = everyType();

		NbtIo.write(file, original, false);

		assertEquals(original, NbtIo.read(file));
	}

	/** writing what was read has to give the very same bytes, or the game would see a different file */
	@Test
	void writingWhatWasReadGivesIdenticalBytes() throws IOException
	{
		Path first = this.tmp.resolve("first.nbt");
		Path second = this.tmp.resolve("second.nbt");
		NbtIo.write(first, everyType(), false);

		NbtIo.write(second, NbtIo.read(first), false);

		assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
	}

	@Test
	void gzipIsDetectedOnReadWhicheverWayItWasWritten() throws IOException
	{
		Path plain = this.tmp.resolve("plain.nbt");
		Path zipped = this.tmp.resolve("zipped.dat");
		NbtIo.write(plain, everyType(), false);
		NbtIo.write(zipped, everyType(), true);

		assertEquals(NbtIo.read(plain), NbtIo.read(zipped));
		// and it really is compressed
		assertEquals(0x1f, Files.readAllBytes(zipped)[0] & 0xff);
	}

	/** minecraft writes the root with an empty name, so the first bytes are 0x0A 0x00 0x00 */
	@Test
	void theRootIsANamedCompoundWithAnEmptyName() throws IOException
	{
		Path file = this.tmp.resolve("root.nbt");
		NbtIo.write(file, new NbtCompound(), false);

		byte[] bytes = Files.readAllBytes(file);

		assertArrayEquals(new byte[]{NbtTag.COMPOUND, 0, 0, NbtTag.END}, bytes);
	}

	@Test
	void aFileThatIsNotACompoundIsRejected()
	{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		buffer.write(NbtTag.STRING);

		assertThrows(IOException.class, () -> NbtIo.read(new ByteArrayInputStream(buffer.toByteArray())));
	}

	@Test
	void aTruncatedFileIsRejectedRatherThanReadAsEmpty()
	{
		assertThrows(IOException.class,
				() -> NbtIo.read(new ByteArrayInputStream(new byte[]{NbtTag.COMPOUND, 0, 0, NbtTag.INT, 0, 1, 'a'})));
	}

	@Test
	void gzipThatIsNotNbtIsRejected() throws IOException
	{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(buffer))
		{
			gzip.write("not nbt at all".getBytes("UTF-8"));
		}

		assertThrows(IOException.class, () -> NbtIo.read(new ByteArrayInputStream(buffer.toByteArray())));
	}
}
