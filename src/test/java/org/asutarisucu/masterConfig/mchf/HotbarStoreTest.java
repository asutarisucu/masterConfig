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
import org.asutarisucu.masterConfig.nbt.NbtList;
import org.asutarisucu.masterConfig.nbt.NbtTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotbarStoreTest
{
	private static final int DV_1_18_2 = 2975;
	private static final int DV_1_20_1 = 3337;
	private static final int DV_1_21_1 = 3955;

	@TempDir
	Path tmp;

	private static NbtList legacyRow(String id, int count)
	{
		NbtList row = new NbtList();
		NbtCompound item = new NbtCompound();
		item.putString("id", id);
		item.putByte("Count", (byte) count);
		row.add(NbtTag.ofCompound(item));
		for (int i = 1; i < Mchf.HOTBAR_SIZE; i++)
		{
			row.add(NbtTag.ofCompound(new NbtCompound()));
		}
		return row;
	}

	private static NbtList componentRow(String id, int count)
	{
		NbtList row = new NbtList();
		NbtCompound item = new NbtCompound();
		item.putString("id", id);
		item.putInt("count", count);
		row.add(NbtTag.ofCompound(item));
		for (int i = 1; i < Mchf.HOTBAR_SIZE; i++)
		{
			row.add(NbtTag.ofCompound(new NbtCompound()));
		}
		return row;
	}

	private static NbtCompound nativeFile(int dataVersion, int group, NbtList row)
	{
		NbtCompound root = new NbtCompound();
		root.putInt("DataVersion", dataVersion);
		root.putList(String.valueOf(group), row);
		return root;
	}

	@Test
	void anEmptyStoreProducesNoFile()
	{
		assertNull(new HotbarStore().toNativeHotbarFile(DV_1_21_1));
	}

	@Test
	void storeSurvivesAWriteReadRoundTrip() throws IOException
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_20_1, 2, legacyRow("minecraft:stone", 64)), DV_1_20_1);
		Path file = this.tmp.resolve("hotbars.dat");

		store.write(file);
		HotbarStore reloaded = HotbarStore.read(file);

		assertEquals(DV_1_20_1, reloaded.group(2).lastSavedBy);
		assertEquals(store.group(2).raw, reloaded.group(2).raw);
		assertEquals(store.group(2).common, reloaded.group(2).common);
	}

	@Test
	void readingAMissingFileGivesAnEmptyStore() throws IOException
	{
		assertNull(HotbarStore.read(this.tmp.resolve("nope.dat")).group(0).raw);
	}

	@Test
	void theSameVersionGetsItsOwnBytesBack()
	{
		HotbarStore store = new HotbarStore();
		NbtList row = legacyRow("minecraft:stone", 64);
		store.absorbNativeHotbarFile(nativeFile(DV_1_20_1, 0, row), DV_1_20_1);

		NbtCompound out = store.toNativeHotbarFile(DV_1_20_1);

		assertEquals(DV_1_20_1, out.getInt("DataVersion", -1));
		assertEquals(row, out.getList("0"));
	}

	/**
	 * An older version saved it, so the file keeps that DataVersion and the raw content untouched.
	 * The vanilla loader is the one that lifts it, which is exactly lossless
	 */
	@Test
	void anOlderGroupIsHandedToTheVanillaFixerUntouched()
	{
		HotbarStore store = new HotbarStore();
		NbtList row = legacyRow("minecraft:stone", 64);
		store.absorbNativeHotbarFile(nativeFile(DV_1_18_2, 0, row), DV_1_18_2);

		NbtCompound out = store.toNativeHotbarFile(DV_1_21_1);

		assertEquals(DV_1_18_2, out.getInt("DataVersion", -1));
		assertEquals(row, out.getList("0"));
	}

	/**
	 * A newer version saved it, and a DataFixer cannot run backwards, so it has to come from Common
	 */
	@Test
	void aNewerGroupIsRebuiltFromCommonInTheOldShape()
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_21_1, 0, componentRow("minecraft:stone", 64)), DV_1_21_1);

		NbtCompound out = store.toNativeHotbarFile(DV_1_18_2);

		assertEquals(DV_1_18_2, out.getInt("DataVersion", -1));
		NbtCompound item = out.getList("0").get(0).asCompound();
		assertEquals("minecraft:stone", item.getString("id", null));
		assertEquals(64, item.getInt("Count", -1));   // legacy spelling, not "count"
		assertNull(item.get("count"));
	}

	/**
	 * The file carries one DataVersion for all nine groups. Anything not matching it must be rebuilt into
	 * that same shape, otherwise the vanilla fixer would migrate already migrated groups
	 */
	@Test
	void groupsFromDifferentVersionsAreAllBroughtToOneShape()
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_18_2, 0, legacyRow("minecraft:stone", 64)), DV_1_18_2);
		store.absorbNativeHotbarFile(nativeFile(DV_1_18_2, 1, legacyRow("minecraft:dirt", 32)), DV_1_18_2);
		store.absorbNativeHotbarFile(nativeFile(DV_1_21_1, 2, componentRow("minecraft:oak_log", 16)), DV_1_21_1);

		NbtCompound out = store.toNativeHotbarFile(DV_1_21_1);

		// two of the three groups are 1.18.2, so that is the version the file claims
		assertEquals(DV_1_18_2, out.getInt("DataVersion", -1));
		for (String group : new String[]{"0", "1", "2"})
		{
			NbtCompound item = out.getList(group).get(0).asCompound();
			assertNull(item.get("count"), "group " + group + " must be in the 1.18.2 shape");
			assertTrue(item.getInt("Count", -1) > 0, "group " + group);
		}
		assertEquals("minecraft:oak_log", out.getList("2").get(0).asCompound().getString("id", null));
	}

	@Test
	void savingReplacesWhatWasThereBeforeSoTheVersionsCannotDisagree()
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_21_1, 0, componentRow("minecraft:oak_log", 16)), DV_1_21_1);
		NbtList before = store.group(0).raw;

		store.absorbNativeHotbarFile(nativeFile(DV_1_18_2, 0, legacyRow("minecraft:stone", 64)), DV_1_18_2);

		assertEquals(DV_1_18_2, store.group(0).lastSavedBy);
		assertNotEquals(before, store.group(0).raw);
		assertEquals("minecraft:stone", store.group(0).common.get(0).asCompound().getString(Mchf.ITEM_ID, null));
	}

	/**
	 * The game rewrites all nine groups on every save. An old version that merely displayed the other eight
	 * must not end up owning them, or their high fidelity Raw would be replaced by its own downgraded view
	 */
	@Test
	void savingInAnOldVersionDoesNotTakeOverTheGroupsItOnlyDisplayed()
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_21_1, 0, componentRow("minecraft:oak_log", 16)), DV_1_21_1);
		store.absorbNativeHotbarFile(nativeFile(DV_1_21_1, 1, componentRow("minecraft:stone", 64)), DV_1_21_1);

		// 1.18.2 gets the whole thing rebuilt from Common, changes group 1, and saves everything back
		NbtCompound shown = store.toNativeHotbarFile(DV_1_18_2);
		shown.putList("1", legacyRow("minecraft:dirt", 1));
		int replaced = store.absorbNativeHotbarFile(shown, DV_1_18_2);

		assertEquals(1, replaced);
		assertEquals(DV_1_21_1, store.group(0).lastSavedBy, "group 0 was untouched, it must stay owned by 1.21.1");
		assertEquals(DV_1_18_2, store.group(1).lastSavedBy);
		assertEquals("minecraft:dirt", store.group(1).common.get(0).asCompound().getString(Mchf.ITEM_ID, null));
	}

	@Test
	void reSavingTheExactSameContentChangesNothing()
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_21_1, 3, componentRow("minecraft:stone", 64)), DV_1_21_1);

		assertEquals(0, store.absorbNativeHotbarFile(store.toNativeHotbarFile(DV_1_21_1), DV_1_21_1));
	}

	@Test
	void untouchedGroupsAreKeptWhenAnotherGroupIsSaved()
	{
		HotbarStore store = new HotbarStore();
		store.absorbNativeHotbarFile(nativeFile(DV_1_20_1, 5, legacyRow("minecraft:stone", 64)), DV_1_20_1);

		// the game always writes all nine groups, but a file that only carries one must not wipe the rest
		store.absorbNativeHotbarFile(nativeFile(DV_1_20_1, 6, legacyRow("minecraft:dirt", 1)), DV_1_20_1);

		assertEquals("minecraft:stone", store.group(5).common.get(0).asCompound().getString(Mchf.ITEM_ID, null));
		assertEquals("minecraft:dirt", store.group(6).common.get(0).asCompound().getString(Mchf.ITEM_ID, null));
	}
}
