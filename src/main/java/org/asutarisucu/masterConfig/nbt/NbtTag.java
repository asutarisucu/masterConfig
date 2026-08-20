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

import java.util.Arrays;

/**
 * One NBT tag. The binary format has not changed since TAG_Long_Array was added in 1.12,
 * so this stays valid for every minecraft version this mod supports and needs no preprocessor branch.
 */
public final class NbtTag
{
	public static final byte END = 0;
	public static final byte BYTE = 1;
	public static final byte SHORT = 2;
	public static final byte INT = 3;
	public static final byte LONG = 4;
	public static final byte FLOAT = 5;
	public static final byte DOUBLE = 6;
	public static final byte BYTE_ARRAY = 7;
	public static final byte STRING = 8;
	public static final byte LIST = 9;
	public static final byte COMPOUND = 10;
	public static final byte INT_ARRAY = 11;
	public static final byte LONG_ARRAY = 12;

	private final byte type;
	private final Object value;

	private NbtTag(byte type, Object value)
	{
		this.type = type;
		this.value = value;
	}

	public static NbtTag ofByte(byte value)
	{
		return new NbtTag(BYTE, value);
	}

	public static NbtTag ofBoolean(boolean value)
	{
		return ofByte((byte) (value ? 1 : 0));
	}

	public static NbtTag ofShort(short value)
	{
		return new NbtTag(SHORT, value);
	}

	public static NbtTag ofInt(int value)
	{
		return new NbtTag(INT, value);
	}

	public static NbtTag ofLong(long value)
	{
		return new NbtTag(LONG, value);
	}

	public static NbtTag ofFloat(float value)
	{
		return new NbtTag(FLOAT, value);
	}

	public static NbtTag ofDouble(double value)
	{
		return new NbtTag(DOUBLE, value);
	}

	public static NbtTag ofByteArray(byte[] value)
	{
		return new NbtTag(BYTE_ARRAY, value);
	}

	public static NbtTag ofString(String value)
	{
		return new NbtTag(STRING, value);
	}

	public static NbtTag ofList(NbtList value)
	{
		return new NbtTag(LIST, value);
	}

	public static NbtTag ofCompound(NbtCompound value)
	{
		return new NbtTag(COMPOUND, value);
	}

	public static NbtTag ofIntArray(int[] value)
	{
		return new NbtTag(INT_ARRAY, value);
	}

	public static NbtTag ofLongArray(long[] value)
	{
		return new NbtTag(LONG_ARRAY, value);
	}

	public byte type()
	{
		return this.type;
	}

	public Object value()
	{
		return this.value;
	}

	public boolean isNumber()
	{
		return this.value instanceof Number;
	}

	/**
	 * @return the numeric value regardless of which numeric tag it is, or the fallback for a non numeric tag
	 */
	public int asInt(int fallback)
	{
		return this.value instanceof Number ? ((Number) this.value).intValue() : fallback;
	}

	public boolean asBoolean(boolean fallback)
	{
		return this.value instanceof Number ? ((Number) this.value).intValue() != 0 : fallback;
	}

	public String asString(String fallback)
	{
		return this.type == STRING ? (String) this.value : fallback;
	}

	public NbtCompound asCompound()
	{
		return this.type == COMPOUND ? (NbtCompound) this.value : null;
	}

	public NbtList asList()
	{
		return this.type == LIST ? (NbtList) this.value : null;
	}

	/** deep copy, so that a tag taken out of one tree can be edited without touching the other */
	public NbtTag copy()
	{
		if (this.value instanceof NbtCompound)
		{
			return ofCompound(((NbtCompound) this.value).copy());
		}
		if (this.value instanceof NbtList)
		{
			return ofList(((NbtList) this.value).copy());
		}
		if (this.value instanceof byte[])
		{
			return ofByteArray(((byte[]) this.value).clone());
		}
		if (this.value instanceof int[])
		{
			return ofIntArray(((int[]) this.value).clone());
		}
		if (this.value instanceof long[])
		{
			return ofLongArray(((long[]) this.value).clone());
		}
		return this;  // the rest are immutable
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof NbtTag))
		{
			return false;
		}
		NbtTag that = (NbtTag) other;
		if (this.type != that.type)
		{
			return false;
		}
		if (this.value instanceof byte[])
		{
			return Arrays.equals((byte[]) this.value, (byte[]) that.value);
		}
		if (this.value instanceof int[])
		{
			return Arrays.equals((int[]) this.value, (int[]) that.value);
		}
		if (this.value instanceof long[])
		{
			return Arrays.equals((long[]) this.value, (long[]) that.value);
		}
		return this.value.equals(that.value);
	}

	@Override
	public int hashCode()
	{
		int hash = this.type;
		if (this.value instanceof byte[])
		{
			return 31 * hash + Arrays.hashCode((byte[]) this.value);
		}
		if (this.value instanceof int[])
		{
			return 31 * hash + Arrays.hashCode((int[]) this.value);
		}
		if (this.value instanceof long[])
		{
			return 31 * hash + Arrays.hashCode((long[]) this.value);
		}
		return 31 * hash + this.value.hashCode();
	}

	@Override
	public String toString()
	{
		if (this.value instanceof byte[])
		{
			return Arrays.toString((byte[]) this.value);
		}
		if (this.value instanceof int[])
		{
			return Arrays.toString((int[]) this.value);
		}
		if (this.value instanceof long[])
		{
			return Arrays.toString((long[]) this.value);
		}
		return String.valueOf(this.value);
	}
}
