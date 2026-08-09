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

		ClientPlayNetworking.registerGlobalReceiver(VvbBackendPayload.TYPE,
				(payload, context) -> BackendState.handle(payload));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> VoxyAdapter.onWorldJoin());
	}
}
