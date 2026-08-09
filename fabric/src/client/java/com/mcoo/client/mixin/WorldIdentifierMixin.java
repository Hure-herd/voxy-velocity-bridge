package com.mcoo.client.mixin;

import com.mcoo.client.backend.BackendState;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
