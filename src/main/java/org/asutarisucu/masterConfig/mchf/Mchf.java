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

/**
 * MasterConfig Hotbar Format constants.
 *
 * <p>The DataVersions below are the ones where minecraft changed the on disk shape of an item stack.
 * Every one of them was taken from the fixer that performs the change, in
 * {@code net.minecraft.util.datafix.DataFixers}, not from guesswork
 */
public final class Mchf
{
	public static final int FORMAT_VERSION = 1;

	/** ItemStackComponentizationFix: {@code tag} became {@code components} (1.20.5 snapshot 24w09a) */
	public static final int V_COMPONENTS = 3818;
	/** PlayerHeadBlockProfileFix: the shape of {@code minecraft:profile} changed */
	public static final int V_PROFILE = 3820;
	/** AttributeModifierIdFix: the shape of {@code minecraft:attribute_modifiers} changed */
	public static final int V_ATTRIBUTE_ID = 3945;
	/** AttributeIdPrefixFix: it changed again */
	public static final int V_ATTRIBUTE_PREFIX = 4055;
	/** CustomModelDataExpandFix: {@code minecraft:custom_model_data} int became {@code {floats:[f]}} (1.21.4) */
	public static final int V_CMD_EXPANDED = 4175;
	/** UnflattenTextComponentFix: text components stopped being json strings and became nbt (1.21.5) */
	public static final int V_TEXT_NBT = 4290;
	/** TooltipDisplayComponentFix: {@code show_in_tooltip} moved out into {@code minecraft:tooltip_display} (1.21.5) */
	public static final int V_TOOLTIP_DISPLAY = 4307;

	private static final int[] SUB_TIER_BOUNDS = {
			V_COMPONENTS, V_PROFILE, V_ATTRIBUTE_ID, V_ATTRIBUTE_PREFIX, V_CMD_EXPANDED, V_TEXT_NBT, V_TOOLTIP_DISPLAY
	};

	// root keys of hotbars.dat
	public static final String KEY_FORMAT_VERSION = "FormatVersion";
	public static final String KEY_HOTBARS = "Hotbars";
	public static final String KEY_LAST_SAVED_BY = "LastSavedBy";
	public static final String KEY_RAW = "Raw";
	public static final String KEY_COMMON = "Common";

	// keys of a Common item
	public static final String ITEM_ID = "Id";
	public static final String ITEM_COUNT = "Count";
	public static final String ITEM_DAMAGE = "Damage";
	public static final String ITEM_REPAIR_COST = "RepairCost";
	public static final String ITEM_UNBREAKABLE = "Unbreakable";
	public static final String ITEM_CUSTOM_MODEL_DATA = "CustomModelData";
	public static final String ITEM_ENCHANTMENTS = "Enchantments";
	public static final String ITEM_STORED_ENCHANTMENTS = "StoredEnchantments";
	public static final String ITEM_CUSTOM_NAME = "CustomName";
	public static final String ITEM_LORE = "Lore";
	public static final String ITEM_DYED_COLOR = "DyedColor";
	public static final String ITEM_POTION = "Potion";
	public static final String ITEM_POTION_ID = "Id";
	public static final String ITEM_POTION_COLOR = "Color";
	public static final String ITEM_POTION_EFFECTS = "Effects";
	public static final String ITEM_TRIM = "Trim";
	public static final String ITEM_PROFILE = "Profile";
	public static final String ITEM_FIREWORKS = "Fireworks";
	public static final String ITEM_FIREWORK_EXPLOSION = "FireworkExplosion";
	public static final String ITEM_WRITTEN_BOOK = "WrittenBook";
	public static final String ITEM_WRITABLE_BOOK = "WritableBook";
	public static final String ITEM_CONTAINER = "Container";
	public static final String ITEM_CONTAINER_SLOT = "Slot";
	public static final String ITEM_CONTAINER_ITEM = "Item";
	/** whatever this format could not normalize, kept verbatim */
	public static final String ITEM_EXTRA = "Extra";
	/** the DataVersion Extra came from, so it is only spliced back into a compatible shape */
	public static final String ITEM_EXTRA_FROM = "ExtraFrom";

	/** the shared file, deliberately not called hotbar.nbt because it is not in the vanilla format */
	public static final String STORE_FILE_NAME = "hotbars.dat";
	/** the vanilla file, which stays a per instance working file */
	public static final String NATIVE_FILE_NAME = "hotbar.nbt";
	/** marks a hotbar.nbt this mod generated, so a real one is never silently overwritten */
	public static final String MARKER_KEY = "MasterConfigGenerated";

	public static final int HOTBAR_GROUPS = 9;
	public static final int HOTBAR_SIZE = 9;

	private Mchf()
	{
	}

	public static boolean usesComponents(int dataVersion)
	{
		return dataVersion >= V_COMPONENTS;
	}

	/**
	 * TooltipDisplayComponentFix pulled {@code show_in_tooltip} out of the individual components and
	 * unwrapped what was left, so from 4307 on {@code minecraft:enchantments} is the id to level map itself
	 * rather than {@code {levels: ...}}, and {@code minecraft:dyed_color} is a plain int rather than
	 * {@code {rgb: ...}}
	 */
	public static boolean usesUnwrappedComponents(int dataVersion)
	{
		return dataVersion >= V_TOOLTIP_DISPLAY;
	}

	/**
	 * Two DataVersions in the same sub tier write every item field the same way, so the unnormalized
	 * leftovers of one can be pasted straight into the other
	 */
	public static int subTier(int dataVersion)
	{
		int tier = 0;
		for (int bound : SUB_TIER_BOUNDS)
		{
			if (dataVersion >= bound)
			{
				tier++;
			}
		}
		return tier;
	}

	public static String ensureNamespaced(String id)
	{
		return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
	}
}
