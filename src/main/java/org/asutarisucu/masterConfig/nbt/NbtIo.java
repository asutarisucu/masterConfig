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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes nbt files without going through minecraft.
 *
 * <p>{@code hotbar.nbt} is written by {@code NbtIo.write(CompoundTag, Path)} in every supported version,
 * i.e. uncompressed, with a named root whose name is empty. Reading auto detects gzip anyway,
 * so a file compressed by something else still loads
 */
public final class NbtIo
{
	private static final int GZIP_MAGIC_FIRST = 0x1f;
	private static final int GZIP_MAGIC_SECOND = 0x8b;

	private NbtIo()
	{
	}

	public static NbtCompound read(Path file) throws IOException
	{
		try (InputStream fileStream = Files.newInputStream(file))
		{
			return read(fileStream);
		}
	}

	public static NbtCompound read(InputStream rawStream) throws IOException
	{
		PushbackInputStream pushback = new PushbackInputStream(new BufferedInputStream(rawStream), 2);
		int first = pushback.read();
		int second = pushback.read();
		if (second >= 0)
		{
			pushback.unread(second);
		}
		if (first >= 0)
		{
			pushback.unread(first);
		}
		InputStream stream = first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND ? new GZIPInputStream(pushback) : pushback;

		DataInputStream input = new DataInputStream(stream);
		byte type = input.readByte();
		if (type != NbtTag.COMPOUND)
		{
			throw new IOException("The root nbt tag must be a compound, got type " + type);
		}
		input.readUTF();  // the root name, always empty in practice
		return readCompound(input, 0);
	}

	public static void write(Path file, NbtCompound root, boolean gzip) throws IOException
	{
		Path parent = file.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		try (OutputStream fileStream = Files.newOutputStream(file))
		{
			OutputStream stream = gzip ? new GZIPOutputStream(fileStream) : new BufferedOutputStream(fileStream);
			DataOutputStream output = new DataOutputStream(stream);
			output.writeByte(NbtTag.COMPOUND);
			output.writeUTF("");
			writeCompound(output, root);
			output.flush();
			if (stream instanceof GZIPOutputStream)
			{
				((GZIPOutputStream) stream).finish();
			}
			stream.flush();
		}
	}

	private static NbtCompound readCompound(DataInputStream input, int depth) throws IOException
	{
		if (depth > 512)
		{
			throw new IOException("The nbt is nested too deeply");
		}
		NbtCompound compound = new NbtCompound();
		while (true)
		{
			byte type = input.readByte();
			if (type == NbtTag.END)
			{
				return compound;
			}
			String name = input.readUTF();
			compound.put(name, readPayload(input, type, depth + 1));
		}
	}

	private static NbtTag readPayload(DataInputStream input, byte type, int depth) throws IOException
	{
		switch (type)
		{
			case NbtTag.BYTE:
				return NbtTag.ofByte(input.readByte());
			case NbtTag.SHORT:
				return NbtTag.ofShort(input.readShort());
			case NbtTag.INT:
				return NbtTag.ofInt(input.readInt());
			case NbtTag.LONG:
				return NbtTag.ofLong(input.readLong());
			case NbtTag.FLOAT:
				return NbtTag.ofFloat(input.readFloat());
			case NbtTag.DOUBLE:
				return NbtTag.ofDouble(input.readDouble());
			case NbtTag.BYTE_ARRAY:
			{
				byte[] array = new byte[readLength(input)];
				input.readFully(array);
				return NbtTag.ofByteArray(array);
			}
			case NbtTag.STRING:
				return NbtTag.ofString(input.readUTF());
			case NbtTag.LIST:
			{
				byte elementType = input.readByte();
				int length = readLength(input);
				NbtList list = new NbtList();
				list.setElementType(elementType);
				for (int i = 0; i < length; i++)
				{
					list.add(readPayload(input, elementType, depth + 1));
				}
				return NbtTag.ofList(list);
			}
			case NbtTag.COMPOUND:
				return NbtTag.ofCompound(readCompound(input, depth));
			case NbtTag.INT_ARRAY:
			{
				int[] array = new int[readLength(input)];
				for (int i = 0; i < array.length; i++)
				{
					array[i] = input.readInt();
				}
				return NbtTag.ofIntArray(array);
			}
			case NbtTag.LONG_ARRAY:
			{
				long[] array = new long[readLength(input)];
				for (int i = 0; i < array.length; i++)
				{
					array[i] = input.readLong();
				}
				return NbtTag.ofLongArray(array);
			}
			default:
				throw new IOException("Unknown nbt tag type " + type);
		}
	}

	private static int readLength(DataInputStream input) throws IOException
	{
		int length = input.readInt();
		if (length < 0)
		{
			throw new EOFException("Negative nbt array length " + length);
		}
		return length;
	}

	private static void writeCompound(DataOutputStream output, NbtCompound compound) throws IOException
	{
		for (java.util.Map.Entry<String, NbtTag> entry : compound.entries().entrySet())
		{
			output.writeByte(entry.getValue().type());
			output.writeUTF(entry.getKey());
			writePayload(output, entry.getValue());
		}
		output.writeByte(NbtTag.END);
	}

	private static void writePayload(DataOutputStream output, NbtTag tag) throws IOException
	{
		Object value = tag.value();
		switch (tag.type())
		{
			case NbtTag.BYTE:
				output.writeByte((Byte) value);
				break;
			case NbtTag.SHORT:
				output.writeShort((Short) value);
				break;
			case NbtTag.INT:
				output.writeInt((Integer) value);
				break;
			case NbtTag.LONG:
				output.writeLong((Long) value);
				break;
			case NbtTag.FLOAT:
				output.writeFloat((Float) value);
				break;
			case NbtTag.DOUBLE:
				output.writeDouble((Double) value);
				break;
			case NbtTag.BYTE_ARRAY:
			{
				byte[] array = (byte[]) value;
				output.writeInt(array.length);
				output.write(array);
				break;
			}
			case NbtTag.STRING:
				output.writeUTF((String) value);
				break;
			case NbtTag.LIST:
			{
				NbtList list = (NbtList) value;
				output.writeByte(list.elementType());
				output.writeInt(list.size());
				for (NbtTag item : list.items())
				{
					writePayload(output, item);
				}
				break;
			}
			case NbtTag.COMPOUND:
				writeCompound(output, (NbtCompound) value);
				break;
			case NbtTag.INT_ARRAY:
			{
				int[] array = (int[]) value;
				output.writeInt(array.length);
				for (int item : array)
				{
					output.writeInt(item);
				}
				break;
			}
			case NbtTag.LONG_ARRAY:
			{
				long[] array = (long[]) value;
				output.writeInt(array.length);
				for (long item : array)
				{
					output.writeLong(item);
				}
				break;
			}
			default:
				throw new IOException("Unknown nbt tag type " + tag.type());
		}
	}
}
