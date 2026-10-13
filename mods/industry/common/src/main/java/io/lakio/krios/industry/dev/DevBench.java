package io.lakio.krios.industry.dev;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tick-model benchmark (responsiveness question, 2026-10-13): two block
 * types without items and the {@code /krios_industry bench} subtree.
 * Temporary dev tooling, removed once the ADR settles the model.
 */
public final class DevBench {

    public static final Supplier<Block> NODE = Services.REGISTRAR.register(BuiltInRegistries.BLOCK,
            Constants.id("bench_node"), () -> new BenchNodeBlock(BlockBehaviour.Properties.of().noLootTable()));

    public static final Supplier<Block> CONTROLLER = Services.REGISTRAR.register(BuiltInRegistries.BLOCK,
            Constants.id("bench_controller"), () -> new ControllerBlock(BlockBehaviour.Properties.of().noLootTable()));

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<BenchNodeBlockEntity>> NODE_BE = () ->
            (BlockEntityType<BenchNodeBlockEntity>) Holder.NODE_BE.get();

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<ControllerBlockEntity>> CONTROLLER_BE = () ->
            (BlockEntityType<ControllerBlockEntity>) Holder.CONTROLLER_BE.get();

    /** Registered after the blocks (the types reference them). */
    private static final class Holder {
        static final Supplier<BlockEntityType<?>> NODE_BE = Services.REGISTRAR.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.id("bench_node"),
                () -> Services.REGISTRAR.createBlockEntityType(BenchNodeBlockEntity::new, NODE.get()));
        static final Supplier<BlockEntityType<?>> CONTROLLER_BE = Services.REGISTRAR.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.id("bench_controller"),
                () -> Services.REGISTRAR.createBlockEntityType(ControllerBlockEntity::new, CONTROLLER.get()));
    }

    private DevBench() {
    }

    public static void init() {
        // Forces static registration (blocks, then block entity types).
        Holder.NODE_BE.getClass();
    }

    /** {@code bench suite <n>}, {@code bench run <n> <mode>}, {@code bench stop}. */
    public static LiteralArgumentBuilder<CommandSourceStack> commandTree() {
        return Commands.literal("bench")
                .then(Commands.literal("suite")
                        .then(Commands.argument("nodes", IntegerArgumentType.integer(1, 20_000))
                                .executes(context -> start(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "nodes"),
                                        List.of(BenchMode.VALUES)))))
                .then(Commands.literal("run")
                        .then(Commands.argument("nodes", IntegerArgumentType.integer(1, 20_000))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "nodes"),
                                                List.of(BenchMode.BASELINE, BenchMode.valueOf(
                                                        StringArgumentType.getString(context, "mode")
                                                                .toUpperCase())))))))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            BenchRunner.stop();
                            context.getSource().sendSuccess(() -> Component.literal("Bench stopped."), false);
                            return 1;
                        }));
    }

    private static int start(CommandSourceStack source, int nodes, List<BenchMode> modes) {
        if (BenchRunner.isRunning()) {
            source.sendFailure(Component.literal("A bench is already running (bench stop)."));
            return 0;
        }
        BenchRunner.start(source.getLevel(), BlockPos.containing(source.getPosition()), nodes, modes);
        return 1;
    }

    /** Drives the runner once per server tick. */
    public static final class ControllerBlock extends BaseEntityBlock {

        private static final MapCodec<ControllerBlock> CODEC = simpleCodec(ControllerBlock::new);

        public ControllerBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ControllerBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return level.isClientSide ? null : (l, p, s, be) -> BenchRunner.tick();
        }
    }

    public static final class ControllerBlockEntity extends BlockEntity {

        public ControllerBlockEntity(BlockPos pos, BlockState state) {
            super(CONTROLLER_BE.get(), pos, state);
        }
    }
}
