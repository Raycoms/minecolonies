package com.minecolonies.coremod.items;

import com.ldtteam.structurize.blueprints.v1.BlueprintTagUtils;
import com.ldtteam.structurize.helpers.Settings;
import com.ldtteam.structurize.placement.handlers.placement.PlacementError;
import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.coremod.MineColonies;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.minecolonies.api.util.constant.Constants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.CANT_PLACE_COLONY_IN_OTHER_DIM;

/**
 * Class to handle the placement of the supplychest and with it the supplycamp.
 */
public class ItemSupplyCampDeployer extends AbstractItemMinecolonies
{
    /**
     * The name of the structure
     */
    private static final String SUPPLY_CAMP_STRUCTURE_NAME = "supplycamp";

    /**
     * Offset south/west of the supply camp.
     */
    private static final int OFFSET_DISTANCE = 5;

    /**
     * Offset south/east of the supply camp.
     */
    private static final int OFFSET_LEFT = 0;

    /**
     * Offset y of the supply camp.
     */
    private static final int OFFSET_Y = 0;

    /**
     * Creates a new supplycamp deployer. The item is not stackable.
     *
     * @param properties the properties.
     */
    public ItemSupplyCampDeployer(final Item.Properties properties)
    {
        super("supplycampdeployer", properties.stacksTo(1).tab(ModCreativeTabs.MINECOLONIES));
    }

    @NotNull
    @Override
    public InteractionResult useOn(final UseOnContext ctx)
    {
        if (ctx.getLevel().isClientSide)
        {
            if (!MineColonies.getConfig().getServer().allowOtherDimColonies.get() && !WorldUtil.isOverworldType(ctx.getLevel()))
            {
                LanguageHandler.sendPlayerMessage(ctx.getPlayer(), CANT_PLACE_COLONY_IN_OTHER_DIM);
                return InteractionResult.FAIL;
            }
            placeSupplyCamp(ctx.getClickedPos(), ctx.getPlayer().getDirection());
        }

        return InteractionResult.FAIL;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(final Level worldIn, final Player playerIn, final InteractionHand hand)
    {
        final ItemStack stack = playerIn.getItemInHand(hand);
        if (worldIn.isClientSide)
        {
            if (!MineColonies.getConfig().getServer().allowOtherDimColonies.get() && !WorldUtil.isOverworldType(worldIn))
            {
                LanguageHandler.sendPlayerMessage(playerIn, CANT_PLACE_COLONY_IN_OTHER_DIM);
                return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
            }
            placeSupplyCamp(null, playerIn.getDirection());
        }

        return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
    }

    /**
     * Places a supply camp on the given position looking to the given direction.
     *
     * @param pos       the position to place the supply camp at.
     * @param direction the direction the supply camp should face.
     */
    private void placeSupplyCamp(@Nullable final BlockPos pos, @NotNull final Direction direction)
    {
        if (pos == null)
        {
            MineColonies.proxy.openBuildToolWindow(null, SUPPLY_CAMP_STRUCTURE_NAME, 0);
            return;
        }

        final BlockPos tempPos;
        final int rotations;
        switch (direction)
        {
            case SOUTH:
                tempPos = pos.offset(OFFSET_LEFT, OFFSET_Y, OFFSET_DISTANCE);
                rotations = ROTATE_THREE_TIMES;
                break;
            case NORTH:
                tempPos = pos.offset(-OFFSET_LEFT, OFFSET_Y, -OFFSET_DISTANCE);
                rotations = ROTATE_ONCE;
                break;
            case EAST:
                tempPos = pos.offset(OFFSET_DISTANCE, OFFSET_Y, -OFFSET_LEFT);
                rotations = ROTATE_TWICE;
                break;
            default:
                tempPos = pos.offset(-OFFSET_DISTANCE, OFFSET_Y, OFFSET_LEFT);
                rotations = ROTATE_0_TIMES;
                break;
        }
        MineColonies.proxy.openBuildToolWindow(tempPos, SUPPLY_CAMP_STRUCTURE_NAME, rotations);
    }

    /**
     * Checks if the camp can be placed.
     *
     * @param world              the world.
     * @param pos                the position.
     * @param placementErrorList the list of placement errors.
     * @param placer             the placer.
     * @return true if so.
     */
    public static boolean canCampBePlaced(
      @NotNull final Level world,
      @NotNull final BlockPos pos,
      @NotNull final List<PlacementError> placementErrorList,
      final Player placer)
    {
        if (MineColonies.getConfig().getServer().noSupplyPlacementRestrictions.get())
        {
            return true;
        }

        final BlockPos zeroPos = pos.subtract(Settings.instance.getActiveStructure().getPrimaryBlockOffset());
        final int sizeX = Settings.instance.getActiveStructure().getSizeX();
        final int sizeZ = Settings.instance.getActiveStructure().getSizeZ();

        int groundLevel = zeroPos.getY();
        final BlockPos groundLevelPos = BlueprintTagUtils.getFirstPosForTag(Settings.instance.getActiveStructure(), "groundlevel");
        if (groundLevelPos != null)
        {
            groundLevel = groundLevelPos.getY();
        }

        for (int z = zeroPos.getZ(); z < zeroPos.getZ() + sizeZ; z++)
        {
            for (int x = zeroPos.getX(); x < zeroPos.getX() + sizeX; x++)
            {
                checkIfSolidAndNotInColony(world, new BlockPos(x, groundLevel, z), placementErrorList, placer);

                if (world.getBlockState(new BlockPos(x, groundLevel + 1, z)).getMaterial().isSolid())
                {
                    final PlacementError placementError = new PlacementError(PlacementError.PlacementErrorType.NEEDS_AIR_ABOVE, new BlockPos(x, pos.getY(), z));
                    placementErrorList.add(placementError);
                }
            }
        }

        return placementErrorList.isEmpty();
    }

    /**
     * Check if the there is a solid block at a position and it's not in a colony.
     *
     * @param world              the world.
     * @param pos                the position.
     * @param placementErrorList a list of placement errors.
     * @param placer             the player placing the supply camp.
     */
    private static void checkIfSolidAndNotInColony(final Level world, final BlockPos pos, @NotNull final List<PlacementError> placementErrorList, final Player placer)
    {
        final boolean isSolid = world.getBlockState(pos).getMaterial().isSolid();
        final boolean notInAnyColony = hasPlacePermission(world, pos, placer);
        if (!isSolid)
        {
            final PlacementError placementError = new PlacementError(PlacementError.PlacementErrorType.NOT_SOLID, pos);
            placementErrorList.add(placementError);
        }
        if (!notInAnyColony)
        {
            final PlacementError placementError = new PlacementError(PlacementError.PlacementErrorType.INSIDE_COLONY, pos);
            placementErrorList.add(placementError);
        }
    }

    /**
     * Check if a coordinate is in any colony.
     *
     * @param world  the world to check in.
     * @param pos    the position.
     * @param placer the placer.
     * @return true if no colony found.
     */
    private static boolean hasPlacePermission(final Level world, final BlockPos pos, final Player placer)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
        return colony == null || colony.getPermissions().hasPermission(placer, Action.PLACE_BLOCKS);
    }
}
