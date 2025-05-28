package com.example.mixin.client;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Enumeration;

import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.CopyToClipboard;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.util.Formatting;
import net.minecraft.text.Style;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(IntegratedServer.class)
public class TunnelMixin {
	private static final Set<String> invitedPlayers = new HashSet<>();

	private Socket tunnelSocket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;

	private String lanIp = null;
	private int lanPort = 0;

	private final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

	@Inject(method = "setupServer", at = @At("RETURN"))
	private void onSetupServer(CallbackInfoReturnable<Boolean> cir) {
		IntegratedServer server = (IntegratedServer)(Object)this;

		server.getCommandManager().getDispatcher().register(
				CommandManager.literal("invite")
						.then(CommandManager.argument("player", StringArgumentType.word())
								.executes(context -> invitePlayer(context))
						)
		);
		try {
			if (out != null) out.close();
			if (in != null) in.close();
			if (tunnelSocket != null && !tunnelSocket.isClosed()) tunnelSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		out = null;
		in = null;
		tunnelSocket = null;

	}

	private int invitePlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		String playerName = StringArgumentType.getString(context, "player");
		context.getSource().sendFeedback(() -> Text.literal("Player " + playerName + " invited!"), false);
		return 1;
	}

	@Inject(method = "openToLan", at = @At("RETURN"))
	private void onOpenToLan(GameMode gameMode, boolean cheatsAllowed, int port, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			lanIp = getLocalIPv4Address();
			lanPort = port;

			new Thread(() -> {
				try {
					tunnelSocket = new Socket("86.163.174.83", 5000);
					out = new PrintWriter(tunnelSocket.getOutputStream(), true);
					in = new BufferedReader(new InputStreamReader(tunnelSocket.getInputStream()));

					out.println("LOCAL:" + lanIp + ":" + lanPort);

					String response = in.readLine();
					if (response != null && !response.isEmpty()) {
						sendMessageToPlayers("Your LAN world is exposed at " + response, response);
					} else {
						sendMessageToPlayers("Failed to receive public address from tunnel server.", null);
					}

					String line;
					while ((line = in.readLine()) != null) {
						if (line.equals("NEW_CONNECTION")) {
							THREAD_POOL.execute(this::handleNewConnection);
						}
					}
				} catch (IOException e) {
					sendMessageToPlayers("Tunnel connection lost: " + e.getMessage(), null);
					e.printStackTrace();
				}
			}).start();

			IntegratedServer server = (IntegratedServer) (Object) this;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				player.sendMessage(Text.literal("Multiplayer mod is active and your LAN world is opening!"), false);
			}
		}
	}

	private void handleNewConnection() {
		try {
			if (tunnelSocket == null || tunnelSocket.isClosed()) return;

			Socket localSocket = new Socket(lanIp, lanPort);

			THREAD_POOL.execute(() -> {
				try {
					pipeStream(tunnelSocket.getInputStream(), localSocket.getOutputStream());
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			THREAD_POOL.execute(() -> {
				try {
					pipeStream(localSocket.getInputStream(), tunnelSocket.getOutputStream());
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

		} catch (IOException e) {
			sendMessageToPlayers("Error handling new connection: " + e.getMessage(), null);
			e.printStackTrace();
		}
	}

	private void pipeStream(InputStream in, OutputStream out) {
		try {
			byte[] buffer = new byte[8192];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
				out.flush();
			}
		} catch (IOException e) {
		} finally {
			try { out.close(); } catch (IOException ignored) {}
			try { in.close(); } catch (IOException ignored) {}
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void onServerStop(CallbackInfo ci) {
		try {
			if (out != null) out.close();
			if (in != null) in.close();
			if (tunnelSocket != null && !tunnelSocket.isClosed()) tunnelSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		out = null;
		in = null;
		tunnelSocket = null;
	}

	private void sendMessageToPlayers(String message, String copyText) {
		IntegratedServer server = (IntegratedServer) (Object) this;

		Text text;
		if (copyText != null) {
			text = Text.literal(message).setStyle(
					Style.EMPTY.withColor(Formatting.GREEN)
							.withClickEvent(new CopyToClipboard(copyText))
							.withHoverEvent(new ShowText(Text.literal("Click to copy")))
			);
		} else {
			text = Text.literal(message).formatted(Formatting.RED);
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.sendMessage(text, false);
		}
	}

	private static String getLocalIPv4Address() {
		try {
			Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
			for (NetworkInterface netint : java.util.Collections.list(nets)) {
				if (netint.isUp() && !netint.isLoopback()) {
					Enumeration<InetAddress> addresses = netint.getInetAddresses();
					for (InetAddress addr : java.util.Collections.list(addresses)) {
						if (addr instanceof java.net.Inet4Address) {
							return addr.getHostAddress();
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "127.0.0.1"; // fallback
	}
}
