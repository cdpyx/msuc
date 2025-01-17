package pers.solid.mishang.uc.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.data.client.Models;
import net.minecraft.data.client.TextureKey;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.predicate.entity.EntityFlagsPredicate;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import pers.solid.brrp.v1.api.RuntimeResourcePack;
import pers.solid.brrp.v1.generator.ItemResourceGenerator;
import pers.solid.brrp.v1.model.ModelJsonBuilder;

import pers.solid.mishang.uc.util.TextBridge;

import java.util.List;

public class ExplosionToolItem extends Item implements HotbarScrollInteraction, DispenserBehavior, ItemResourceGenerator {
  public ExplosionToolItem(Settings settings) {
    super(settings);
    DispenserBlock.registerBehavior(this, this);
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
    final ItemStack stack = user.getStackInHand(hand);
    final HitResult raycast = user.raycast(128, 0, user.isSneaking());
    if (raycast.getType() == HitResult.Type.MISS) {
      return TypedActionResult.fail(stack);
    }
    if (world.isClient) {
      return TypedActionResult.pass(stack);
    }

    final Vec3d pos = raycast.getPos();
    final GameRules.BooleanRule booleanRule = world.getGameRules().get(GameRules.DO_TILE_DROPS);
    final boolean backup = booleanRule.get();
    if (user.isCreative()) {
      // 创造模式下，将游戏规则临时设为不掉落。
      booleanRule.set(false, null);
    }
    Explosion explosion = new Explosion(world, user, user.isSneaking() ? world.getDamageSources().explosion(null) : null, null, pos.x, pos.y, pos.z, power(stack), createFire(stack), destructionType(stack));
    explosion.collectBlocksAndDamageEntities();
    explosion.affectWorld(true);

    // 适用于 1.19.3，因为不是通过 world.createExplosion 实现的，没有向客户端发送消息，所以需要在这里手动发送
    if (!explosion.shouldDestroy()) {
      explosion.clearAffectedBlocks();
    }
    for (PlayerEntity playerEntity : world.getPlayers()) {
      ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) playerEntity;
      if (serverPlayerEntity.squaredDistanceTo(pos.x, pos.y, pos.z) < 4096.0) {
        serverPlayerEntity.networkHandler.sendPacket(new ExplosionS2CPacket(pos.x, pos.y, pos.z, power(stack), explosion.getAffectedBlocks(), explosion.getAffectedPlayers().get(serverPlayerEntity)));
      }
    }
    stack.damage((int) power(stack), user, e -> e.sendToolBreakStatus(hand));
    if (user.isCreative()) {
      booleanRule.set(backup, null);
    }
    return TypedActionResult.success(stack);
  }

  @Override
  public Text getName(ItemStack stack) {
    return TextBridge.translatable(getTranslationKey(stack) + ".formatted", power(stack), TextBridge.translatable("item.mishanguc.explosion_tool.createFire." + createFire(stack)), TextBridge.translatable("item.mishanguc.explosion_tool.destructionType." + destructionType(stack).name().toLowerCase()));
  }

  /**
   * @return 该物品产生的爆炸类型。
   */
  public Explosion.DestructionType destructionType(ItemStack stack) {
    final String destructionType = stack.getOrCreateNbt().getString("destructionType");
    return switch (destructionType) {
      case "none", "keep" -> Explosion.DestructionType.KEEP;
      case "destroy_with_decay", "destroy" -> Explosion.DestructionType.DESTROY_WITH_DECAY;
      default -> Explosion.DestructionType.DESTROY;
    };
  }

  /**
   * @return 物品产生爆炸时，是否造成火焰。
   */
  public boolean createFire(ItemStack stack) {
    return stack.getOrCreateNbt().getBoolean("createFire");
  }

  /**
   * @return 该物品的爆炸力量，用于在爆炸时使用。默认为 4。
   */
  public float power(ItemStack stack) {
    final NbtCompound nbt = stack.getOrCreateNbt();
    return nbt.contains("power", NbtElement.NUMBER_TYPE) ? MathHelper.clamp(nbt.getFloat("power"), -128, 128) : 4;
  }

  public void appendToEntries(ItemGroup.Entries stacks) {
    stacks.add(new ItemStack(this));
    ItemStack stack = new ItemStack(this);
    stack.getOrCreateNbt().putBoolean("createFire", true);
    stacks.add(stack);

    stack = new ItemStack(this);
    stack.getOrCreateNbt().putString("destructionType", "keep");
    stacks.add(stack);

    stack = new ItemStack(this);
    stack.getOrCreateNbt().putString("destructionType", "destroy_with_decay");
    stacks.add(stack);
  }

  @Override
  public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
    super.appendTooltip(stack, world, tooltip, context);
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.1", TextBridge.keybind("key.use").styled(style -> style.withColor(0xdddddd))).formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.2").formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.3").formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.4").formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.5").formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.power", TextBridge.literal(String.valueOf(power(stack))).formatted(Formatting.YELLOW)).formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.createFire", createFire(stack) ? ScreenTexts.YES.copy().formatted(Formatting.GREEN) : ScreenTexts.NO.copy().formatted(Formatting.RED)).formatted(Formatting.GRAY));
    tooltip.add(TextBridge.translatable("item.mishanguc.explosion_tool.tooltip.destructionType", TextBridge.translatable("item.mishanguc.explosion_tool.destructionType." + destructionType(stack).name().toLowerCase()).styled(style -> style.withColor(0x779999))).formatted(Formatting.GRAY));
  }

  @Override
  public void onScroll(int selectedSlot, double scrollAmount, ServerPlayerEntity player, ItemStack stack) {
    final boolean creative = player.isCreative();
    final float power = MathHelper.clamp(power(stack) - (float) scrollAmount, creative ? -128 : 0, creative ? 128 : 64);
    stack.getOrCreateNbt().putFloat("power", power);
  }

  @Override
  public ItemStack dispense(BlockPointer pointer, ItemStack stack) {
    final ServerWorld world = pointer.getWorld();

    final BlockPos basePos = pointer.getPos();
    final Direction direction = pointer.getBlockState().get(DispenserBlock.FACING);
    for (int i = 1; i < 33; i++) {
      final BlockPos pos = basePos.offset(direction, i);
      if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
          && world.getEntitiesByClass(Entity.class, new Box(pos), EntityPredicates.EXCEPT_SPECTATOR.and(Entity::canHit).and(EntityFlagsPredicate.Builder.create().sneaking(false).build()::test)).isEmpty()
      ) {
        continue;
      }
      Explosion explosion = new Explosion(world, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power(stack), createFire(stack), destructionType(stack));
      explosion.collectBlocksAndDamageEntities();
      explosion.affectWorld(true);
      // 适用于 1.19.3，因为不是通过 world.createExplosion 实现的，没有向客户端发送消息，所以需要在这里手动发送
      if (!explosion.shouldDestroy()) {
        explosion.clearAffectedBlocks();
      }
      for (ServerPlayerEntity playerEntity : world.getPlayers()) {
        if (playerEntity.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) < 4096.0) {
          playerEntity.networkHandler.sendPacket(new ExplosionS2CPacket(pos.getX(), pos.getY(), pos.getZ(), power(stack), explosion.getAffectedBlocks(), explosion.getAffectedPlayers().get(playerEntity)));
        }
      }
      if (stack.damage((int) power(stack), pointer.getWorld().getRandom(), null)) {
        stack.setCount(0);
      }
    }
    return stack;
  }

  @Environment(EnvType.CLIENT)
  @Override
  public ModelJsonBuilder getItemModel() {
    // 此物品的模型由普通的资源包手动提供，不在运行时的资源包中。
    return null;
  }

  @Environment(EnvType.CLIENT)
  @Override
  public void writeItemModel(RuntimeResourcePack pack) {
    final ModelJsonBuilder itemModel = getItemModel();
    final Identifier itemModelId = getItemModelId();
    if (itemModel != null) pack.addModel(itemModelId, itemModel);
    final Identifier textureId = getTextureId();
    for (final String name : new String[]{
        "_fire",
        "_4", "_4_fire",
        "_8", "_8_fire",
        "_16", "_16_fire",
        "_32", "_32_fire",
        "_64", "_64_fire",
        "_128", "_128_fire",
    }) {
      pack.addModel(itemModelId.brrp_suffixed(name), ModelJsonBuilder.create(Models.HANDHELD).addTexture(TextureKey.LAYER0, textureId.brrp_suffixed(name)));
    }
  }
}
