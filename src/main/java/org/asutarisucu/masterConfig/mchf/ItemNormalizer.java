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

import java.util.Map;

import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_CONTAINER;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_CONTAINER_ITEM;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_CONTAINER_SLOT;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_COUNT;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_CUSTOM_MODEL_DATA;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_CUSTOM_NAME;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_DAMAGE;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_DYED_COLOR;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_POTION;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_POTION_COLOR;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_POTION_EFFECTS;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_POTION_ID;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_ENCHANTMENTS;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_EXTRA;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_EXTRA_FROM;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_ID;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_LORE;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_REPAIR_COST;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_FIREWORKS;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_FIREWORK_EXPLOSION;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_PROFILE;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_STORED_ENCHANTMENTS;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_TRIM;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_WRITABLE_BOOK;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_WRITTEN_BOOK;
import static org.asutarisucu.masterConfig.mchf.Mchf.ITEM_UNBREAKABLE;
import static org.asutarisucu.masterConfig.mchf.Mchf.V_CMD_EXPANDED;
import static org.asutarisucu.masterConfig.mchf.Mchf.V_TEXT_NBT;
import static org.asutarisucu.masterConfig.mchf.Mchf.ensureNamespaced;
import static org.asutarisucu.masterConfig.mchf.Mchf.subTier;
import static org.asutarisucu.masterConfig.mchf.Mchf.usesComponents;
import static org.asutarisucu.masterConfig.mchf.Mchf.usesUnwrappedComponents;

/**
 * Converts one item stack between the nbt minecraft writes and the version neutral MCHF Common form.
 *
 * <p>Every mapping here comes from the vanilla {@code ItemStackComponentizationFix},
 * {@code CustomModelDataExpandFix} and {@code UnflattenTextComponentFix}, which are the authority on
 * how the same information is spelled in the old and the new shape.
 *
 * <p>What cannot be normalized is kept verbatim in {@code Extra} together with the DataVersion it came from.
 * Extra is pasted back only when the target writes items the same way (see {@link Mchf#subTier}), which makes
 * a downgrade inside one sub tier lossless and a downgrade across sub tiers lossy but never wrong
 */
public final class ItemNormalizer
{
	private static final String LEGACY_TAG = "tag";
	private static final String COMPONENTS = "components";

	private static final String C_DAMAGE = "minecraft:damage";
	private static final String C_REPAIR_COST = "minecraft:repair_cost";
	private static final String C_UNBREAKABLE = "minecraft:unbreakable";
	private static final String C_CUSTOM_MODEL_DATA = "minecraft:custom_model_data";
	private static final String C_ENCHANTMENTS = "minecraft:enchantments";
	private static final String C_STORED_ENCHANTMENTS = "minecraft:stored_enchantments";
	private static final String C_CUSTOM_NAME = "minecraft:custom_name";
	private static final String C_LORE = "minecraft:lore";
	private static final String C_CONTAINER = "minecraft:container";
	private static final String C_DYED_COLOR = "minecraft:dyed_color";
	private static final String C_POTION_CONTENTS = "minecraft:potion_contents";
	private static final String C_TRIM = "minecraft:trim";
	private static final String C_PROFILE = "minecraft:profile";
	private static final String C_FIREWORKS = "minecraft:fireworks";
	private static final String C_FIREWORK_EXPLOSION = "minecraft:firework_explosion";
	private static final String C_WRITTEN_BOOK = "minecraft:written_book_content";
	private static final String C_WRITABLE_BOOK = "minecraft:writable_book_content";

	private static final String ID_WRITTEN_BOOK = "minecraft:written_book";
	private static final String ID_WRITABLE_BOOK = "minecraft:writable_book";

	/** the firework explosion shapes, in the order the legacy Type int used */
	private static final String[] EXPLOSION_SHAPES = {"small_ball", "large_ball", "star", "creeper", "burst"};

	private ItemNormalizer()
	{
	}

	public static boolean isEmptyItem(NbtCompound item)
	{
		return item == null || item.isEmpty() || item.getString("id", "").isEmpty();
	}

	public static NbtCompound toCommon(NbtCompound item, int dataVersion)
	{
		NbtCompound common = new NbtCompound();
		if (isEmptyItem(item))
		{
			return common;
		}
		boolean components = usesComponents(dataVersion);

		common.putString(ITEM_ID, ensureNamespaced(item.getString("id", "")));
		common.putInt(ITEM_COUNT, item.getInt(components ? "count" : "Count", 1));

		NbtCompound source = item.getCompound(components ? COMPONENTS : LEGACY_TAG);
		NbtCompound extra = source == null ? new NbtCompound() : source.copy();

		if (components)
		{
			moveInt(extra, C_DAMAGE, common, ITEM_DAMAGE);
			moveInt(extra, C_REPAIR_COST, common, ITEM_REPAIR_COST);
			// left in Extra on purpose: below 4307 it also carries show_in_tooltip, which Common has no room for
			if (extra.has(C_UNBREAKABLE))
			{
				common.putByte(ITEM_UNBREAKABLE, (byte) 1);
			}
			// likewise, from 4175 on this also carries flags / strings / colors
			NbtTag customModelData = extra.get(C_CUSTOM_MODEL_DATA);
			if (customModelData != null)
			{
				common.putInt(ITEM_CUSTOM_MODEL_DATA, readCustomModelData(customModelData, dataVersion));
			}
			readComponentEnchantments(extra, C_ENCHANTMENTS, common, ITEM_ENCHANTMENTS);
			readComponentEnchantments(extra, C_STORED_ENCHANTMENTS, common, ITEM_STORED_ENCHANTMENTS);
			NbtTag name = extra.remove(C_CUSTOM_NAME);
			if (name != null)
			{
				common.putString(ITEM_CUSTOM_NAME, toJsonText(name, dataVersion));
			}
			NbtTag lore = extra.remove(C_LORE);
			if (lore != null && lore.asList() != null)
			{
				common.putList(ITEM_LORE, toJsonTextList(lore.asList(), dataVersion));
			}
			NbtTag dyed = extra.get(C_DYED_COLOR);
			if (dyed != null)
			{
				// 4307 unwrapped this one from {rgb: n} to a plain int. Below that it also carries
				// show_in_tooltip, so like the two above it stays in Extra
				NbtCompound wrapped = dyed.asCompound();
				common.putInt(ITEM_DYED_COLOR, wrapped != null ? wrapped.getInt("rgb", 0) : dyed.asInt(0));
			}
			NbtTag potionTag = extra.get(C_POTION_CONTENTS);
			if (potionTag != null)
			{
				NbtCompound potionContents = potionTag.asCompound();
				NbtCompound normalized = new NbtCompound();
				if (potionContents == null)
				{
					// the codec accepts a bare potion id as a shorthand for {potion: id}
					if (potionTag.type() == NbtTag.STRING)
					{
						normalized.putString(ITEM_POTION_ID, potionTag.asString(""));
						extra.remove(C_POTION_CONTENTS);
					}
				}
				else
				{
					copyIfPresent(potionContents, "potion", normalized, ITEM_POTION_ID);
					copyIfPresent(potionContents, "custom_color", normalized, ITEM_POTION_COLOR);
					copyIfPresent(potionContents, "custom_effects", normalized, ITEM_POTION_EFFECTS);
					if (!normalized.isEmpty())
					{
						// custom_name is not mapped, so take out only what Common holds
						potionContents.remove("potion");
						potionContents.remove("custom_color");
						potionContents.remove("custom_effects");
						if (potionContents.isEmpty())
						{
							extra.remove(C_POTION_CONTENTS);
						}
					}
				}
				if (!normalized.isEmpty())
				{
					common.putCompound(ITEM_POTION, normalized);
				}
			}
			// these five have exactly the same shape from 1.20.5 through 26.x, checked against the component
			// codecs of 1.21.2 / 1.21.4 / 1.21.8 / 1.21.11 / 26.1, so Common keeps them verbatim
			moveTag(extra, C_TRIM, common, ITEM_TRIM);
			moveTag(extra, C_PROFILE, common, ITEM_PROFILE);
			moveTag(extra, C_FIREWORKS, common, ITEM_FIREWORKS);
			moveTag(extra, C_FIREWORK_EXPLOSION, common, ITEM_FIREWORK_EXPLOSION);
			moveTag(extra, C_WRITABLE_BOOK, common, ITEM_WRITABLE_BOOK);
			NbtCompound writtenBook = extra.getCompound(C_WRITTEN_BOOK);
			if (writtenBook != null)
			{
				// only a written book holds text components in its pages, so only it needs the 4290 handling
				common.putCompound(ITEM_WRITTEN_BOOK, writtenBookToCommon(writtenBook, dataVersion));
				extra.remove(C_WRITTEN_BOOK);
			}
			NbtTag container = extra.remove(C_CONTAINER);
			if (container != null && container.asList() != null)
			{
				common.putList(ITEM_CONTAINER, componentContainerToCommon(container.asList(), dataVersion));
			}
		}
		else
		{
			moveInt(extra, "Damage", common, ITEM_DAMAGE);
			moveInt(extra, "RepairCost", common, ITEM_REPAIR_COST);
			NbtTag unbreakable = extra.remove("Unbreakable");
			if (unbreakable != null && unbreakable.asBoolean(false))
			{
				common.putByte(ITEM_UNBREAKABLE, (byte) 1);
			}
			moveInt(extra, "CustomModelData", common, ITEM_CUSTOM_MODEL_DATA);
			readLegacyEnchantments(extra, "Enchantments", common, ITEM_ENCHANTMENTS);
			readLegacyEnchantments(extra, "StoredEnchantments", common, ITEM_STORED_ENCHANTMENTS);

			NbtCompound display = extra.getCompound("display");
			if (display != null)
			{
				NbtTag name = display.remove("Name");
				if (name != null && name.type() == NbtTag.STRING)
				{
					common.putString(ITEM_CUSTOM_NAME, name.asString(""));
				}
				NbtTag lore = display.remove("Lore");
				if (lore != null && lore.asList() != null)
				{
					common.putList(ITEM_LORE, lore.asList().copy());
				}
				NbtTag color = display.remove("color");
				if (color != null && color.isNumber())
				{
					common.putInt(ITEM_DYED_COLOR, color.asInt(0));
				}
				if (display.isEmpty())
				{
					extra.remove("display");
				}
			}

			NbtCompound potion = new NbtCompound();
			copyIfPresent(extra, "Potion", potion, ITEM_POTION_ID);
			copyIfPresent(extra, "CustomPotionColor", potion, ITEM_POTION_COLOR);
			copyIfPresent(extra, "custom_potion_effects", potion, ITEM_POTION_EFFECTS);
			if (!potion.isEmpty())
			{
				common.putCompound(ITEM_POTION, potion);
				extra.remove("Potion");
				extra.remove("CustomPotionColor");
				extra.remove("custom_potion_effects");
			}

			moveTag(extra, "Trim", common, ITEM_TRIM);  // the legacy Trim tag already holds {material, pattern}
			NbtTag skullOwner = extra.remove("SkullOwner");
			if (skullOwner != null)
			{
				NbtCompound profile = skullOwnerToCommon(skullOwner);
				if (!profile.isEmpty())
				{
					common.putCompound(ITEM_PROFILE, profile);
				}
			}
			NbtCompound legacyFireworks = extra.getCompound("Fireworks");
			if (legacyFireworks != null)
			{
				common.putCompound(ITEM_FIREWORKS, legacyFireworksToCommon(legacyFireworks));
				extra.remove("Fireworks");
			}
			NbtCompound legacyExplosion = extra.getCompound("Explosion");
			if (legacyExplosion != null)
			{
				common.putCompound(ITEM_FIREWORK_EXPLOSION, legacyExplosionToCommon(legacyExplosion));
				extra.remove("Explosion");
			}
			String itemId = common.getString(ITEM_ID, "");
			NbtCompound legacyBook = legacyBookToCommon(extra, itemId);
			if (legacyBook != null)
			{
				common.putCompound(ID_WRITABLE_BOOK.equals(itemId) ? ITEM_WRITABLE_BOOK : ITEM_WRITTEN_BOOK, legacyBook);
			}

			NbtCompound blockEntity = extra.getCompound("BlockEntityTag");
			if (blockEntity != null)
			{
				NbtTag items = blockEntity.remove("Items");
				if (items != null && items.asList() != null)
				{
					common.putList(ITEM_CONTAINER, legacyContainerToCommon(items.asList(), dataVersion));
				}
				if (blockEntity.isEmpty())
				{
					extra.remove("BlockEntityTag");
				}
			}
		}

		if (!extra.isEmpty())
		{
			common.putCompound(ITEM_EXTRA, extra);
			common.putInt(ITEM_EXTRA_FROM, dataVersion);
		}
		return common;
	}

	public static NbtCompound fromCommon(NbtCompound common, int dataVersion)
	{
		NbtCompound item = new NbtCompound();
		if (common == null || common.isEmpty() || common.getString(ITEM_ID, "").isEmpty())
		{
			return item;
		}
		boolean components = usesComponents(dataVersion);
		int count = common.getInt(ITEM_COUNT, 1);

		item.putString("id", common.getString(ITEM_ID, ""));
		if (components)
		{
			item.putInt("count", count);
		}
		else
		{
			item.putByte("Count", (byte) count);
		}

		NbtCompound extra = common.getCompound(ITEM_EXTRA);
		int extraFrom = common.getInt(ITEM_EXTRA_FROM, Integer.MIN_VALUE);
		NbtCompound target = extra != null && subTier(extraFrom) == subTier(dataVersion) ? extra.copy() : new NbtCompound();

		if (components)
		{
			putIntIfPresent(common, ITEM_DAMAGE, target, C_DAMAGE);
			putIntIfPresent(common, ITEM_REPAIR_COST, target, C_REPAIR_COST);
			// when Extra was pasted in it already holds the full component, which is richer than Common,
			// so only build one from Common when there is none
			if (common.has(ITEM_UNBREAKABLE) && !target.has(C_UNBREAKABLE))
			{
				target.putCompound(C_UNBREAKABLE, new NbtCompound());
			}
			if (common.has(ITEM_CUSTOM_MODEL_DATA) && !target.has(C_CUSTOM_MODEL_DATA))
			{
				target.put(C_CUSTOM_MODEL_DATA, writeCustomModelData(common.getInt(ITEM_CUSTOM_MODEL_DATA, 0), dataVersion));
			}
			writeComponentEnchantments(common, ITEM_ENCHANTMENTS, target, C_ENCHANTMENTS, dataVersion);
			writeComponentEnchantments(common, ITEM_STORED_ENCHANTMENTS, target, C_STORED_ENCHANTMENTS, dataVersion);
			if (common.has(ITEM_DYED_COLOR) && !target.has(C_DYED_COLOR))
			{
				int rgb = common.getInt(ITEM_DYED_COLOR, 0);
				if (usesUnwrappedComponents(dataVersion))
				{
					target.putInt(C_DYED_COLOR, rgb);
				}
				else
				{
					NbtCompound wrapped = new NbtCompound();
					wrapped.putInt("rgb", rgb);
					target.putCompound(C_DYED_COLOR, wrapped);
				}
			}
			NbtCompound potion = common.getCompound(ITEM_POTION);
			if (potion != null)
			{
				NbtCompound contents = target.getCompound(C_POTION_CONTENTS);
				if (contents == null)
				{
					contents = new NbtCompound();
					target.putCompound(C_POTION_CONTENTS, contents);
				}
				copyIfPresent(potion, ITEM_POTION_ID, contents, "potion");
				copyIfPresent(potion, ITEM_POTION_COLOR, contents, "custom_color");
				copyIfPresent(potion, ITEM_POTION_EFFECTS, contents, "custom_effects");
			}
			if (common.has(ITEM_CUSTOM_NAME))
			{
				target.put(C_CUSTOM_NAME, fromJsonText(common.getString(ITEM_CUSTOM_NAME, ""), dataVersion));
			}
			NbtList lore = common.getList(ITEM_LORE);
			if (lore != null)
			{
				target.putList(C_LORE, fromJsonTextList(lore, dataVersion));
			}
			copyIfPresent(common, ITEM_TRIM, target, C_TRIM);
			copyIfPresent(common, ITEM_PROFILE, target, C_PROFILE);
			copyIfPresent(common, ITEM_FIREWORKS, target, C_FIREWORKS);
			copyIfPresent(common, ITEM_FIREWORK_EXPLOSION, target, C_FIREWORK_EXPLOSION);
			copyIfPresent(common, ITEM_WRITABLE_BOOK, target, C_WRITABLE_BOOK);
			NbtCompound writtenBook = common.getCompound(ITEM_WRITTEN_BOOK);
			if (writtenBook != null)
			{
				target.putCompound(C_WRITTEN_BOOK, writtenBookFromCommon(writtenBook, dataVersion));
			}
			NbtList container = common.getList(ITEM_CONTAINER);
			if (container != null)
			{
				target.putList(C_CONTAINER, commonContainerToComponent(container, dataVersion));
			}
			if (!target.isEmpty())
			{
				item.putCompound(COMPONENTS, target);
			}
		}
		else
		{
			putIntIfPresent(common, ITEM_DAMAGE, target, "Damage");
			putIntIfPresent(common, ITEM_REPAIR_COST, target, "RepairCost");
			if (common.has(ITEM_UNBREAKABLE))
			{
				target.putByte("Unbreakable", (byte) 1);
			}
			putIntIfPresent(common, ITEM_CUSTOM_MODEL_DATA, target, "CustomModelData");
			writeLegacyEnchantments(common, ITEM_ENCHANTMENTS, target, "Enchantments");
			writeLegacyEnchantments(common, ITEM_STORED_ENCHANTMENTS, target, "StoredEnchantments");

			if (common.has(ITEM_CUSTOM_NAME) || common.has(ITEM_LORE))
			{
				NbtCompound display = target.getCompound("display");
				if (display == null)
				{
					display = new NbtCompound();
					target.putCompound("display", display);
				}
				if (common.has(ITEM_CUSTOM_NAME))
				{
					display.putString("Name", common.getString(ITEM_CUSTOM_NAME, ""));
				}
				NbtList lore = common.getList(ITEM_LORE);
				if (lore != null)
				{
					display.putList("Lore", lore.copy());
				}
			}
			if (common.has(ITEM_DYED_COLOR))
			{
				NbtCompound display = target.getCompound("display");
				if (display == null)
				{
					display = new NbtCompound();
					target.putCompound("display", display);
				}
				display.putInt("color", common.getInt(ITEM_DYED_COLOR, 0));
			}
			NbtCompound potion = common.getCompound(ITEM_POTION);
			if (potion != null)
			{
				copyIfPresent(potion, ITEM_POTION_ID, target, "Potion");
				copyIfPresent(potion, ITEM_POTION_COLOR, target, "CustomPotionColor");
				copyIfPresent(potion, ITEM_POTION_EFFECTS, target, "custom_potion_effects");
			}

			copyIfPresent(common, ITEM_TRIM, target, "Trim");
			NbtCompound profile = common.getCompound(ITEM_PROFILE);
			if (profile != null)
			{
				target.putCompound("SkullOwner", commonToSkullOwner(profile));
			}
			NbtCompound fireworks = common.getCompound(ITEM_FIREWORKS);
			if (fireworks != null)
			{
				target.putCompound("Fireworks", commonToLegacyFireworks(fireworks));
			}
			NbtCompound explosion = common.getCompound(ITEM_FIREWORK_EXPLOSION);
			if (explosion != null)
			{
				target.putCompound("Explosion", commonToLegacyExplosion(explosion));
			}
			NbtCompound anyBook = common.getCompound(ITEM_WRITTEN_BOOK);
			if (anyBook == null)
			{
				anyBook = common.getCompound(ITEM_WRITABLE_BOOK);
			}
			if (anyBook != null)
			{
				commonBookToLegacy(anyBook, target);
			}
			NbtList container = common.getList(ITEM_CONTAINER);
			if (container != null)
			{
				NbtCompound blockEntity = target.getCompound("BlockEntityTag");
				if (blockEntity == null)
				{
					blockEntity = new NbtCompound();
					target.putCompound("BlockEntityTag", blockEntity);
				}
				blockEntity.putList("Items", commonContainerToLegacy(container, dataVersion));
			}
			if (!target.isEmpty())
			{
				item.putCompound(LEGACY_TAG, target);
			}
		}
		return item;
	}

	// ---- helpers ----

	private static void moveInt(NbtCompound from, String fromKey, NbtCompound to, String toKey)
	{
		NbtTag tag = from.remove(fromKey);
		if (tag != null && tag.isNumber())
		{
			to.putInt(toKey, tag.asInt(0));
		}
	}

	private static void putIntIfPresent(NbtCompound from, String fromKey, NbtCompound to, String toKey)
	{
		if (from.has(fromKey))
		{
			to.putInt(toKey, from.getInt(fromKey, 0));
		}
	}

	private static int readCustomModelData(NbtTag tag, int dataVersion)
	{
		if (dataVersion >= V_CMD_EXPANDED)
		{
			NbtCompound expanded = tag.asCompound();
			NbtList floats = expanded == null ? null : expanded.getList("floats");
			return floats == null || floats.size() == 0 ? 0 : floats.get(0).asInt(0);
		}
		return tag.asInt(0);
	}

	private static NbtTag writeCustomModelData(int value, int dataVersion)
	{
		if (dataVersion >= V_CMD_EXPANDED)
		{
			NbtCompound expanded = new NbtCompound();
			expanded.putList("floats", NbtList.of(NbtTag.ofFloat(value)));
			return NbtTag.ofCompound(expanded);
		}
		return NbtTag.ofInt(value);
	}

	private static String toJsonText(NbtTag tag, int dataVersion)
	{
		return dataVersion >= V_TEXT_NBT ? JsonNbt.nbtToJson(tag) : tag.asString("");
	}

	private static NbtTag fromJsonText(String json, int dataVersion)
	{
		return dataVersion >= V_TEXT_NBT ? JsonNbt.jsonToNbt(json) : NbtTag.ofString(json);
	}

	private static NbtList toJsonTextList(NbtList source, int dataVersion)
	{
		NbtList result = new NbtList();
		for (NbtTag line : source.items())
		{
			result.add(NbtTag.ofString(toJsonText(line, dataVersion)));
		}
		return result;
	}

	private static NbtList fromJsonTextList(NbtList source, int dataVersion)
	{
		java.util.List<NbtTag> converted = new java.util.ArrayList<>();
		byte type = NbtTag.END;
		boolean mixed = false;
		for (NbtTag line : source.items())
		{
			NbtTag tag = fromJsonText(line.asString(""), dataVersion);
			if (converted.isEmpty())
			{
				type = tag.type();
			}
			else if (tag.type() != type)
			{
				mixed = true;
			}
			converted.add(tag);
		}

		NbtList result = new NbtList();
		for (int i = 0; i < converted.size(); i++)
		{
			// an nbt list cannot hold two types. If json parsing produced both compounds and strings,
			// fall back to the plain string form, which is a valid text component everywhere
			result.add(mixed ? NbtTag.ofString(source.get(i).asString("")) : converted.get(i));
		}
		return result;
	}

	private static void readLegacyEnchantments(NbtCompound tag, String key, NbtCompound common, String commonKey)
	{
		NbtTag raw = tag.remove(key);
		NbtList list = raw == null ? null : raw.asList();
		if (list == null)
		{
			return;
		}
		NbtCompound levels = new NbtCompound();
		for (NbtTag entry : list.items())
		{
			NbtCompound enchantment = entry.asCompound();
			if (enchantment == null)
			{
				continue;
			}
			String id = enchantment.getString("id", "");
			int level = enchantment.getInt("lvl", 0);
			if (!id.isEmpty() && level > 0)
			{
				levels.putInt(ensureNamespaced(id), level);
			}
		}
		if (!levels.isEmpty())
		{
			common.putCompound(commonKey, levels);
		}
	}

	private static void writeLegacyEnchantments(NbtCompound common, String commonKey, NbtCompound tag, String key)
	{
		NbtCompound levels = common.getCompound(commonKey);
		if (levels == null || levels.isEmpty())
		{
			return;
		}
		NbtList list = new NbtList();
		for (Map.Entry<String, NbtTag> entry : levels.entries().entrySet())
		{
			NbtCompound enchantment = new NbtCompound();
			enchantment.putString("id", entry.getKey());
			enchantment.put("lvl", NbtTag.ofShort((short) entry.getValue().asInt(1)));
			list.add(NbtTag.ofCompound(enchantment));
		}
		tag.putList(key, list);
	}

	private static void readComponentEnchantments(NbtCompound components, String key, NbtCompound common, String commonKey)
	{
		NbtCompound component = components.getCompound(key);
		if (component == null)
		{
			return;
		}
		// the componentization fix nests the id to level map under "levels", the rest is tooltip control
		NbtCompound levels = component.getCompound("levels");
		NbtCompound source = levels != null ? levels : component;
		NbtCompound result = new NbtCompound();
		source.entries().forEach((id, level) -> {
			if (level.isNumber() && level.asInt(0) > 0)
			{
				result.putInt(ensureNamespaced(id), level.asInt(0));
			}
		});
		if (result.isEmpty())
		{
			return;
		}
		common.putCompound(commonKey, result);
		if (levels == null)
		{
			components.remove(key);
			return;
		}
		component.remove("levels");
		if (component.isEmpty())
		{
			components.remove(key);
		}
	}

	private static void writeComponentEnchantments(NbtCompound common, String commonKey, NbtCompound components, String key, int dataVersion)
	{
		NbtCompound levels = common.getCompound(commonKey);
		if (levels == null || levels.isEmpty())
		{
			return;
		}
		if (usesUnwrappedComponents(dataVersion))
		{
			// from 4307 on the component is the id to level map itself
			components.putCompound(key, levels.copy());
			return;
		}
		NbtCompound component = components.getCompound(key);
		if (component == null)
		{
			component = new NbtCompound();
			components.putCompound(key, component);
		}
		component.putCompound("levels", levels.copy());
	}

	private static void copyIfPresent(NbtCompound from, String fromKey, NbtCompound to, String toKey)
	{
		NbtTag tag = from.get(fromKey);
		if (tag != null)
		{
			to.put(toKey, tag.copy());
		}
	}

	private static NbtList legacyContainerToCommon(NbtList items, int dataVersion)
	{
		NbtList result = new NbtList();
		for (NbtTag entry : items.items())
		{
			NbtCompound stored = entry.asCompound();
			if (stored == null)
			{
				continue;
			}
			NbtCompound inner = stored.copy();
			int slot = inner.getInt("Slot", 0) & 0xFF;
			inner.remove("Slot");
			result.add(NbtTag.ofCompound(containerEntry(slot, toCommon(inner, dataVersion))));
		}
		return result;
	}

	private static NbtList componentContainerToCommon(NbtList container, int dataVersion)
	{
		NbtList result = new NbtList();
		for (NbtTag entry : container.items())
		{
			NbtCompound stored = entry.asCompound();
			NbtCompound inner = stored == null ? null : stored.getCompound("item");
			if (inner == null)
			{
				continue;
			}
			result.add(NbtTag.ofCompound(containerEntry(stored.getInt("slot", 0), toCommon(inner, dataVersion))));
		}
		return result;
	}

	private static NbtList commonContainerToLegacy(NbtList container, int dataVersion)
	{
		NbtList result = new NbtList();
		for (NbtTag entry : container.items())
		{
			NbtCompound stored = entry.asCompound();
			if (stored == null)
			{
				continue;
			}
			NbtCompound item = fromCommon(stored.getCompound(ITEM_CONTAINER_ITEM), dataVersion);
			if (item.isEmpty())
			{
				continue;
			}
			item.putByte("Slot", (byte) stored.getInt(ITEM_CONTAINER_SLOT, 0));
			result.add(NbtTag.ofCompound(item));
		}
		return result;
	}

	private static NbtList commonContainerToComponent(NbtList container, int dataVersion)
	{
		NbtList result = new NbtList();
		for (NbtTag entry : container.items())
		{
			NbtCompound stored = entry.asCompound();
			if (stored == null)
			{
				continue;
			}
			NbtCompound item = fromCommon(stored.getCompound(ITEM_CONTAINER_ITEM), dataVersion);
			if (item.isEmpty())
			{
				continue;
			}
			NbtCompound slotEntry = new NbtCompound();
			slotEntry.putInt("slot", stored.getInt(ITEM_CONTAINER_SLOT, 0));
			slotEntry.putCompound("item", item);
			result.add(NbtTag.ofCompound(slotEntry));
		}
		return result;
	}

	private static void moveTag(NbtCompound from, String fromKey, NbtCompound to, String toKey)
	{
		NbtTag tag = from.remove(fromKey);
		if (tag != null)
		{
			to.put(toKey, tag);
		}
	}

	// ---- written book -------------------------------------------------------------
	// {title:{raw,filtered?}, author, generation?, pages:[{raw,filtered?}], resolved?}
	// Only the pages are text components, so only they change shape at 4290. The title is a plain string

	private static NbtCompound writtenBookToCommon(NbtCompound component, int dataVersion)
	{
		NbtCompound book = component.copy();
		NbtList pages = book.getList("pages");
		if (pages != null && dataVersion >= V_TEXT_NBT)
		{
			book.putList("pages", mapFilteredPair(pages, tag -> NbtTag.ofString(JsonNbt.nbtToJson(tag))));
		}
		return book;
	}

	private static NbtCompound writtenBookFromCommon(NbtCompound common, int dataVersion)
	{
		NbtCompound book = common.copy();
		NbtList pages = book.getList("pages");
		if (pages != null && dataVersion >= V_TEXT_NBT)
		{
			book.putList("pages", mapFilteredPair(pages, tag -> JsonNbt.jsonToNbt(tag.asString(""))));
		}
		return book;
	}

	private static NbtList mapFilteredPair(NbtList pages, java.util.function.UnaryOperator<NbtTag> mapper)
	{
		NbtList result = new NbtList();
		for (NbtTag page : pages.items())
		{
			// the codec accepts a bare value as a shorthand for {raw: value}, and from 4290 on that bare
			// value is itself a compound, so the only way to tell the two apart is the "raw" key.
			// The shorthand is expanded here, because an nbt list cannot hold both forms at once
			NbtCompound pair = page.asCompound();
			if (pair == null || pair.get("raw") == null)
			{
				NbtCompound expanded = new NbtCompound();
				expanded.put("raw", mapper.apply(page));
				result.add(NbtTag.ofCompound(expanded));
				continue;
			}
			NbtCompound mapped = pair.copy();
			if (pair.get("raw") != null)
			{
				mapped.put("raw", mapper.apply(pair.get("raw")));
			}
			if (pair.get("filtered") != null)
			{
				mapped.put("filtered", mapper.apply(pair.get("filtered")));
			}
			result.add(NbtTag.ofCompound(mapped));
		}
		return result;
	}

	/**
	 * The legacy side keeps the pages in {@code pages} and their moderated variants in {@code filtered_pages},
	 * a map from page index to text, and the same split for the title
	 */
	private static NbtCompound legacyBookToCommon(NbtCompound tag, String itemId)
	{
		boolean written = ID_WRITTEN_BOOK.equals(itemId);
		if (!written && !ID_WRITABLE_BOOK.equals(itemId))
		{
			return null;
		}
		NbtTag rawPages = tag.remove("pages");
		NbtCompound filteredPages = tag.getCompound("filtered_pages");
		tag.remove("filtered_pages");

		NbtCompound book = new NbtCompound();
		if (rawPages != null && rawPages.asList() != null)
		{
			NbtList pages = new NbtList();
			NbtList source = rawPages.asList();
			for (int i = 0; i < source.size(); i++)
			{
				NbtCompound pair = new NbtCompound();
				pair.put("raw", source.get(i).copy());
				if (filteredPages != null && filteredPages.get(String.valueOf(i)) != null)
				{
					pair.put("filtered", filteredPages.get(String.valueOf(i)).copy());
				}
				pages.add(NbtTag.ofCompound(pair));
			}
			book.putList("pages", pages);
		}
		if (written)
		{
			NbtTag title = tag.remove("title");
			NbtTag filteredTitle = tag.remove("filtered_title");
			if (title != null)
			{
				NbtCompound pair = new NbtCompound();
				pair.put("raw", title.copy());
				if (filteredTitle != null)
				{
					pair.put("filtered", filteredTitle.copy());
				}
				book.putCompound("title", pair);
			}
			moveTag(tag, "author", book, "author");
			moveTag(tag, "generation", book, "generation");
			moveTag(tag, "resolved", book, "resolved");
		}
		return book.isEmpty() ? null : book;
	}

	private static void commonBookToLegacy(NbtCompound book, NbtCompound tag)
	{
		NbtList pages = book.getList("pages");
		if (pages != null)
		{
			NbtList raw = new NbtList();
			NbtCompound filtered = new NbtCompound();
			for (int i = 0; i < pages.size(); i++)
			{
				NbtCompound pair = pages.get(i).asCompound();
				if (pair == null)
				{
					raw.add(pages.get(i).copy());
					continue;
				}
				raw.add(pair.get("raw") != null ? pair.get("raw").copy() : NbtTag.ofString(""));
				if (pair.get("filtered") != null)
				{
					filtered.put(String.valueOf(i), pair.get("filtered").copy());
				}
			}
			tag.putList("pages", raw);
			if (!filtered.isEmpty())
			{
				tag.putCompound("filtered_pages", filtered);
			}
		}
		NbtCompound title = book.getCompound("title");
		if (title != null)
		{
			copyIfPresent(title, "raw", tag, "title");
			copyIfPresent(title, "filtered", tag, "filtered_title");
		}
		copyIfPresent(book, "author", tag, "author");
		copyIfPresent(book, "generation", tag, "generation");
		copyIfPresent(book, "resolved", tag, "resolved");
	}

	// ---- player head --------------------------------------------------------------
	// legacy SkullOwner is either a bare name or {Name, Id, Properties:{key:[{Value,Signature?}]}},
	// the component is {name?, id?, properties?:[{name,value,signature?}]}

	private static NbtCompound skullOwnerToCommon(NbtTag skullOwner)
	{
		NbtCompound profile = new NbtCompound();
		if (skullOwner.type() == NbtTag.STRING)
		{
			profile.putString("name", skullOwner.asString(""));
			return profile;
		}
		NbtCompound owner = skullOwner.asCompound();
		if (owner == null)
		{
			return profile;
		}
		copyIfPresent(owner, "Name", profile, "name");
		copyIfPresent(owner, "Id", profile, "id");
		NbtCompound properties = owner.getCompound("Properties");
		if (properties != null && !properties.isEmpty())
		{
			NbtList flattened = new NbtList();
			properties.entries().forEach((name, values) -> {
				NbtList list = values.asList();
				if (list == null)
				{
					return;
				}
				for (NbtTag value : list.items())
				{
					NbtCompound source = value.asCompound();
					if (source == null)
					{
						continue;
					}
					NbtCompound property = new NbtCompound();
					property.putString("name", name);
					copyIfPresent(source, "Value", property, "value");
					copyIfPresent(source, "Signature", property, "signature");
					flattened.add(NbtTag.ofCompound(property));
				}
			});
			if (flattened.size() > 0)
			{
				profile.putList("properties", flattened);
			}
		}
		return profile;
	}

	private static NbtCompound commonToSkullOwner(NbtCompound profile)
	{
		NbtCompound owner = new NbtCompound();
		copyIfPresent(profile, "name", owner, "Name");
		copyIfPresent(profile, "id", owner, "Id");
		NbtList properties = profile.getList("properties");
		if (properties != null && properties.size() > 0)
		{
			NbtCompound grouped = new NbtCompound();
			for (NbtTag entry : properties.items())
			{
				NbtCompound property = entry.asCompound();
				if (property == null)
				{
					continue;
				}
				String name = property.getString("name", "");
				NbtCompound value = new NbtCompound();
				copyIfPresent(property, "value", value, "Value");
				copyIfPresent(property, "signature", value, "Signature");
				NbtList bucket = grouped.getList(name);
				if (bucket == null)
				{
					bucket = new NbtList();
					grouped.putList(name, bucket);
				}
				bucket.add(NbtTag.ofCompound(value));
			}
			if (!grouped.isEmpty())
			{
				owner.putCompound("Properties", grouped);
			}
		}
		return owner;
	}

	// ---- fireworks ------------------------------------------------------------------

	private static NbtCompound legacyFireworksToCommon(NbtCompound fireworks)
	{
		NbtCompound common = new NbtCompound();
		NbtTag flight = fireworks.get("Flight");
		if (flight != null)
		{
			common.putByte("flight_duration", (byte) flight.asInt(0));
		}
		NbtList explosions = fireworks.getList("Explosions");
		if (explosions != null)
		{
			NbtList converted = new NbtList();
			for (NbtTag explosion : explosions.items())
			{
				if (explosion.asCompound() != null)
				{
					converted.add(NbtTag.ofCompound(legacyExplosionToCommon(explosion.asCompound())));
				}
			}
			common.putList("explosions", converted);
		}
		return common;
	}

	private static NbtCompound commonToLegacyFireworks(NbtCompound common)
	{
		NbtCompound fireworks = new NbtCompound();
		if (common.has("flight_duration"))
		{
			fireworks.putByte("Flight", (byte) common.getInt("flight_duration", 0));
		}
		NbtList explosions = common.getList("explosions");
		if (explosions != null)
		{
			NbtList converted = new NbtList();
			for (NbtTag explosion : explosions.items())
			{
				if (explosion.asCompound() != null)
				{
					converted.add(NbtTag.ofCompound(commonToLegacyExplosion(explosion.asCompound())));
				}
			}
			fireworks.putList("Explosions", converted);
		}
		return fireworks;
	}

	private static NbtCompound legacyExplosionToCommon(NbtCompound explosion)
	{
		NbtCompound common = new NbtCompound();
		int type = explosion.getInt("Type", 0);
		common.putString("shape", EXPLOSION_SHAPES[type >= 0 && type < EXPLOSION_SHAPES.length ? type : 0]);
		copyIfPresent(explosion, "Colors", common, "colors");
		copyIfPresent(explosion, "FadeColors", common, "fade_colors");
		copyIfPresent(explosion, "Trail", common, "has_trail");
		copyIfPresent(explosion, "Flicker", common, "has_twinkle");
		return common;
	}

	private static NbtCompound commonToLegacyExplosion(NbtCompound common)
	{
		NbtCompound explosion = new NbtCompound();
		String shape = common.getString("shape", EXPLOSION_SHAPES[0]);
		int type = 0;
		for (int i = 0; i < EXPLOSION_SHAPES.length; i++)
		{
			if (EXPLOSION_SHAPES[i].equals(shape))
			{
				type = i;
				break;
			}
		}
		explosion.putByte("Type", (byte) type);
		copyIfPresent(common, "colors", explosion, "Colors");
		copyIfPresent(common, "fade_colors", explosion, "FadeColors");
		copyIfPresent(common, "has_trail", explosion, "Trail");
		copyIfPresent(common, "has_twinkle", explosion, "Flicker");
		return explosion;
	}

	private static NbtCompound containerEntry(int slot, NbtCompound commonItem)
	{
		NbtCompound entry = new NbtCompound();
		entry.putInt(ITEM_CONTAINER_SLOT, slot);
		entry.putCompound(ITEM_CONTAINER_ITEM, commonItem);
		return entry;
	}
}
