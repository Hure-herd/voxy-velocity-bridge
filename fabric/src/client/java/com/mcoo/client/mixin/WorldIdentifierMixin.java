package com.mcoo.client.mixin;

import com.mcoo.client.backend.BackendState;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 核心注入点（设计文档 §3.3 Mixin 兼容层 · 注入点 1）。
 *
 * WorldIdentifier.of(Level) 是 Voxy 获取世界标识的唯一入口：
 *  - getWorldId() 磁盘路径只由 biomeSeed + key 决定
 *  - hashCode/equals（内存引擎缓存）由 key + biomeSeed + dimension 决定
 *
 * 因此把 backend 混入 biomeSeed 一处注入，即可同时隔离磁盘目录、内存引擎、配置存储。
 * backend 为 null（无代理/未收到 payload）时完全走 Voxy 原生逻辑（fail-safe 回退）。
 *
 * require = 0：Voxy 缺失/不兼容时静默回退（正式版行为）。
 */
@Mixin(WorldIdentifier.class)
public class WorldIdentifierMixin {

	@Inject(method = "of", at = @At("RETURN"), cancellable = true, require = 0)
	private static void vvb$injectBackend(Level level, CallbackInfoReturnable<WorldIdentifier> cir) {
		String backend = BackendState.getWorldBackend();
		WorldIdentifier original = cir.getReturnValue();
		if (backend == null || backend.isEmpty() || original == null) {
			return;
		}
		long mixedSeed = original.biomeSeed ^ WorldIdentifier.mixStafford13(backend.hashCode());
		cir.setReturnValue(new WorldIdentifier(original.key, mixedSeed, original.dimension));
	}
}
