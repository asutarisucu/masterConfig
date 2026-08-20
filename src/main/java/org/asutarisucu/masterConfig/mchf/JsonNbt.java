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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.asutarisucu.masterConfig.nbt.NbtCompound;
import org.asutarisucu.masterConfig.nbt.NbtList;
import org.asutarisucu.masterConfig.nbt.NbtTag;

import java.util.Map;

/**
 * Converts between the two shapes minecraft uses for a text component.
 *
 * <p>Up to 1.21.4 a text component was stored as a json string; since 1.21.5 it is stored as nbt.
 * {@code UnflattenTextComponentFix} performs the string to nbt direction by parsing the json and
 * handing it to {@code JsonOps.convertTo(NbtOps)}, which is what this mirrors. MCHF always keeps
 * the json string form so the common format has one canonical shape
 */
public final class JsonNbt
{
	private JsonNbt()
	{
	}

	public static NbtTag jsonToNbt(String json)
	{
		try
		{
			return convert(JsonParser.parseString(json));
		}
		catch (Exception e)
		{
			// same fallback as the vanilla fixer: keep it as a plain string
			return NbtTag.ofString(json);
		}
	}

	public static String nbtToJson(NbtTag tag)
	{
		if (tag.type() == NbtTag.STRING)
		{
			// a text component that is just a string stays a string, but as a *json* string
			return new JsonPrimitive(tag.asString("")).toString();
		}
		return convert(tag).toString();
	}

	private static NbtTag convert(JsonElement element)
	{
		if (element.isJsonObject())
		{
			NbtCompound compound = new NbtCompound();
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
			{
				compound.put(entry.getKey(), convert(entry.getValue()));
			}
			return NbtTag.ofCompound(compound);
		}
		if (element.isJsonArray())
		{
			NbtList list = new NbtList();
			for (JsonElement item : element.getAsJsonArray())
			{
				list.add(convert(item));
			}
			return NbtTag.ofList(list);
		}
		if (element.isJsonNull())
		{
			return NbtTag.ofString("");
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isBoolean())
		{
			return NbtTag.ofBoolean(primitive.getAsBoolean());
		}
		if (primitive.isNumber())
		{
			double value = primitive.getAsDouble();
			return value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) <= Integer.MAX_VALUE
					? NbtTag.ofInt((int) value)
					: NbtTag.ofDouble(value);
		}
		return NbtTag.ofString(primitive.getAsString());
	}

	private static JsonElement convert(NbtTag tag)
	{
		switch (tag.type())
		{
			case NbtTag.COMPOUND:
			{
				JsonObject object = new JsonObject();
				tag.asCompound().entries().forEach((key, value) -> object.add(key, convert(value)));
				return object;
			}
			case NbtTag.LIST:
			{
				JsonArray array = new JsonArray();
				for (NbtTag item : tag.asList().items())
				{
					array.add(convert(item));
				}
				return array;
			}
			case NbtTag.STRING:
				return new JsonPrimitive(tag.asString(""));
			case NbtTag.BYTE:
			case NbtTag.SHORT:
			case NbtTag.INT:
			case NbtTag.LONG:
			case NbtTag.FLOAT:
			case NbtTag.DOUBLE:
				return new JsonPrimitive((Number) tag.value());
			default:
				return JsonNull.INSTANCE;
		}
	}
}
