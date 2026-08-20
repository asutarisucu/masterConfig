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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regressions for the cases where a component carries more than the normalized form can express.
 *
 * <p>The promise is that nothing is lost between two versions that spell items the same way. That only
 * holds if such a component is left in {@code Extra} instead of being consumed, so these tests pin down
 * the fields the earlier implementation quietly dropped
 */
class ComponentFidelityTest
{
	private static final int DV_1_21_1 = 3955;   // components, wrapped, custom_model_data still an int
	private static final int DV_1_21_4 = 4189;   // custom_model_data expanded
	private static final int DV_1_21_11 = 4600;  // text as nbt, components unwrapped
	private static final int DV_1_20_1 = 3337;   // legacy

	private static NbtCompound componentItem(String id, NbtCompound components)
	{
		NbtCompound item = new NbtCompound();
		item.putString("id", id);
		item.putInt("count", 1);
		item.putCompound("components", components);
		return item;
	}

	/** the same version has to get its own bytes back, whatever the component holds */
	private static void assertIdentityRoundTrip(NbtCompound item, int dataVersion)
	{
		assertEquals(item, ItemNormalizer.fromCommon(ItemNormalizer.toCommon(item, dataVersion), dataVersion));
	}

	@Test
	void unbreakableKeepsItsTooltipFlag()
	{
		NbtCompound unbreakable = new NbtCompound();
		unbreakable.putByte("show_in_tooltip", (byte) 0);
		NbtCompound components = new NbtCompound();
		components.putCompound("minecraft:unbreakable", unbreakable);
		NbtCompound item = componentItem("minecraft:diamond_sword", components);

		assertIdentityRoundTrip(item, DV_1_21_1);
		// and Common still knows the item is unbreakable, so an older version can rebuild it
		assertEquals(1, ItemNormalizer.toCommon(item, DV_1_21_1).getInt(Mchf.ITEM_UNBREAKABLE, -1));
	}

	@Test
	void dyedColourKeepsItsTooltipFlag()
	{
		NbtCompound dyed = new NbtCompound();
		dyed.putInt("rgb", 0x8040FF);
		dyed.putByte("show_in_tooltip", (byte) 0);
		NbtCompound components = new NbtCompound();
		components.putCompound("minecraft:dyed_color", dyed);
		NbtCompound item = componentItem("minecraft:leather_helmet", components);

		assertIdentityRoundTrip(item, DV_1_21_1);
		assertEquals(0x8040FF, ItemNormalizer.toCommon(item, DV_1_21_1).getInt(Mchf.ITEM_DYED_COLOR, -1));
	}

	@Test
	void expandedCustomModelDataKeepsItsFlagsStringsAndColours()
	{
		NbtCompound cmd = new NbtCompound();
		cmd.putList("floats", NbtList.of(NbtTag.ofFloat(12)));
		cmd.putList("flags", NbtList.of(NbtTag.ofByte((byte) 1)));
		cmd.putList("strings", NbtList.of(NbtTag.ofString("variant")));
		cmd.putList("colors", NbtList.of(NbtTag.ofInt(0xFF0000)));
		NbtCompound components = new NbtCompound();
		components.putCompound("minecraft:custom_model_data", cmd);
		NbtCompound item = componentItem("minecraft:stick", components);

		assertIdentityRoundTrip(item, DV_1_21_4);
		assertEquals(12, ItemNormalizer.toCommon(item, DV_1_21_4).getInt(Mchf.ITEM_CUSTOM_MODEL_DATA, -1));
	}

	@Test
	void potionContentsKeepItsCustomName()
	{
		NbtCompound contents = new NbtCompound();
		contents.putString("potion", "minecraft:strong_healing");
		contents.putString("custom_name", "Healing Draught");
		NbtCompound components = new NbtCompound();
		components.putCompound("minecraft:potion_contents", contents);
		NbtCompound item = componentItem("minecraft:potion", components);

		assertIdentityRoundTrip(item, DV_1_21_1);
		assertEquals("minecraft:strong_healing",
				ItemNormalizer.toCommon(item, DV_1_21_1).getCompound(Mchf.ITEM_POTION).getString(Mchf.ITEM_POTION_ID, null));
	}

	/** the codec accepts a bare potion id instead of the whole record */
	@Test
	void aBarePotionIdIsUnderstood()
	{
		NbtCompound components = new NbtCompound();
		components.putString("minecraft:potion_contents", "minecraft:swiftness");
		NbtCompound item = componentItem("minecraft:potion", components);

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_21_1);
		assertEquals("minecraft:swiftness", common.getCompound(Mchf.ITEM_POTION).getString(Mchf.ITEM_POTION_ID, null));

		// it comes back in the long form, which the same codec accepts
		NbtCompound rebuilt = ItemNormalizer.fromCommon(common, DV_1_20_1);
		assertEquals("minecraft:swiftness", rebuilt.getCompound("tag").getString("Potion", null));
	}

	/**
	 * An nbt list holds one type only. Lore whose lines are not all valid json objects used to make the
	 * rebuild throw, which aborted the whole hotbar conversion
	 */
	@Test
	void loreThatIsNotAllJsonObjectsStillConverts()
	{
		NbtCompound display = new NbtCompound();
		display.putList("Lore", NbtList.of(
				NbtTag.ofString("{\"text\":\"a proper component\"}"),
				NbtTag.ofString("just some text")
		));
		NbtCompound tag = new NbtCompound();
		tag.putCompound("display", display);
		NbtCompound item = new NbtCompound();
		item.putString("id", "minecraft:stone");
		item.putByte("Count", (byte) 1);
		item.putCompound("tag", tag);

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_20_1);
		NbtList lore = ItemNormalizer.fromCommon(common, DV_1_21_11).getCompound("components").getList("minecraft:lore");

		assertNotNull(lore);
		assertEquals(2, lore.size());
		// both fell back to the plain string form rather than blowing up
		assertEquals(NbtTag.STRING, lore.get(0).type());
		assertEquals(NbtTag.STRING, lore.get(1).type());
	}

	@Test
	void loreThatIsAllJsonObjectsBecomesNbt()
	{
		NbtCompound display = new NbtCompound();
		display.putList("Lore", NbtList.of(
				NbtTag.ofString("{\"text\":\"one\"}"),
				NbtTag.ofString("{\"text\":\"two\"}")
		));
		NbtCompound tag = new NbtCompound();
		tag.putCompound("display", display);
		NbtCompound item = new NbtCompound();
		item.putString("id", "minecraft:stone");
		item.putByte("Count", (byte) 1);
		item.putCompound("tag", tag);

		NbtList lore = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(item, DV_1_20_1), DV_1_21_11)
				.getCompound("components").getList("minecraft:lore");

		assertEquals(NbtTag.COMPOUND, lore.get(0).type());
		assertEquals("one", lore.get(0).asCompound().getString("text", null));
	}

	/**
	 * From 4290 on a page is a text component in nbt, and the codec also accepts the page on its own as a
	 * shorthand for {raw: page}. Both forms are compounds, so only the "raw" key tells them apart
	 */
	@Test
	void bookPagesInTheShorthandFormAreExpandedAndConverted()
	{
		NbtCompound bare = new NbtCompound();
		bare.putString("text", "page one");
		NbtCompound content = new NbtCompound();
		content.putList("pages", NbtList.of(NbtTag.ofCompound(bare)));
		NbtCompound components = new NbtCompound();
		components.putCompound("minecraft:written_book_content", content);
		NbtCompound item = componentItem("minecraft:written_book", components);

		// Common canonicalises it into the long form with the text as json
		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_21_11);
		NbtCompound page = common.getCompound(Mchf.ITEM_WRITTEN_BOOK).getList("pages").get(0).asCompound();
		assertEquals("{\"text\":\"page one\"}", page.getString("raw", null));

		// and it comes back as nbt again
		NbtTag raw = ItemNormalizer.fromCommon(common, DV_1_21_11)
				.getCompound("components").getCompound("minecraft:written_book_content")
				.getList("pages").get(0).asCompound().get("raw");
		assertEquals(NbtTag.COMPOUND, raw.type());
		assertEquals("page one", raw.asCompound().getString("text", null));
	}

	@Test
	void bookPagesInTheLongFormSurviveUnchanged()
	{
		NbtCompound text = new NbtCompound();
		text.putString("text", "page one");
		NbtCompound pair = new NbtCompound();
		pair.putCompound("raw", text);
		NbtCompound content = new NbtCompound();
		content.putString("author", "asutarisucu");
		content.putList("pages", NbtList.of(NbtTag.ofCompound(pair)));
		NbtCompound components = new NbtCompound();
		components.putCompound("minecraft:written_book_content", content);

		assertIdentityRoundTrip(componentItem("minecraft:written_book", components), DV_1_21_11);
	}

	/** nothing above may break the plain case, where Common really does hold everything */
	@Test
	void aPlainComponentItemLeavesNothingBehind()
	{
		NbtCompound components = new NbtCompound();
		components.putInt("minecraft:damage", 3);
		NbtCompound item = componentItem("minecraft:diamond_sword", components);

		NbtCompound common = ItemNormalizer.toCommon(item, DV_1_21_1);

		assertNull(common.getCompound(Mchf.ITEM_EXTRA));
		assertEquals(3, common.getInt(Mchf.ITEM_DAMAGE, -1));
		assertIdentityRoundTrip(item, DV_1_21_1);
	}
}
