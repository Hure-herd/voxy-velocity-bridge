package com.mcoo.vvb;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Plugin(
		id = "vvb-velocity",
		name = "Voxy Velocity Bridge",
		version = "1.0.0",
		description = "Voxy LOD world cache isolation across backend servers",
		authors = {"Hureherd"}
)
public class VvbVelocityPlugin {

	public static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("vvb", "backend");

	private final ProxyServer proxy;
	private final Logger logger;

	@Inject
	public VvbVelocityPlugin(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
		proxy.getChannelRegistrar().register(CHANNEL);
		logger.info("[VVB] channel {} registered", CHANNEL.getId());
	}

	@Subscribe
	public void onPreConnect(ServerPreConnectEvent event) {
		Player player = event.getPlayer();
		String backend = event.getOriginalServer().getServerInfo().getName();
		send(player, Phase.PREPARE, backend);
	}

	@Subscribe
	public void onPostConnect(ServerPostConnectEvent event) {
		Player player = event.getPlayer();
		player.getCurrentServer().ifPresent(connection -> {
			String backend = connection.getServerInfo().getName();
			send(player, Phase.CONFIRM, backend);
		});
	}

	private void send(Player player, Phase phase, String backend) {
		logger.info("[VVB] {} -> {} ({})", player.getUsername(), backend, phase.name());
		player.sendPluginMessage(CHANNEL, encode(phase, backend));
	}

	private static byte[] encode(Phase phase, String backend) {
		byte[] name = backend.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(phase.id);
		writeVarInt(bos, name.length);
		bos.writeBytes(name);
		return bos.toByteArray();
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	private enum Phase {
		PREPARE((byte) 0x01),
		CONFIRM((byte) 0x02);

		final byte id;

		Phase(byte id) {
			this.id = id;
		}
	}
}
