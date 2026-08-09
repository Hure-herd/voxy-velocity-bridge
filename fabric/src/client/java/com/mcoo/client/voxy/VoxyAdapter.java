package com.mcoo.client.voxy;

import com.mcoo.client.backend.BackendState;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoxyAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger("voxy-velocity-bridge");

	private static final boolean VOXY_PRESENT = FabricLoader.getInstance().isModLoaded("voxy");

	private VoxyAdapter() {
	}

	public static boolean isVoxyPresent() {
		return VOXY_PRESENT;
	}

	public static long mixBackend(long biomeSeed, String backend) {
		return biomeSeed ^ me.cortex.voxy.commonImpl.WorldIdentifier.mixStafford13(backend.hashCode());
	}

	public static void onBackendConfirmed() {
		if (!VOXY_PRESENT) {
			return;
		}
		if (Minecraft.getInstance().level == null) {
			LOGGER.info("[VVB] world not loaded yet, defer renderer rebuild to JOIN");
			return;
		}
		rebuildRenderer();
	}

	public static void onWorldJoin() {
		if (!VOXY_PRESENT) {
			return;
		}
		if (BackendState.getWorldBackend() == null) {
			LOGGER.info("[VVB] backend not confirmed yet, defer renderer rebuild to CONFIRM");
			return;
		}
		rebuildRenderer();
	}

	public static void rebuildRenderer() {
		if (!VOXY_PRESENT) {
			return;
		}
		IVoxyRenderSystemHolder holder = IVoxyRenderSystemHolder.getNullableHolder();
		if (holder == null) {
			LOGGER.info("[VVB] No voxy renderer holder available, skip rebuild");
			return;
		}
		holder.voxy$shutdownRenderer();
		holder.voxy$setWorld(Minecraft.getInstance().level);
		holder.voxy$createRenderer();
		LOGGER.info("[VVB] Voxy renderer rebuilt, backend = {}", BackendState.getWorldBackend());
	}
}
