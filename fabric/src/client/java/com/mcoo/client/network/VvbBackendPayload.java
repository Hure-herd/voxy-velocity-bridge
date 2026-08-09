package com.mcoo.client.network;

import com.mcoo.VoxyVelocityBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VvbBackendPayload(Phase phase, String backendName) implements CustomPacketPayload {

	public static final Type<VvbBackendPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("vvb", "backend"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VvbBackendPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, p -> p.phase().id,
			ByteBufCodecs.STRING_UTF8, VvbBackendPayload::backendName,
			(phaseId, name) -> new VvbBackendPayload(Phase.fromId(phaseId), name)
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public enum Phase {
		PREPARE((byte) 0x01),
		CONFIRM((byte) 0x02);

		public final byte id;

		Phase(byte id) {
			this.id = id;
		}

		public static Phase fromId(byte id) {
			for (Phase phase : values()) {
				if (phase.id == id) {
					return phase;
				}
			}
			throw new IllegalArgumentException("Unknown VVB phase: " + id);
		}
	}
}
