package com.mcoo.client.backend;

import com.mcoo.client.network.VvbBackendPayload;
import com.mcoo.client.voxy.VoxyAdapter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
