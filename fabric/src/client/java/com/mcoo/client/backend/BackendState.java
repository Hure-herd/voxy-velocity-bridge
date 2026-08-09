package com.mcoo.client.backend;

import com.mcoo.client.network.VvbBackendPayload;
import com.mcoo.client.voxy.VoxyAdapter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端 backend 状态机（设计文档 §3.2）。
 *
 * currentBackend：已确认的当前后端（CONFIRM 后更新）
 * pendingBackend：PREPARE 收到、尚未确认的即将到来的后端
 * worldBackend：当前 ClientLevel 所属后端（后续 Mixin 层将用它选择 Voxy 世界缓存）
 */
public final class BackendState {

	private static final Logger LOGGER = LoggerFactory.getLogger("voxy-velocity-bridge");

	@Nullable
	private static String currentBackend;

	@Nullable
	private static String pendingBackend;

	@Nullable
	private static String worldBackend;

	private BackendState() {
	}

	public static void handle(VvbBackendPayload payload) {
		switch (payload.phase()) {
			case PREPARE -> {
				pendingBackend = payload.backendName();
				LOGGER.info("[VVB] PREPARE: pending backend = {}", pendingBackend);
			}
			case CONFIRM -> {
				String previous = currentBackend;
				currentBackend = payload.backendName();
				worldBackend = currentBackend;
				LOGGER.info("[VVB] {} -> {}", previous == null ? "none" : previous, currentBackend);
				// CONFIRM 时 worldBackend 已更新；渲染器重建时机由 VoxyAdapter 判断（世界未加载则延后）
				VoxyAdapter.onBackendConfirmed();
			}
		}
	}

	@Nullable
	public static String getCurrentBackend() {
		return currentBackend;
	}

	@Nullable
	public static String getPendingBackend() {
		return pendingBackend;
	}

	@Nullable
	public static String getWorldBackend() {
		return worldBackend;
	}
}
