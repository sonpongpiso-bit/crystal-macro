package dev.son.crystalmacro.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;

public final class CrystalMacroClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(CrystalMacroHandler::onClientTick);

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (CrystalMacroHandler.isPerformingVanillaInteraction()) {
				return InteractionResult.PASS;
			}
			if (CrystalMacroHandler.shouldSuppressVanillaUse()) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (CrystalMacroHandler.isPerformingVanillaInteraction()) {
				return InteractionResult.PASS;
			}
			if (CrystalMacroHandler.shouldSuppressVanillaUse()) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (CrystalMacroHandler.isPerformingVanillaInteraction()) {
				return InteractionResult.PASS;
			}
			if (CrystalMacroHandler.shouldSuppressVanillaUse()) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}
}
