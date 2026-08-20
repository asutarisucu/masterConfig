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

package org.asutarisucu.masterConfig.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import org.asutarisucu.masterConfig.MasterConfigService;
import org.asutarisucu.masterConfig.OptionsTextFile;
import org.asutarisucu.masterConfig.Settings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.asutarisucu.masterConfig.MasterConfigMod.LOGGER;

/**
 * Makes a single {@code options.txt} usable by every minecraft version at once.
 *
 * <p>Three things happen here, see DESIGN.md section 6:
 * <ol>
 *     <li>the file is read from and written to the master root instead of the game directory</li>
 *     <li>keys this version does not know about are written back after the game has saved,
 *         because {@code Options#save} only writes the keys it knows</li>
 *     <li>the DataFixer is skipped, because a shared file has no single "saved by" version to fix from</li>
 * </ol>
 *
 * <p>The shape of {@code Options} is identical from 1.18.2 to 26.2
 * ({@code private final File optionsFile}, a {@code (Minecraft, File)} constructor ending in {@code this.load()},
 * public {@code load} / {@code save}, and {@code private CompoundTag dataFix(CompoundTag)}),
 * so no preprocessor branch is needed
 */
@Mixin(Options.class)
public abstract class OptionsMixin
{
	@Mutable
	@Shadow
	@Final
	public File optionsFile;

	/** the whole file as it was right before the game overwrote it, or null when the feature is off */
	@Unique
	private Map<String, String> masterconfig$beforeSave;

	@Unique
	private static boolean masterconfig$isShared()
	{
		return MasterConfigService.resolveTarget(Settings.TARGET_OPTIONS) != null;
	}

	@Unique
	private static boolean masterconfig$shouldPreserve()
	{
		return masterconfig$isShared() && MasterConfigService.getSettings().preserveUnknownOptions;
	}

	/**
	 * Injected right before the constructor calls {@code load()}, because at RETURN the file has already been read
	 */
	@Inject(
			method = "<init>",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V")
	)
	private void masterconfig$redirectOptionsFile(Minecraft minecraft, File gameDir, CallbackInfo ci)
	{
		Path shared = MasterConfigService.resolveTarget(Settings.TARGET_OPTIONS);
		if (shared != null)
		{
			this.optionsFile = shared.toFile();
			LOGGER.info("options.txt redirected to {}", shared);
		}
	}

	/**
	 * In a shared file every key is in the format of whichever version wrote it last, so there is no single
	 * version to fix from. Running the fixers anyway re-applies old migrations to already migrated values:
	 * OptionsMenuBlurrinessFix multiplies by ten again, OptionsAccessibilityOnboardFix and
	 * OptionsSetGraphicsPresetToCustomFix overwrite unconditionally.
	 *
	 * <p>The one case where fixing is still correct is the very first launch after installing this mod,
	 * when an old {@code options.txt} is moved into the master root, so that run is left alone
	 */
	@Inject(method = "dataFix", at = @At("HEAD"), cancellable = true)
	private void masterconfig$skipDataFix(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir)
	{
		if (masterconfig$shouldPreserve() && !MasterConfigService.isOptionsFreshlyMigrated())
		{
			cir.setReturnValue(tag);
		}
	}

	@Inject(method = "save", at = @At("HEAD"))
	private void masterconfig$captureBeforeSave(CallbackInfo ci)
	{
		this.masterconfig$beforeSave = masterconfig$shouldPreserve() ? OptionsTextFile.read(this.optionsFile.toPath()) : null;
	}

	/**
	 * Whatever was in the file before the save but is not in it afterwards is, by definition,
	 * a key this version does not know about. Append it back
	 */
	@Inject(method = "save", at = @At("RETURN"))
	private void masterconfig$restoreUnknownKeys(CallbackInfo ci)
	{
		Map<String, String> before = this.masterconfig$beforeSave;
		this.masterconfig$beforeSave = null;
		if (before == null || before.isEmpty())
		{
			return;
		}

		Path file = this.optionsFile.toPath();
		List<String> dropped = OptionsTextFile.droppedLines(before, OptionsTextFile.read(file));
		if (dropped.isEmpty())
		{
			return;
		}
		try
		{
			OptionsTextFile.append(file, dropped);
			LOGGER.debug("Kept {} option key(s) that this version does not know: {}", dropped.size(), dropped);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to keep the option keys that this version does not know", e);
		}
	}
}
