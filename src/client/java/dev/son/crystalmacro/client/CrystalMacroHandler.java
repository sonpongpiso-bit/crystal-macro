package dev.son.crystalmacro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side crystal macro. All world changes go through vanilla
 * {@link MultiPlayerGameMode} interactions so the server validates them.
 */
public final class CrystalMacroHandler {
	private static final long OBSIDIAN_PLACE_COOLDOWN_MS = 500L;

	private static boolean performingVanillaInteraction;
	private static boolean sequenceBusy;
	private static long lastObsidianPlaceTimeMs;
	@Nullable
	private static BlockPos lastObsidianPlacePos;

	private CrystalMacroHandler() {
	}

	public static boolean isPerformingVanillaInteraction() {
		return performingVanillaInteraction;
	}

	public static boolean shouldSuppressVanillaUse() {
		Minecraft client = Minecraft.getInstance();
		return isMacroActive(client);
	}

	public static void onClientTick(Minecraft client) {
		if (!isMacroActive(client)) {
			resetTransientState();
			return;
		}

		if (sequenceBusy) {
			return;
		}

		sequenceBusy = true;
		try {
			runSequence(client);
		} finally {
			sequenceBusy = false;
		}
	}

	private static void resetTransientState() {
		sequenceBusy = false;
		performingVanillaInteraction = false;
	}

	private static boolean isMacroActive(Minecraft client) {
		if (client == null || client.player == null || client.level == null || client.gameMode == null) {
			return false;
		}
		if (client.screen != null) {
			return false;
		}
		if (!client.options.keyUse.isDown()) {
			return false;
		}
		if (client.gameMode.isSpectator() || client.gameMode.getPlayerMode() == GameType.ADVENTURE) {
			return false;
		}
		return isHoldingEndCrystal(client.player);
	}

	private static void runSequence(Minecraft client) {
		HitResult hit = currentHit(client);
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
			Entity entity = entityHit.getEntity();
			if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
				breakCrystal(client, crystal);
			}
			return;
		}

		if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
			return;
		}

		BlockPos lookedPos = blockHit.getBlockPos();
		if (!client.level.getBlockState(lookedPos).is(Blocks.OBSIDIAN)) {
			tryPlaceObsidian(client, blockHit);
			return;
		}

		if (!stillLookingAt(client, lookedPos)) {
			return;
		}

		EndCrystal existing = findCrystalOnObsidian(client, lookedPos);
		if (existing != null) {
			if (stillLookingAt(client, lookedPos) || isLookingAtCrystal(client, existing)) {
				breakCrystal(client, existing);
			}
			return;
		}

		if (!canPlaceCrystalAt(client, lookedPos)) {
			return;
		}

		if (!stillLookingAt(client, lookedPos)) {
			return;
		}

		if (!placeCrystal(client, blockHit, lookedPos)) {
			return;
		}

		if (!stillLookingAt(client, lookedPos) && !isLookingAtCrystalOn(client, lookedPos)) {
			return;
		}

		EndCrystal placed = findCrystalOnObsidian(client, lookedPos);
		if (placed != null && (stillLookingAt(client, lookedPos) || isLookingAtCrystal(client, placed))) {
			breakCrystal(client, placed);
		}
	}

	private static void tryPlaceObsidian(Minecraft client, BlockHitResult blockHit) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			return;
		}

		BlockPos lookedPos = blockHit.getBlockPos();
		if (client.level.getBlockState(lookedPos).is(Blocks.OBSIDIAN)) {
			return;
		}

		BlockPos destination = obsidianDestination(client, blockHit);
		if (destination == null) {
			return;
		}

		if (client.level.getBlockState(destination).is(Blocks.OBSIDIAN)) {
			return;
		}

		if (lastObsidianPlacePos != null && lastObsidianPlacePos.equals(destination)) {
			if (client.level.getBlockState(destination).is(Blocks.OBSIDIAN)) {
				return;
			}
		}

		long now = System.currentTimeMillis();
		if (now - lastObsidianPlaceTimeMs < OBSIDIAN_PLACE_COOLDOWN_MS) {
			return;
		}

		if (!(currentHit(client) instanceof BlockHitResult liveHit)) {
			return;
		}
		if (!liveHit.getBlockPos().equals(lookedPos) || liveHit.getDirection() != blockHit.getDirection()) {
			return;
		}

		InteractionHand obsidianHand = findObsidianHand(player);
		int previousSlot = player.getInventory().getSelectedSlot();
		boolean switched = false;

		if (obsidianHand == null) {
			int obsidianSlot = findHotbarSlot(player, Items.OBSIDIAN);
			if (obsidianSlot < 0) {
				return;
			}
			if (obsidianSlot != previousSlot) {
				player.getInventory().setSelectedSlot(obsidianSlot);
				switched = true;
			}
			obsidianHand = InteractionHand.MAIN_HAND;
		}

		InteractionResult result = useItemOn(client, obsidianHand, liveHit);

		if (switched) {
			ItemStack previous = player.getInventory().getItem(previousSlot);
			if (previous.is(Items.END_CRYSTAL) && player.getInventory().getSelectedSlot() != previousSlot) {
				player.getInventory().setSelectedSlot(previousSlot);
			}
		}

		if (result.consumesAction()) {
			lastObsidianPlaceTimeMs = now;
			lastObsidianPlacePos = destination;
			player.swing(obsidianHand);
		}
	}

	private static boolean placeCrystal(Minecraft client, BlockHitResult originalHit, BlockPos expectedObsidian) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return false;
		}

		HitResult live = currentHit(client);
		if (!(live instanceof BlockHitResult liveHit)) {
			return false;
		}
		if (!liveHit.getBlockPos().equals(expectedObsidian)) {
			return false;
		}
		if (!client.level.getBlockState(expectedObsidian).is(Blocks.OBSIDIAN)) {
			return false;
		}
		if (!canPlaceCrystalAt(client, expectedObsidian)) {
			return false;
		}

		InteractionHand crystalHand = findCrystalHand(player);
		if (crystalHand == null) {
			return false;
		}

		InteractionResult result = useItemOn(client, crystalHand, liveHit);
		if (result.consumesAction()) {
			player.swing(crystalHand);
			return true;
		}
		return false;
	}

	private static void breakCrystal(Minecraft client, EndCrystal crystal) {
		LocalPlayer player = client.player;
		MultiPlayerGameMode gameMode = client.gameMode;
		if (player == null || gameMode == null || !crystal.isAlive()) {
			return;
		}

		HitResult live = currentHit(client);
		boolean validTarget = false;
		if (live instanceof EntityHitResult entityHit && entityHit.getEntity() == crystal) {
			validTarget = true;
		} else if (live instanceof BlockHitResult blockHit) {
			BlockPos looked = blockHit.getBlockPos();
			if (client.level != null && client.level.getBlockState(looked).is(Blocks.OBSIDIAN)
					&& isCrystalOnObsidian(crystal, looked)) {
				validTarget = true;
			}
		}
		if (!validTarget) {
			return;
		}

		performingVanillaInteraction = true;
		try {
			gameMode.attack(player, crystal);
			player.swing(InteractionHand.MAIN_HAND);
		} finally {
			performingVanillaInteraction = false;
		}
	}

	private static InteractionResult useItemOn(Minecraft client, InteractionHand hand, BlockHitResult hit) {
		LocalPlayer player = client.player;
		MultiPlayerGameMode gameMode = client.gameMode;
		if (player == null || gameMode == null) {
			return InteractionResult.FAIL;
		}

		performingVanillaInteraction = true;
		try {
			return gameMode.useItemOn(player, hand, hit);
		} finally {
			performingVanillaInteraction = false;
		}
	}

	@Nullable
	private static HitResult currentHit(Minecraft client) {
		return client.hitResult;
	}

	private static boolean stillLookingAt(Minecraft client, BlockPos pos) {
		HitResult hit = currentHit(client);
		return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos);
	}

	private static boolean isLookingAtCrystal(Minecraft client, EndCrystal crystal) {
		HitResult hit = currentHit(client);
		return hit instanceof EntityHitResult entityHit && entityHit.getEntity() == crystal;
	}

	private static boolean isLookingAtCrystalOn(Minecraft client, BlockPos obsidianPos) {
		HitResult hit = currentHit(client);
		if (!(hit instanceof EntityHitResult entityHit)) {
			return false;
		}
		return entityHit.getEntity() instanceof EndCrystal crystal && isCrystalOnObsidian(crystal, obsidianPos);
	}

	@Nullable
	private static BlockPos obsidianDestination(Minecraft client, BlockHitResult hit) {
		if (client.level == null) {
			return null;
		}

		BlockPos clicked = hit.getBlockPos();
		BlockState clickedState = client.level.getBlockState(clicked);
		if (clickedState.isAir()) {
			return null;
		}

		if (clickedState.canBeReplaced()) {
			return clicked;
		}

		BlockPos destination = clicked.relative(hit.getDirection());
		BlockState destinationState = client.level.getBlockState(destination);
		if (!destinationState.canBeReplaced() && !destinationState.isAir()) {
			return null;
		}
		return destination;
	}

	private static boolean canPlaceCrystalAt(Minecraft client, BlockPos obsidianPos) {
		if (client.level == null) {
			return false;
		}
		if (!client.level.getBlockState(obsidianPos).is(Blocks.OBSIDIAN)) {
			return false;
		}

		BlockPos above = obsidianPos.above();
		if (!client.level.getBlockState(above).isAir()) {
			return false;
		}

		AABB spawnBox = new AABB(
				above.getX(),
				above.getY(),
				above.getZ(),
				above.getX() + 1.0,
				above.getY() + 2.0,
				above.getZ() + 1.0
		);
		for (Entity entity : client.level.getEntities((Entity) null, spawnBox)) {
			if (entity.isAlive()) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	private static EndCrystal findCrystalOnObsidian(Minecraft client, BlockPos obsidianPos) {
		if (client.level == null) {
			return null;
		}

		BlockPos above = obsidianPos.above();
		AABB box = new AABB(
				above.getX(),
				above.getY(),
				above.getZ(),
				above.getX() + 1.0,
				above.getY() + 2.0,
				above.getZ() + 1.0
		);
		EndCrystal found = null;
		for (Entity entity : client.level.getEntities((Entity) null, box)) {
			if (entity instanceof EndCrystal crystal && crystal.isAlive() && isCrystalOnObsidian(crystal, obsidianPos)) {
				found = crystal;
				break;
			}
		}
		return found;
	}

	private static boolean isCrystalOnObsidian(EndCrystal crystal, BlockPos obsidianPos) {
		BlockPos crystalBase = BlockPos.containing(crystal.getX(), crystal.getY(), crystal.getZ());
		return crystalBase.equals(obsidianPos.above()) || crystalBase.equals(obsidianPos);
	}

	private static boolean isHoldingEndCrystal(LocalPlayer player) {
		return findCrystalHand(player) != null;
	}

	@Nullable
	private static InteractionHand findCrystalHand(LocalPlayer player) {
		if (player.getMainHandItem().is(Items.END_CRYSTAL)) {
			return InteractionHand.MAIN_HAND;
		}
		if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	@Nullable
	private static InteractionHand findObsidianHand(LocalPlayer player) {
		if (player.getOffhandItem().is(Items.OBSIDIAN)) {
			return InteractionHand.OFF_HAND;
		}
		if (player.getMainHandItem().is(Items.OBSIDIAN)) {
			return InteractionHand.MAIN_HAND;
		}
		return null;
	}

	private static int findHotbarSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).is(item)) {
				return slot;
			}
		}
		return -1;
	}
}
