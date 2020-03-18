import com.github.simplenet.Client;
import com.github.simplenet.packet.Packet;
import engine.command.Command;
import engine.command.CommandParser;
import engine.command.CommandResult;
import engine.command.CommandStorage;
import engine.configs.Config;
import engine.debug.Log;
import engine.entity.Player;
import engine.events.HandleEvent;
import engine.events.server.*;
import engine.server.PacketTypes;
import engine.server.ServerBase;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer extends ServerBase {
	private CommandStorage commandStorage;
	private ConcurrentHashMap<Client, Player> clients;

	public GameServer() {
		addEventHandler(this);
		init();
		start();
	}

	public static void main(String[] args) {
		new GameServer();
	}

	public CommandStorage getCommandStorage() {
		return commandStorage;
	}

	@HandleEvent(ServerStartEvent.PRE_INIT)
	public void onPreInit(ServerStartEvent event) {

		//Enable Console colors
		AnsiConsole.systemInstall();

		//Check for and load or create new server configuration
		Log.info("Starting Server PreInitialization...");
		if (!Files.exists(Paths.get("server.config"))) {
			Log.info("Creating Server Configurations...");
			Config config = Config.builder()
					.setFileName("server")
					.addKey("ip", "localhost")
					.addKey("port", "49056")
					.addKey("motd", "Message of the Day!")
					.build();
			if (config.save()) {
				return;
			}
		} else {
			Log.info("Initializing...");
			return;
		}
		Log.error("Something went wrong during server initialization, the server will not start.");
	}

	@HandleEvent(ServerStartEvent.INIT)
	public void onInit(ServerStartEvent event) {
		this.clients = new ConcurrentHashMap<>();

		//Initialize Command Storage
		this.commandStorage = new CommandStorage();

		//Create commands
		getCommandStorage().addCommand(
				Command.builder()
						.alias("hello", "hi")
						.addSubCommand(
								Command.builder()
										.alias("all")
										.executes((player, arguments) -> {
											getServer().queueAndFlushToAllExcept(Packet.builder().putByte(PacketTypes.CHAT).putString(player.getUsername() + ": said hi."));
											return CommandResult.success();
										})
										.build()
						)
						.executes((player, arguments) -> {
							Log.info(player.getUsername() + " said hello.");
							return CommandResult.success();
						})
						.build()
		);

		getCommandStorage().addCommand(
				Command.builder()
						.alias("stop")
						.executes(((player, arguments) -> {
							//TODO this needs to use a permission system probably
							setShouldClose(true);
							return CommandResult.success();
						}))
						.build()
		);
	}

	@HandleEvent(ServerStartEvent.POST_INIT)
	public void onPostInit(ServerStartEvent event) {
		try {
			Config config = Config.load("server");
			setAddress(config.getOptions().get("ip"));
			setPort(Integer.parseInt(config.getOptions().get("port")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@HandleEvent(ServerStartEvent.START)
	public void onStart(ServerStartEvent event) {
		Log.info("Server Started.");
	}

	@HandleEvent(ServerCommandReceivedEvent.RECEIVED)
	public void onCommand(ServerCommandReceivedEvent event) {
		CommandResult result = CommandParser.parse(event.getCommand(), clients.get(event.getClient()));
		if (!result.wasSuccessful()) {
			Packet.builder().putByte(PacketTypes.CHAT).putString("Error: " + result.getFeedback()).queueAndFlush(event.getClient());
		} else {
			if (result.getFeedback().length() > 0) {
				Packet.builder().putByte(PacketTypes.CHAT).putString(result.getFeedback()).queueAndFlush(event.getClient());
			}
		}
	}

	@HandleEvent(ServerClientPacketReceivedEvent.RECEIVED)
	public void onClientValidation(ServerClientPacketReceivedEvent event) {
		clients.put(event.getClient(), new Player(event.getUsername()));
		Log.info("Client packet received: " + event.getUsername() + " successfully joined.");
	}

	@HandleEvent(ServerChatEvent.RECEIVED)
	public void onChat(ServerChatEvent event) {
		getServer().queueAndFlushToAllExcept(Packet.builder().putByte(PacketTypes.CHAT).putString(clients.get(event.getClient()).getUsername() +
				": " + event.getMessage()), event.getClient());
	}

	@HandleEvent(ServerClientConnectionEvent.POST_DISCONNECT)
	public void postDisconnect(ServerClientConnectionEvent event) {
		if (clients.get(event.getClient()) != null) {
			Log.info(clients.get(event.getClient()).getUsername() + " left.");
			clients.remove(event.getClient());
		}
	}
}
