package com.mcoo.client;

import com.mcoo.client.backend.BackendState;
import com.mcoo.client.network.VvbBackendPayload;
import com.mcoo.client.voxy.VoxyAdapter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class VoxyVelocityBridgeClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		PayloadTypeRegistry.clientboundPlay().register(VvbBackendPayload.TYPE, VvbBackendPayload.STREAM_CODEC);

		// handler 在 render thread 调用，可直接更新状态；CONFIRM 时自动重建 Voxy 渲染器
		ClientPlayNetworking.registerGlobalReceiver(VvbBackendPayload.TYPE,
				(payload, context) -> BackendState.handle(payload));

		// 世界加载完成时：若 CONFIRM 已到则重建渲染器（CONFIRM 先到而世界未加载的场景由这里兜底）
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> VoxyAdapter.onWorldJoin());
	}
}
