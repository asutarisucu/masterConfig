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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class NbtCompound
{
	private final Map<String, NbtTag> entries = new LinkedHashMap<>();

	public Map<String, NbtTag> entries()
	{
		return Collections.unmodifiableMap(this.entries);
	}

	public Set<String> keys()
	{
		return Collections.unmodifiableSet(this.entries.keySet());
	}

	public boolean isEmpty()
	{
		return this.entries.isEmpty();
	}

	public boolean has(String key)
	{
		return this.entries.containsKey(key);
	}

	public NbtTag get(String key)
	{
		return this.entries.get(key);
	}

	public NbtCompound put(String key, NbtTag tag)
	{
		if (tag != null)
		{
			this.entries.put(key, tag);
		}
		return this;
	}

	public NbtTag remove(String key)
	{
		return this.entries.remove(key);
	}

	public NbtCompound putByte(String key, byte value)
	{
		return this.put(key, NbtTag.ofByte(value));
	}

	public NbtCompound putInt(String key, int value)
	{
		return this.put(key, NbtTag.ofInt(value));
	}

	public NbtCompound putString(String key, String value)
	{
		return this.put(key, NbtTag.ofString(value));
	}

	public NbtCompound putCompound(String key, NbtCompound value)
	{
		return this.put(key, NbtTag.ofCompound(value));
	}

	public NbtCompound putList(String key, NbtList value)
	{
		return this.put(key, NbtTag.ofList(value));
	}

	public int getInt(String key, int fallback)
	{
		NbtTag tag = this.entries.get(key);
		return tag == null ? fallback : tag.asInt(fallback);
	}

	public boolean getBoolean(String key, boolean fallback)
	{
		NbtTag tag = this.entries.get(key);
		return tag == null ? fallback : tag.asBoolean(fallback);
	}

	public String getString(String key, String fallback)
	{
		NbtTag tag = this.entries.get(key);
		return tag == null ? fallback : tag.asString(fallback);
	}

	/**
	 * @return the compound at the key, or null when absent or of another type
	 */
	public NbtCompound getCompound(String key)
	{
		NbtTag tag = this.entries.get(key);
		return tag == null ? null : tag.asCompound();
	}

	/**
	 * @return the list at the key, or null when absent or of another type
	 */
	public NbtList getList(String key)
	{
		NbtTag tag = this.entries.get(key);
		return tag == null ? null : tag.asList();
	}

	public NbtCompound copy()
	{
		NbtCompound copy = new NbtCompound();
		this.entries.forEach((key, tag) -> copy.put(key, tag.copy()));
		return copy;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof NbtCompound && this.entries.equals(((NbtCompound) other).entries);
	}

	@Override
	public int hashCode()
	{
		return this.entries.hashCode();
	}

	@Override
	public String toString()
	{
		return this.entries.toString();
	}
}
