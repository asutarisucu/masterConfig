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

package org.asutarisucu.masterConfig.mchf;

import org.asutarisucu.masterConfig.nbt.NbtCompound;
import org.asutarisucu.masterConfig.nbt.NbtIo;
import org.asutarisucu.masterConfig.nbt.NbtList;
import org.asutarisucu.masterConfig.nbt.NbtTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.asutarisucu.masterConfig.mchf.Mchf.FORMAT_VERSION;
import static org.asutarisucu.masterConfig.mchf.Mchf.HOTBAR_GROUPS;
import static org.asutarisucu.masterConfig.mchf.Mchf.KEY_COMMON;
import static org.asutarisucu.masterConfig.mchf.Mchf.KEY_FORMAT_VERSION;
import static org.asutarisucu.masterConfig.mchf.Mchf.KEY_HOTBARS;
import static org.asutarisucu.masterConfig.mchf.Mchf.KEY_LAST_SAVED_BY;
import static org.asutarisucu.masterConfig.mchf.Mchf.KEY_RAW;

/**
 * The shared {@code hotbars.dat}, and the two directions it is used in.
 *
 * <p>The mod never asks minecraft to convert anything. It writes a plain {@code hotbar.nbt} carrying the
 * DataVersion of whoever produced the content, and lets the vanilla loader run its own DataFixer over it.
 * That covers every "same or older" case exactly. Only content produced by a *newer* version has to be
 * rebuilt from the normalized Common form, because a DataFixer cannot run backwards
 */
public final class HotbarStore
{
	private final List<Group> groups = new ArrayList<>();

	/** one saved hotbar out of the nine */
	public static final class Group
	{
		/** DataVersion of the instance that wrote this group last, or 0 when it was never written */
		public int lastSavedBy;
		/** the vanilla item list as {@code lastSavedBy} spelled it, or null */
		public NbtList raw;
		/** the same nine slots in the version neutral form, or null */
		public NbtList common;
	}

	public HotbarStore()
	{
		for (int i = 0; i < HOTBAR_GROUPS; i++)
		{
			this.groups.add(new Group());
		}
	}

	public Group group(int index)
	{
		return this.groups.get(index);
	}

	public static HotbarStore read(Path file) throws IOException
	{
		HotbarStore store = new HotbarStore();
		if (!Files.isRegularFile(file))
		{
			return store;
		}
		NbtCompound root = NbtIo.read(file);
		NbtList hotbars = root.getList(KEY_HOTBARS);
		if (hotbars == null)
		{
			return store;
		}
		for (int i = 0; i < Math.min(HOTBAR_GROUPS, hotbars.size()); i++)
		{
			NbtCompound entry = hotbars.get(i).asCompound();
			if (entry == null)
			{
				continue;
			}
			Group group = store.group(i);
			group.lastSavedBy = entry.getInt(KEY_LAST_SAVED_BY, 0);
			group.raw = entry.getList(KEY_RAW);
			group.common = entry.getList(KEY_COMMON);
		}
		return store;
	}

	public void write(Path file) throws IOException
	{
		NbtCompound root = new NbtCompound();
		root.putInt(KEY_FORMAT_VERSION, FORMAT_VERSION);
		NbtList hotbars = new NbtList();
		for (Group group : this.groups)
		{
			NbtCompound entry = new NbtCompound();
			entry.putInt(KEY_LAST_SAVED_BY, group.lastSavedBy);
			if (group.raw != null)
			{
				entry.putList(KEY_RAW, group.raw);
			}
			if (group.common != null)
			{
				entry.putList(KEY_COMMON, group.common);
			}
			hotbars.add(NbtTag.ofCompound(entry));
		}
		root.putList(KEY_HOTBARS, hotbars);
		NbtIo.write(file, root, true);
	}

	/**
	 * Builds the {@code hotbar.nbt} that the given version should see.
	 *
	 * @param dataVersion the DataVersion of the running instance
	 * @return the root tag to write, or null when there is nothing shared yet
	 */
	public NbtCompound toNativeHotbarFile(int dataVersion)
	{
		// the file carries exactly one DataVersion and the vanilla loader applies its fixers to the whole file,
		// so every group in it has to be in the shape of that one version. Pick the version that lets the most
		// groups be handed over untouched, and rebuild the remaining ones from Common into that same shape
		int fileVersion = pickFileVersion(dataVersion);

		NbtCompound root = new NbtCompound();
		boolean any = false;
		for (int i = 0; i < HOTBAR_GROUPS; i++)
		{
			Group group = this.groups.get(i);
			NbtList items;
			if (group.raw != null && group.lastSavedBy == fileVersion)
			{
				items = group.raw.copy();  // exact, the fixer will lift it from fileVersion to the current one
			}
			else if (group.common != null)
			{
				items = commonToNative(group.common, fileVersion);
			}
			else
			{
				continue;
			}
			root.putList(String.valueOf(i), items);
			any = true;
		}
		if (!any)
		{
			return null;
		}
		root.putInt("DataVersion", fileVersion);
		return root;
	}

	/**
	 * @return the DataVersion most groups were last saved by, among those the vanilla DataFixer can still
	 * lift to {@code dataVersion}. Falls back to {@code dataVersion}, which makes the fixer a no-op
	 */
	private int pickFileVersion(int dataVersion)
	{
		int best = dataVersion;
		int bestCount = 0;
		for (Group candidate : this.groups)
		{
			if (candidate.raw == null || candidate.lastSavedBy > dataVersion || candidate.lastSavedBy <= 0)
			{
				continue;
			}
			int count = 0;
			for (Group other : this.groups)
			{
				if (other.raw != null && other.lastSavedBy == candidate.lastSavedBy)
				{
					count++;
				}
			}
			if (count > bestCount || (count == bestCount && candidate.lastSavedBy > best))
			{
				best = candidate.lastSavedBy;
				bestCount = count;
			}
		}
		return best;
	}

	/**
	 * Takes in a {@code hotbar.nbt} the game has just written and makes it the shared content.
	 *
	 * <p>The game rewrites all nine groups whenever the player saves one of them, so taking the file at face
	 * value would let a single save in an old version flatten the eight groups it merely displayed.
	 * A group whose contents did not actually change is therefore left exactly as it was, which keeps the
	 * high fidelity Raw of whichever version really wrote it
	 *
	 * @return how many groups were actually replaced
	 */
	public int absorbNativeHotbarFile(NbtCompound root, int dataVersion)
	{
		int replaced = 0;
		for (int i = 0; i < HOTBAR_GROUPS; i++)
		{
			NbtList items = root.getList(String.valueOf(i));
			Group group = this.groups.get(i);
			if (items == null)
			{
				continue;
			}
			NbtList common = nativeToCommon(items, dataVersion);
			if (group.common != null && sameContents(group.common, common))
			{
				continue;
			}
			group.lastSavedBy = dataVersion;
			group.raw = items.copy();
			group.common = common;
			replaced++;
		}
		return replaced;
	}

	/**
	 * Compares two Common rows ignoring {@code Extra}, which legitimately differs between two versions
	 * holding the very same items
	 */
	static boolean sameContents(NbtList left, NbtList right)
	{
		return stripExtra(left).equals(stripExtra(right));
	}

	private static NbtList stripExtra(NbtList row)
	{
		NbtList stripped = new NbtList();
		for (NbtTag item : row.items())
		{
			NbtCompound copy = item.asCompound() == null ? new NbtCompound() : item.asCompound().copy();
			stripExtra(copy);
			stripped.add(NbtTag.ofCompound(copy));
		}
		return stripped;
	}

	private static void stripExtra(NbtCompound commonItem)
	{
		commonItem.remove(Mchf.ITEM_EXTRA);
		commonItem.remove(Mchf.ITEM_EXTRA_FROM);
		NbtList container = commonItem.getList(Mchf.ITEM_CONTAINER);
		if (container != null)
		{
			for (NbtTag entry : container.items())
			{
				NbtCompound inner = entry.asCompound() == null ? null : entry.asCompound().getCompound(Mchf.ITEM_CONTAINER_ITEM);
				if (inner != null)
				{
					stripExtra(inner);
				}
			}
		}
	}

	static NbtList nativeToCommon(NbtList items, int dataVersion)
	{
		NbtList common = new NbtList();
		for (NbtTag item : items.items())
		{
			common.add(NbtTag.ofCompound(ItemNormalizer.toCommon(item.asCompound(), dataVersion)));
		}
		return common;
	}

	static NbtList commonToNative(NbtList common, int dataVersion)
	{
		NbtList items = new NbtList();
		for (NbtTag item : common.items())
		{
			items.add(NbtTag.ofCompound(ItemNormalizer.fromCommon(item.asCompound(), dataVersion)));
		}
		return items;
	}
}
