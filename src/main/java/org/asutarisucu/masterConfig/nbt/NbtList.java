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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NbtList
{
	private byte elementType = NbtTag.END;
	private final List<NbtTag> items = new ArrayList<>();

	public static NbtList of(NbtTag... tags)
	{
		NbtList list = new NbtList();
		for (NbtTag tag : tags)
		{
			list.add(tag);
		}
		return list;
	}

	public byte elementType()
	{
		return this.elementType;
	}

	public void setElementType(byte elementType)
	{
		this.elementType = elementType;
	}

	public List<NbtTag> items()
	{
		return Collections.unmodifiableList(this.items);
	}

	public int size()
	{
		return this.items.size();
	}

	public NbtTag get(int index)
	{
		return this.items.get(index);
	}

	public NbtList add(NbtTag tag)
	{
		if (this.items.isEmpty())
		{
			this.elementType = tag.type();
		}
		else if (this.elementType != tag.type())
		{
			throw new IllegalArgumentException("An nbt list holds one type only, has " + this.elementType + ", got " + tag.type());
		}
		this.items.add(tag);
		return this;
	}

	public NbtList copy()
	{
		NbtList copy = new NbtList();
		copy.elementType = this.elementType;
		for (NbtTag tag : this.items)
		{
			copy.items.add(tag.copy());
		}
		return copy;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof NbtList && this.items.equals(((NbtList) other).items);
	}

	@Override
	public int hashCode()
	{
		return this.items.hashCode();
	}

	@Override
	public String toString()
	{
		return this.items.toString();
	}
}
