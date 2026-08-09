package com.mcoo.client.voxy;

import com.mcoo.client.backend.BackendState;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Voxy 兼容层抽象（设计文档 §3.3）。
 *
 * 对 Voxy 的引用全部收敛在这里：Voxy 未安装或版本不兼容时，
 * 相关逻辑通过 isVoxyPresent()/rebuildRenderer() 天然失效并回退到原生行为。
 */
public final class VoxyAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger("voxy-velocity-bridge");

	private static final boolean VOXY_PRESENT = FabricLoader.getInstance().isModLoaded("voxy");

	private VoxyAdapter() {
	}

	public static boolean isVoxyPresent() {
		return VOXY_PRESENT;
	}

	/**
	 * 把 backend 身份混入 biomeSeed，使不同后端得到不同的 WorldIdentifier。
	 */
	public static long mixBackend(long biomeSeed, String backend) {
		return biomeSeed ^ me.cortex.voxy.commonImpl.WorldIdentifier.mixStafford13(backend.hashCode());
	}

	/**
	 * CONFIRM 到达时调用：若新世界已加载则立即重建渲染器，否则等 JOIN 再重建。
	 */
	public static void onBackendConfirmed() {
		if (!VOXY_PRESENT) {
			return;
		}
		if (Minecraft.getInstance().level == null) {
			// 新世界还没加载（setLevel 未发生），identifier 仍是旧后端的；等 JOIN 再重建
			LOGGER.info("[VVB] world not loaded yet, defer renderer rebuild to JOIN");
			return;
		}
		rebuildRenderer();
	}

	/**
	 * 世界加载完成（JOIN）时调用：CONFIRM 已到时重建渲染器。
	 */
	public static void onWorldJoin() {
		if (!VOXY_PRESENT) {
			return;
		}
		if (BackendState.getWorldBackend() == null) {
			// CONFIRM 还没到，worldBackend 未更新；等 CONFIRM 时再重建
			LOGGER.info("[VVB] backend not confirmed yet, defer renderer rebuild to CONFIRM");
			return;
		}
		rebuildRenderer();
	}

	/**
	 * 强制重建 Voxy 渲染器（切服后调用）。
	 *
	 * Voxy 的渲染器在 Minecraft.setLevel / allChanged 时重建，Velocity 后端切换
	 * （同一 TCP 连接内换后端）不走这两条路径，导致渲染器仍绑定旧后端的引擎。
	 * 这里通过 Voxy 公开接口 IVoxyRenderSystemHolder 强制 shutdown + create，
	 * 让渲染器重新绑定当前 ClientLevel（含 backend 混入）对应的引擎。
	 *
	 * 注意：voxy$createRenderer() 用的是 LevelRenderer 内部缓存的 identifier
	 * （voxy$setWorld 时通过 WorldIdentifier.of(level) 计算的）。切服时 setLevel
	 * 先于 CONFIRM 到达，缓存的 identifier 仍是旧 backend 的 mixed hash，导致
	 * createRenderer 重新绑定旧引擎。因此这里先调 voxy$setWorld(当前 level)
	 * 用最新 backend 刷新 identifier 缓存，再 createRenderer。
	 */
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
		// 关键修复：用当前 level + 最新 backend 重新计算 identifier，覆盖 LevelRenderer 的缓存
		// （setWorld 内部会 Objects.equals 短路，此处已 shutdown 所以安全；不相等则更新缓存）
		holder.voxy$setWorld(Minecraft.getInstance().level);
		holder.voxy$createRenderer();
		LOGGER.info("[VVB] Voxy renderer rebuilt, backend = {}", BackendState.getWorldBackend());
	}
}
