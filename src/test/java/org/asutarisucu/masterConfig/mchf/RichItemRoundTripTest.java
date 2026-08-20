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
 * The item kinds that carry more than a name and a count.
 *
 * <p>Every expectation here was taken from the code that defines the shape, never guessed:
 * the legacy side from {@code ItemStackComponentizationFix}, the component side from the codecs in
 * {@code WrittenBookContentComponent}, {@code ProfileComponent}, {@code FireworksComponent},
 * {@code FireworkExplosionComponent} and {@code ArmorTrim}. Those codecs are byte for byte identical
 * in 1.21.2, 1.21.4, 1.21.8, 1.21.11 and 26.1
 */
class RichItemRoundTripTest
{
	private static final int DV_1_20_1 = 3337;
	private static final int DV_1_21_1 = 3955;
	private static final int DV_1_21_11 = 4600;

	private static NbtCompound legacyItem(String id, NbtCompound tag)
	{
		NbtCompound item = new NbtCompound();
		item.putString("id", id);
		item.putByte("Count", (byte) 1);
		item.putCompound("tag", tag);
		return item;
	}

	/** legacy -> Common -> component -> Common -> legacy has to land back on the original */
	private static void assertSurvivesBothWays(NbtCompound legacy, int componentVersion)
	{
		NbtCompound common = ItemNormalizer.toCommon(legacy, DV_1_20_1);
		NbtCompound modern = ItemNormalizer.fromCommon(common, componentVersion);
		NbtCompound back = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(modern, componentVersion), DV_1_20_1);
		assertEquals(legacy, back);
	}

	@Test
	void writtenBookKeepsItsTitleAuthorAndPages()
	{
		NbtCompound tag = new NbtCompound();
		tag.putString("title", "My Book");
		tag.putString("author", "asutarisucu");
		tag.putInt("generation", 1);
		tag.putByte("resolved", (byte) 1);
		tag.putList("pages", NbtList.of(
				NbtTag.ofString("{\"text\":\"page one\"}"),
				NbtTag.ofString("{\"text\":\"page two\"}")
		));
		NbtCompound legacy = legacyItem("minecraft:written_book", tag);

		NbtCompound content = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1)
				.getCompound("components").getCompound("minecraft:written_book_content");
		assertEquals("My Book", content.getCompound("title").getString("raw", null));
		assertEquals("asutarisucu", content.getString("author", null));
		assertEquals(2, content.getList("pages").size());
		assertEquals("{\"text\":\"page one\"}", content.getList("pages").get(0).asCompound().getString("raw", null));

		assertSurvivesBothWays(legacy, DV_1_21_1);
	}

	@Test
	void writtenBookPagesBecomeNbtTextFrom12105()
	{
		NbtCompound tag = new NbtCompound();
		tag.putString("title", "T");
		tag.putString("author", "A");
		tag.putList("pages", NbtList.of(NbtTag.ofString("{\"text\":\"hello\"}")));
		NbtCompound legacy = legacyItem("minecraft:written_book", tag);

		NbtCompound content = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_11)
				.getCompound("components").getCompound("minecraft:written_book_content");
		NbtTag page = content.getList("pages").get(0).asCompound().get("raw");
		assertEquals(NbtTag.COMPOUND, page.type());
		assertEquals("hello", page.asCompound().getString("text", null));
		// the title is a plain string even in the new format, so it must not have been converted
		assertEquals(NbtTag.STRING, content.getCompound("title").get("raw").type());

		assertSurvivesBothWays(legacy, DV_1_21_11);
	}

	@Test
	void filteredPagesKeepTheirIndexes()
	{
		NbtCompound filtered = new NbtCompound();
		filtered.putString("1", "{\"text\":\"clean\"}");
		NbtCompound tag = new NbtCompound();
		tag.putString("title", "T");
		tag.putString("author", "A");
		tag.putList("pages", NbtList.of(NbtTag.ofString("{\"text\":\"a\"}"), NbtTag.ofString("{\"text\":\"b\"}")));
		tag.putCompound("filtered_pages", filtered);
		NbtCompound legacy = legacyItem("minecraft:written_book", tag);

		NbtList pages = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1)
				.getCompound("components").getCompound("minecraft:written_book_content").getList("pages");
		assertNull(pages.get(0).asCompound().get("filtered"));
		assertEquals("{\"text\":\"clean\"}", pages.get(1).asCompound().getString("filtered", null));

		assertSurvivesBothWays(legacy, DV_1_21_1);
	}

	@Test
	void writableBookPagesStayPlainText()
	{
		NbtCompound tag = new NbtCompound();
		tag.putList("pages", NbtList.of(NbtTag.ofString("just text"), NbtTag.ofString("more text")));
		NbtCompound legacy = legacyItem("minecraft:writable_book", tag);

		NbtCompound content = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_11)
				.getCompound("components").getCompound("minecraft:writable_book_content");
		assertEquals("just text", content.getList("pages").get(0).asCompound().getString("raw", null));

		assertSurvivesBothWays(legacy, DV_1_21_11);
	}

	@Test
	void playerHeadProfileIsFlattenedAndGroupedBackAgain()
	{
		NbtCompound texture = new NbtCompound();
		texture.putString("Value", "base64data");
		texture.putString("Signature", "sig");
		NbtCompound properties = new NbtCompound();
		properties.putList("textures", NbtList.of(NbtTag.ofCompound(texture)));
		NbtCompound owner = new NbtCompound();
		owner.putString("Name", "Notch");
		owner.put("Id", NbtTag.ofIntArray(new int[]{1, 2, 3, 4}));
		owner.putCompound("Properties", properties);
		NbtCompound tag = new NbtCompound();
		tag.putCompound("SkullOwner", owner);
		NbtCompound legacy = legacyItem("minecraft:player_head", tag);

		NbtCompound profile = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1)
				.getCompound("components").getCompound("minecraft:profile");
		assertEquals("Notch", profile.getString("name", null));
		assertNotNull(profile.get("id"));
		NbtCompound property = profile.getList("properties").get(0).asCompound();
		assertEquals("textures", property.getString("name", null));
		assertEquals("base64data", property.getString("value", null));
		assertEquals("sig", property.getString("signature", null));

		assertSurvivesBothWays(legacy, DV_1_21_1);
	}

	@Test
	void aBareSkullOwnerNameBecomesAProfile()
	{
		NbtCompound tag = new NbtCompound();
		tag.putString("SkullOwner", "Notch");

		NbtCompound common = ItemNormalizer.toCommon(legacyItem("minecraft:player_head", tag), DV_1_20_1);

		assertEquals("Notch", common.getCompound(Mchf.ITEM_PROFILE).getString("name", null));
	}

	@Test
	void fireworkRocketKeepsItsFlightAndExplosions()
	{
		NbtCompound explosion = new NbtCompound();
		explosion.putInt("Type", 3);  // creeper
		explosion.put("Colors", NbtTag.ofIntArray(new int[]{11743532, 3887386}));
		explosion.put("FadeColors", NbtTag.ofIntArray(new int[]{15790320}));
		explosion.putByte("Trail", (byte) 1);
		explosion.putByte("Flicker", (byte) 1);
		NbtCompound fireworks = new NbtCompound();
		fireworks.putInt("Flight", 2);
		fireworks.putList("Explosions", NbtList.of(NbtTag.ofCompound(explosion)));
		NbtCompound tag = new NbtCompound();
		tag.putCompound("Fireworks", fireworks);
		NbtCompound legacy = legacyItem("minecraft:firework_rocket", tag);

		NbtCompound component = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1)
				.getCompound("components").getCompound("minecraft:fireworks");
		assertEquals(2, component.getInt("flight_duration", -1));
		NbtCompound converted = component.getList("explosions").get(0).asCompound();
		assertEquals("creeper", converted.getString("shape", null));
		assertEquals(1, converted.getInt("has_trail", -1));

		// the legacy Flight was an int and comes back as the byte the fix produced, so compare through Common
		NbtCompound modern = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1);
		assertEquals(ItemNormalizer.toCommon(legacy, DV_1_20_1).getCompound(Mchf.ITEM_FIREWORKS),
				ItemNormalizer.toCommon(modern, DV_1_21_1).getCompound(Mchf.ITEM_FIREWORKS));
	}

	@Test
	void fireworkStarKeepsItsExplosion()
	{
		NbtCompound explosion = new NbtCompound();
		explosion.putInt("Type", 1);  // large_ball
		explosion.put("Colors", NbtTag.ofIntArray(new int[]{2437522}));
		NbtCompound tag = new NbtCompound();
		tag.putCompound("Explosion", explosion);
		NbtCompound legacy = legacyItem("minecraft:firework_star", tag);

		NbtCompound component = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1)
				.getCompound("components").getCompound("minecraft:firework_explosion");
		assertEquals("large_ball", component.getString("shape", null));

		NbtCompound backCommon = ItemNormalizer.toCommon(
				ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_1), DV_1_21_1);
		assertEquals("large_ball", backCommon.getCompound(Mchf.ITEM_FIREWORK_EXPLOSION).getString("shape", null));
	}

	@Test
	void armorTrimIsTheSameOnBothSides()
	{
		NbtCompound trim = new NbtCompound();
		trim.putString("material", "minecraft:amethyst");
		trim.putString("pattern", "minecraft:sentry");
		NbtCompound tag = new NbtCompound();
		tag.putCompound("Trim", trim);
		NbtCompound legacy = legacyItem("minecraft:diamond_chestplate", tag);

		NbtCompound component = ItemNormalizer.fromCommon(ItemNormalizer.toCommon(legacy, DV_1_20_1), DV_1_21_11)
				.getCompound("components").getCompound("minecraft:trim");
		assertEquals("minecraft:amethyst", component.getString("material", null));
		assertEquals("minecraft:sentry", component.getString("pattern", null));

		assertSurvivesBothWays(legacy, DV_1_21_11);
	}

	@Test
	void aRichItemLeavesNothingBehindInExtra()
	{
		NbtCompound trim = new NbtCompound();
		trim.putString("material", "minecraft:amethyst");
		trim.putString("pattern", "minecraft:sentry");
		NbtCompound tag = new NbtCompound();
		tag.putCompound("Trim", trim);

		assertNull(ItemNormalizer.toCommon(legacyItem("minecraft:diamond_chestplate", tag), DV_1_20_1)
				.getCompound(Mchf.ITEM_EXTRA));
	}
}
