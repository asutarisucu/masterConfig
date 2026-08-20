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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemNormalizerTest
{
	@TempDir
	Path tmp;

	private static final int DV_1_18_2 = 2975;
	private static final int DV_1_20_1 = 3337;
	private static final int DV_1_21_1 = 3955;   // components, custom_model_data still an int, text still json
	private static final int DV_1_21_4 = 4189;   // custom_model_data expanded
	private static final int DV_1_21_11 = 4600;  // text components are nbt

	private static NbtCompound legacyItem()
	{
		NbtCompound tag = new NbtCompound();
		tag.putInt("Damage", 3);
		tag.putInt("RepairCost", 7);
		tag.putByte("Unbreakable", (byte) 1);
		tag.putInt("CustomModelData", 12);

		NbtCompound enchantment = new NbtCompound();
		enchantment.putString("id", "minecraft:mending");
		enchantment.put("lvl", NbtTag.ofShort((short) 1));
		tag.putList("Enchantments", NbtList.of(NbtTag.ofCompound(enchantment)));

		NbtCompound display = new NbtCompound();
		display.putString("Name", "{\"text\":\"Sharp\"}");
		display.putList("Lore", NbtList.of(NbtTag.ofString("{\"text\":\"line one\"}"), NbtTag.ofString("{\"text\":\"line two\"}")));
		tag.putCompound("display", display);

		NbtCompound item = new NbtCompound();
		item.putString("id", "minecraft:diamond_sword");
		item.putByte("Count", (byte) 1);
		item.putCompound("tag", tag);
		return item;
	}

	private static NbtCompound legacyShulker()
	{
		NbtCompound stone = new NbtCompound();
		stone.putString("id", "minecraft:stone");
		stone.putByte("Count", (byte) 64);
		stone.putByte("Slot", (byte) 3);

		NbtCompound blockEntity = new NbtCompound();
		blockEntity.putString("id", "minecraft:shulker_box");
		blockEntity.putList("Items", NbtList.of(NbtTag.ofCompound(stone)));

		NbtCompound tag = new NbtCompound();
		tag.putCompound("BlockEntityTag", blockEntity);

		NbtCompound item = new NbtCompound();
		item.putString("id", "minecraft:shulker_box");
		item.putByte("Count", (byte) 1);
		item.putCompound("tag", tag);
		return item;
	}

	@Test
	void legacyItemRoundTripsThroughCommon()
	{
		NbtCompound original = legacyItem();

		NbtCompound common = ItemNormalizer.toCommon(original, DV_1_20_1);
		NbtCompound back = ItemNormalizer.fromCommon(common, DV_1_20_1);

		assertEquals(original, back);
	}

	@Test
	void legacyShulkerRoundTripsIncludingItsContents()
	{
		NbtCompound original = legacyShulker();

		NbtCompound common = ItemNormalizer.toCommon(original, DV_1_20_1);
		NbtCompound back = ItemNormalizer.fromCommon(common, DV_1_20_1);

		assertEquals(original, back);
	}

	@Test
	void legacyEnchantmentIdsAreNamespacedInCommon()
	{
		// real 1.20.1 files store the id unnamespaced, Common always carries the canonical form
		NbtCompound item = legacyItem();
		NbtCompound enchantment = item.getCompound("tag").getList("Enchantments").get(0).asCompound();
		enchantment.putString("id", "mending");

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_20_1);

		NbtCompound enchantments = common.getCompound(Mchf.ITEM_ENCHANTMENTS);
		assertNotNull(enchantments);
		assertEquals(1, enchantments.getInt("minecraft:mending", 0));

		// and it comes back namespaced, which every supported version accepts
		NbtCompound back = ItemNormalizer.fromCommon(common, DV_1_20_1);
		assertEquals("minecraft:mending", back.getCompound("tag").getList("Enchantments").get(0).asCompound().getString("id", null));
	}

	@Test
	void nothingIsLeftInExtraForAFullyMappedLegacyItem()
	{
		assertNull(ItemNormalizer.toCommon(legacyItem(), DV_1_20_1).getCompound(Mchf.ITEM_EXTRA));
	}

	@Test
	void unmappedLegacyKeysSurviveInExtra()
	{
		NbtCompound item = legacyItem();
		item.getCompound("tag").putInt("HideFlags", 4);

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_20_1);
		NbtCompound extra = common.getCompound(Mchf.ITEM_EXTRA);

		assertNotNull(extra);
		assertEquals(4, extra.getInt("HideFlags", -1));
		// and it comes back, because 1.18.2 and 1.20.1 write items the same way
		assertEquals(4, ItemNormalizer.fromCommon(common, DV_1_18_2).getCompound("tag").getInt("HideFlags", -1));
	}

	@Test
	void extraIsNotPastedIntoADifferentShape()
	{
		NbtCompound item = legacyItem();
		item.getCompound("tag").putInt("HideFlags", 4);
		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_20_1);

		NbtCompound rebuilt = ItemNormalizer.fromCommon(common, DV_1_21_1);

		// a legacy HideFlags int must never end up among the components
		assertNull(rebuilt.getCompound("components").get("HideFlags"));
	}

	@Test
	void legacyItemBecomesAComponentItemAndBack()
	{
		NbtCompound common = ItemNormalizer.toCommon(legacyItem(), DV_1_20_1);

		NbtCompound modern = ItemNormalizer.fromCommon(common, DV_1_21_1);
		NbtCompound components = modern.getCompound("components");

		assertEquals(1, modern.getInt("count", -1));
		assertEquals(3, components.getInt("minecraft:damage", -1));
		assertEquals(7, components.getInt("minecraft:repair_cost", -1));
		assertNotNull(components.getCompound("minecraft:unbreakable"));
		assertEquals(12, components.getInt("minecraft:custom_model_data", -1));
		assertEquals(1, components.getCompound("minecraft:enchantments").getCompound("levels").getInt("minecraft:mending", 0));
		assertEquals("{\"text\":\"Sharp\"}", components.getString("minecraft:custom_name", null));
		assertEquals(2, components.getList("minecraft:lore").size());

		// and all the way back down
		NbtCompound backToLegacy = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(modern, DV_1_21_1), DV_1_20_1);
		assertEquals(legacyItem(), backToLegacy);
	}

	@Test
	void customModelDataIsExpandedFrom1214AndReadBack()
	{
		NbtCompound common = ItemNormalizer.toCommon(legacyItem(), DV_1_20_1);

		NbtCompound expanded = ItemNormalizer.fromCommon(common, DV_1_21_4);
		NbtList floats = expanded.getCompound("components").getCompound("minecraft:custom_model_data").getList("floats");
		assertEquals(1, floats.size());
		assertEquals(12, floats.get(0).asInt(-1));

		assertEquals(12, ItemNormalizer.toCommon(expanded, DV_1_21_4).getInt(Mchf.ITEM_CUSTOM_MODEL_DATA, -1));
	}

	@Test
	void textComponentsBecomeNbtFrom12111AndComeBackAsJson()
	{
		NbtCompound common = ItemNormalizer.toCommon(legacyItem(), DV_1_20_1);

		NbtCompound modern = ItemNormalizer.fromCommon(common, DV_1_21_11);
		NbtTag name = modern.getCompound("components").get("minecraft:custom_name");
		assertEquals(NbtTag.COMPOUND, name.type());
		assertEquals("Sharp", name.asCompound().getString("text", null));

		assertEquals("{\"text\":\"Sharp\"}", ItemNormalizer.toCommon(modern, DV_1_21_11).getString(Mchf.ITEM_CUSTOM_NAME, null));
	}

	@Test
	void shulkerContentsSurviveTheJumpToComponents()
	{
		NbtCompound common = ItemNormalizer.toCommon(legacyShulker(), DV_1_20_1);

		NbtList container = ItemNormalizer.fromCommon(common, DV_1_21_1).getCompound("components").getList("minecraft:container");

		assertEquals(1, container.size());
		NbtCompound entry = container.get(0).asCompound();
		assertEquals(3, entry.getInt("slot", -1));
		assertEquals("minecraft:stone", entry.getCompound("item").getString("id", null));
		assertEquals(64, entry.getCompound("item").getInt("count", -1));
	}

	/**
	 * TooltipDisplayComponentFix unwrapped several components at 4307, so from 1.21.5 on the
	 * enchantment component is the id to level map itself rather than {@code {levels: ...}}
	 */
	@Test
	void enchantmentsAreFlatFrom12105()
	{
		NbtCompound common = ItemNormalizer.toCommon(legacyItem(), DV_1_20_1);

		NbtCompound wrapped = ItemNormalizer.fromCommon(common, DV_1_21_1).getCompound("components");
		assertEquals(1, wrapped.getCompound("minecraft:enchantments").getCompound("levels").getInt("minecraft:mending", 0));

		NbtCompound flat = ItemNormalizer.fromCommon(common, DV_1_21_11).getCompound("components");
		assertEquals(1, flat.getCompound("minecraft:enchantments").getInt("minecraft:mending", 0));
		assertNull(flat.getCompound("minecraft:enchantments").get("levels"));

		// and reading the flat form back gives the same Common
		assertEquals(1, ItemNormalizer.toCommon(ItemNormalizer.fromCommon(common, DV_1_21_11), DV_1_21_11)
				.getCompound(Mchf.ITEM_ENCHANTMENTS).getInt("minecraft:mending", 0));
	}

	@Test
	void dyedColourCrossesEveryShape()
	{
		NbtCompound item = legacyItem();
		item.getCompound("tag").getCompound("display").putInt("color", 0x8040FF);

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_20_1);
		assertEquals(0x8040FF, common.getInt(Mchf.ITEM_DYED_COLOR, -1));

		// wrapped up to 1.21.4, a plain int from 1.21.5
		assertEquals(0x8040FF, ItemNormalizer.fromCommon(common, DV_1_21_1)
				.getCompound("components").getCompound("minecraft:dyed_color").getInt("rgb", -1));
		assertEquals(0x8040FF, ItemNormalizer.fromCommon(common, DV_1_21_11)
				.getCompound("components").getInt("minecraft:dyed_color", -1));

		// and all the way back down
		assertEquals(item, ItemNormalizer.fromCommon(
				ItemNormalizer.toCommon(ItemNormalizer.fromCommon(common, DV_1_21_11), DV_1_21_11), DV_1_20_1));
	}

	@Test
	void potionContentsCrossTheComponentBoundary()
	{
		NbtCompound tag = new NbtCompound();
		tag.putString("Potion", "minecraft:strong_healing");
		tag.putInt("CustomPotionColor", 0x112233);
		NbtCompound item = new NbtCompound();
		item.putString("id", "minecraft:potion");
		item.putByte("Count", (byte) 1);
		item.putCompound("tag", tag);

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_20_1);
		assertEquals("minecraft:strong_healing", common.getCompound(Mchf.ITEM_POTION).getString(Mchf.ITEM_POTION_ID, null));

		NbtCompound contents = ItemNormalizer.fromCommon(common, DV_1_21_1)
				.getCompound("components").getCompound("minecraft:potion_contents");
		assertEquals("minecraft:strong_healing", contents.getString("potion", null));
		assertEquals(0x112233, contents.getInt("custom_color", -1));

		assertEquals(item, ItemNormalizer.fromCommon(
				ItemNormalizer.toCommon(ItemNormalizer.fromCommon(common, DV_1_21_1), DV_1_21_1), DV_1_20_1));
	}

	/**
	 * The sub tier decides whether unnormalized leftovers can be pasted over unchanged. Versions whose
	 * item spelling differs anywhere must land in different tiers, or Extra would carry a wrong shape
	 */
	@Test
	void versionsWithDifferentItemSpellingAreInDifferentSubTiers()
	{
		int[] versions = {DV_1_18_2, 3839 /* 1.20.6 */, DV_1_21_1, 4082 /* 1.21.3 */, DV_1_21_4, 4325 /* 1.21.5 */};
		for (int i = 1; i < versions.length; i++)
		{
			assertNotEquals(Mchf.subTier(versions[i - 1]), Mchf.subTier(versions[i]),
					"DataVersion " + versions[i - 1] + " and " + versions[i] + " must not share a sub tier");
		}
		// 1.21.5 and everything after it write items the same way
		assertEquals(Mchf.subTier(4325), Mchf.subTier(4440));
	}

	@Test
	void anEmptySlotStaysEmpty()
	{
		assertTrue(ItemNormalizer.toCommon(new NbtCompound(), DV_1_20_1).isEmpty());
		assertTrue(ItemNormalizer.fromCommon(new NbtCompound(), DV_1_21_1).isEmpty());
	}

	/**
	 * A whole hotbar file, shaped the way the game writes one: nine groups of nine slots, empty slots stored
	 * as {@code minecraft:air} with a count of zero, and items ranging from a bare block to a shulker box
	 * with contents. Every item has to survive Common and come back unchanged.
	 *
	 * <p>The same check was run locally against fifteen real {@code hotbar.nbt} files, the largest 87 MB,
	 * but those hold other people's server content so they are not part of the repository
	 */
	@Test
	void awholeHotbarFileRoundTripsItemByItem()
	{
		NbtCompound root = syntheticHotbarFile();
		int dataVersion = root.getInt("DataVersion", -1);

		int checked = 0;
		for (int slot = 0; slot < Mchf.HOTBAR_GROUPS; slot++)
		{
			NbtList hotbar = root.getList(String.valueOf(slot));
			if (hotbar == null)
			{
				continue;
			}
			for (NbtTag item : hotbar.items())
			{
				NbtCompound original = item.asCompound();
				NbtCompound common = ItemNormalizer.toCommon(original, dataVersion);
				NbtCompound back = ItemNormalizer.fromCommon(common, dataVersion);
				if (ItemNormalizer.isEmptyItem(original))
				{
					assertTrue(back.isEmpty(), "slot " + slot);
					continue;
				}
				assertEquals(original, back, "slot " + slot);
				checked++;
			}
		}
		assertTrue(checked >= 6, "the fixture should carry a few real items, had " + checked);
	}

	@Test
	void awholeHotbarFileSurvivesTheFileFormatToo() throws IOException
	{
		Path file = this.tmp.resolve("hotbar.nbt");
		NbtIo.write(file, syntheticHotbarFile(), false);

		assertEquals(syntheticHotbarFile(), NbtIo.read(file));
	}

	private static NbtCompound syntheticHotbarFile()
	{
		NbtCompound root = new NbtCompound();
		root.putInt("DataVersion", DV_1_20_1);
		root.putList("0", NbtList.of(
				NbtTag.ofCompound(plain("minecraft:stone", 64)),
				NbtTag.ofCompound(legacyItem()),
				NbtTag.ofCompound(damaged("minecraft:wooden_axe", 7)),
				NbtTag.ofCompound(legacyShulker()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air())
		));
		root.putList("1", NbtList.of(
				NbtTag.ofCompound(plain("minecraft:redstone", 1)),
				NbtTag.ofCompound(plain("minecraft:repeater", 1)),
				NbtTag.ofCompound(plain("minecraft:comparator", 1)),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air()),
				NbtTag.ofCompound(air())
		));
		// the remaining groups are the empty rows the game still writes out
		for (int i = 2; i < Mchf.HOTBAR_GROUPS; i++)
		{
			NbtList row = new NbtList();
			for (int j = 0; j < Mchf.HOTBAR_SIZE; j++)
			{
				row.add(NbtTag.ofCompound(air()));
			}
			root.putList(String.valueOf(i), row);
		}
		return root;
	}

	private static NbtCompound air()
	{
		NbtCompound item = new NbtCompound();
		item.putString("id", "minecraft:air");
		item.putByte("Count", (byte) 0);
		return item;
	}

	private static NbtCompound plain(String id, int count)
	{
		NbtCompound item = new NbtCompound();
		item.putString("id", id);
		item.putByte("Count", (byte) count);
		return item;
	}

	private static NbtCompound damaged(String id, int damage)
	{
		NbtCompound tag = new NbtCompound();
		tag.putInt("Damage", damage);
		NbtCompound item = plain(id, 1);
		item.putCompound("tag", tag);
		return item;
	}
}
