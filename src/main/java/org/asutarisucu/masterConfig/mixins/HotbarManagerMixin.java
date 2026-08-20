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

import net.minecraft.SharedConstants;
import net.minecraft.client.HotbarManager;
import org.asutarisucu.masterConfig.HotbarSharing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shares the creative hotbars through the master root.
 *
 * <p>Nothing is redirected here. The game keeps using its own {@code hotbar.nbt}; this only refills that
 * file from {@code hotbars.dat} before the game reads it, and folds the result back after the game wrote it.
 * Because both directions work on the nbt file rather than on ItemStack objects, the 1.20.5 componentization
 * needs no special handling in the mixin itself, and the vanilla DataFixer keeps doing the upgrade work.
 *
 * <p>The class is {@code net.minecraft.client.HotbarManager}, not {@code client.player.inventory.HotbarManager}
 */
@Mixin(HotbarManager.class)
public abstract class HotbarManagerMixin
{
	@Unique
	private static int masterconfig$currentDataVersion()
	{
		//#if MC >= 12108
		//$$ return SharedConstants.getCurrentVersion().dataVersion().version();
		//#else
		return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
		//#endif
	}

	@Inject(method = "load", at = @At("HEAD"))
	private void masterconfig$fillFromMaster(CallbackInfo ci)
	{
		HotbarSharing.beforeLoad(masterconfig$currentDataVersion());
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void masterconfig$storeToMaster(CallbackInfo ci)
	{
		HotbarSharing.afterSave(masterconfig$currentDataVersion());
	}
}
