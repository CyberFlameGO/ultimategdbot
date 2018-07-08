package com.github.alex1304.ultimategdbot.modules.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.alex1304.jdash.api.request.GDLevelHttpRequest;
import com.github.alex1304.jdash.api.request.GDLevelSearchHttpRequest;
import com.github.alex1304.jdash.component.GDComponentList;
import com.github.alex1304.jdash.component.GDLevel;
import com.github.alex1304.jdash.component.GDLevelPreview;
import com.github.alex1304.jdashevents.common.CommonEvents;
import com.github.alex1304.jdashevents.customcomponents.GDUpdatedComponent;
import com.github.alex1304.jdashevents.manager.GDEventManager;
import com.github.alex1304.ultimategdbot.core.UltimateGDBot;
import com.github.alex1304.ultimategdbot.exceptions.CommandFailedException;
import com.github.alex1304.ultimategdbot.exceptions.GDServersUnavailableException;
import com.github.alex1304.ultimategdbot.exceptions.InvalidCommandArgsException;
import com.github.alex1304.ultimategdbot.exceptions.ModuleUnavailableException;
import com.github.alex1304.ultimategdbot.modules.Module;
import com.github.alex1304.ultimategdbot.modules.commands.impl.account.AccountCommand;
import com.github.alex1304.ultimategdbot.modules.commands.impl.help.HelpCommand;
import com.github.alex1304.ultimategdbot.modules.commands.impl.leaderboard.LeaderboardMenu;
import com.github.alex1304.ultimategdbot.modules.commands.impl.level.LevelCommand;
import com.github.alex1304.ultimategdbot.modules.commands.impl.modules.ModulesCommand;
import com.github.alex1304.ultimategdbot.modules.commands.impl.profile.ProfileCommand;
import com.github.alex1304.ultimategdbot.modules.commands.impl.setup.SetupCommand;
import com.github.alex1304.ultimategdbot.modules.reply.Reply;
import com.github.alex1304.ultimategdbot.modules.reply.ReplyModule;
import com.github.alex1304.ultimategdbot.utils.BotRoles;
import com.github.alex1304.ultimategdbot.utils.BotUtils;

import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;

/**
 * Module that manages and handles bot commands
 *
 * @author Alex1304
 */
public class CommandsModule implements Module {

	private Map<String, Command> commandMap;
	
	private boolean isEnabled;

	public CommandsModule() {
		this.isEnabled = false;
		this.commandMap = new HashMap<>();
		registerCommands();
	}

	@Override
	public void start() {
		isEnabled = true;
	}

	@Override
	public void stop() {
		isEnabled = false;
	}

	/**
	 * Puts a command into the map, associated by name
	 * @param cmd
	 */
	public void registerCommand(String name, Command cmd) {
		commandMap.put(name, cmd);
	}
	
	/**
	 * This is where the command map is loaded with command instances
	 */
	private void registerCommands() {
		registerCommand("ping", (event, args) -> {
			BotUtils.sendMessage(event.getChannel(), "Pong! :ping_pong:");
		});
		
		registerCommand("help", new HelpCommand());
		registerCommand("modules", new ModulesCommand());
		registerCommand("level", new LevelCommand());
		registerCommand("profile", new ProfileCommand());
		registerCommand("account", new AccountCommand());
		registerCommand("leaderboard", (event, args) -> executeCommand(new LeaderboardMenu(), event, args));
		registerCommand("setup", new SetupCommand());
		
		// Temporary commands
		
		registerCommand("ratelevel", (event, args) -> {
			if (args.isEmpty())
				throw new InvalidCommandArgsException("`" + event.getMessage().getContent() + " <level ID>`");
			
			long id = -1;
			
			try {
				id = Long.parseLong(args.get(0));
			} catch (NumberFormatException e) {
				throw new CommandFailedException("Invalid ID");
			}
			
			final long fid = id;
			GDComponentList<GDLevelPreview> lplist = (GDComponentList<GDLevelPreview>) UltimateGDBot.cache()
					.readAndWriteIfNotExists("gd.levelsearch." + fid, () ->
							UltimateGDBot.gdClient().fetch(new GDLevelSearchHttpRequest("" + fid, 0)));
			
			if (lplist == null)
				throw new GDServersUnavailableException();
			if (lplist.isEmpty())
				throw new CommandFailedException("Level not found");
			
			GDEventManager.getInstance().dispatch(CommonEvents.AWARDED_LEVEL_ADDED, lplist);
		});
		
		registerCommand("unratelevel", (event, args) -> {
			if (args.isEmpty())
				throw new InvalidCommandArgsException("`" + event.getMessage().getContent() + " <level ID>`");
			
			long id = -1;
			
			try {
				id = Long.parseLong(args.get(0));
			} catch (NumberFormatException e) {
				throw new CommandFailedException("Invalid ID");
			}
			
			final long fid = id;
			GDComponentList<GDLevelPreview> lplist = (GDComponentList<GDLevelPreview>) UltimateGDBot.cache()
					.readAndWriteIfNotExists("gd.levelsearch." + fid, () ->
							UltimateGDBot.gdClient().fetch(new GDLevelSearchHttpRequest("" + fid, 0)));
			
			if (lplist == null)
				throw new GDServersUnavailableException();
			if (lplist.isEmpty())
				throw new CommandFailedException("Level not found");
			
			GDEventManager.getInstance().dispatch(CommonEvents.AWARDED_LEVEL_DELETED, lplist);
		});
		
		registerCommand("changeratedlevel", (event, args) -> {
			if (args.size() < 2)
				throw new InvalidCommandArgsException("`" + event.getMessage().getContent() + " <original level ID> <new level ID>`");
			
			long id = -1;
			long id2 = -1;
			
			try {
				id = Long.parseLong(args.get(0));
				id2 = Long.parseLong(args.get(1));
			} catch (NumberFormatException e) {
				throw new CommandFailedException("Invalid ID");
			}
			
			final long fid = id;
			final long fid2 = id2;
			
			GDComponentList<GDLevelPreview> lplist = (GDComponentList<GDLevelPreview>) UltimateGDBot.cache()
					.readAndWriteIfNotExists("gd.levelsearch." + fid, () ->
							UltimateGDBot.gdClient().fetch(new GDLevelSearchHttpRequest("" + fid, 0)));
			
			GDComponentList<GDLevelPreview> lplist2 = (GDComponentList<GDLevelPreview>) UltimateGDBot.cache()
					.readAndWriteIfNotExists("gd.levelsearch." + fid2, () ->
							UltimateGDBot.gdClient().fetch(new GDLevelSearchHttpRequest("" + fid2, 0)));
			
			if (lplist == null || lplist2 == null)
				throw new GDServersUnavailableException();
			if (lplist.isEmpty() || lplist2.isEmpty())
				throw new CommandFailedException("Level(s) not found");
			
			GDComponentList<GDUpdatedComponent<GDLevelPreview>> updated = new GDComponentList<>();
			updated.add(new GDUpdatedComponent<>(lplist.get(0), lplist2.get(0)));
			
			GDEventManager.getInstance().dispatch(CommonEvents.AWARDED_LEVEL_UPDATED, updated);
		});
		

		registerCommand("newdaily", (event, args) -> {
			if (args.isEmpty())
				throw new InvalidCommandArgsException("`" + event.getMessage().getContent() + " <level ID>`");
			
			long id = -1;
			
			try {
				id = Long.parseLong(args.get(0));
			} catch (NumberFormatException e) {
				throw new CommandFailedException("Invalid ID");
			}
			
			final long fid = id;
			GDLevel lvl = (GDLevel) UltimateGDBot.cache()
					.readAndWriteIfNotExists("gd.level." + fid, () ->
							UltimateGDBot.gdClient().fetch(new GDLevelHttpRequest(fid)));
			
			if (lvl == null)
				throw new CommandFailedException("Level not found");
			
			GDEventManager.getInstance().dispatch(CommonEvents.DAILY_LEVEL_CHANGED, new GDUpdatedComponent<>(lvl, lvl));
		});

		registerCommand("newweekly", (event, args) -> {
			if (args.isEmpty())
				throw new InvalidCommandArgsException("`" + event.getMessage().getContent() + " <level ID>`");
			
			long id = -1;
			
			try {
				id = Long.parseLong(args.get(0));
			} catch (NumberFormatException e) {
				throw new CommandFailedException("Invalid ID");
			}
			
			final long fid = id;
			GDLevel lvl = (GDLevel) UltimateGDBot.cache()
					.readAndWriteIfNotExists("gd.level." + fid, () ->
							UltimateGDBot.gdClient().fetch(new GDLevelHttpRequest(fid)));
			
			if (lvl == null)
				throw new CommandFailedException("Level not found");
			
			GDEventManager.getInstance().dispatch(CommonEvents.WEEKLY_DEMON_CHANGED, new GDUpdatedComponent<>(lvl, lvl));
		});
	}
	
	/**
	 * Executes a command asynchronously. Works even if the module is stopped.
	 * 
	 * @param cmd - The command instance
	 * @param event - The message received event containing context info of the command
	 * @param args - The arguments of the command
	 */
	public static void executeCommand(Command cmd, MessageReceivedEvent event, List<String> args) {
		new Thread(() -> {
			try {
				if (BotRoles.isGrantedAll(event.getAuthor(), event.getChannel(), cmd.getRolesRequired()))
					cmd.runCommand(event, args);
				else
					throw new CommandFailedException("You don't have permission to use this command");
			} catch (CommandFailedException e) {
				BotUtils.sendMessage(event.getChannel(), ":negative_squared_cross_mark: " + e.getMessage());
			} catch (DiscordException e) {
				BotUtils.sendMessage(event.getChannel(), ":negative_squared_cross_mark: Sorry, an error occured"
						+ " while running the command.\n```\n" + e.getErrorMessage() + "\n```");
				System.err.println(e.getErrorMessage());
			} catch (Exception e) {
				BotUtils.sendMessage(event.getChannel(), "An internal error occured while running the command."
						+ " Please try again later.");
				UltimateGDBot.logException(e);
			}
		}).start();
	}
	
	/**
	 * Handles the message received event from Discord and runs the command if
	 * prefix and user permissions match
	 * 
	 * @param event - Contains context of the message received
	 */
	public void onMessageReceived(MessageReceivedEvent event) {
		if (!isEnabled && !BotRoles.isGranted(event.getAuthor(), event.getChannel(), BotRoles.OWNER))
			return;
		
		if (event.getAuthor().isBot())
			return;
		
		String[] argArray = event.getMessage().getContent().split(" ");

		if (argArray.length == 0)
			return;
		
		String prefixUsed = BotUtils.prefixUsedInMessage(argArray[0]);
		boolean isMentionPrefix = BotUtils.isMentionPrefix(argArray[0]);
		
		if (prefixUsed == null)
			return;

		final String cmdName = isMentionPrefix ?
				argArray[1] : argArray[0].substring(prefixUsed.length()).toLowerCase();
		final List<String> args = new ArrayList<>(Arrays.asList(argArray));
		
		if (isMentionPrefix)
			args.remove(0);
		args.remove(0);
		
		if (commandMap.containsKey(cmdName)) {
			// Before executing the command, cancel any opened reply for the current user/channel
			try {
				ReplyModule rm = (ReplyModule) UltimateGDBot.getModule("reply");
				Reply r = rm.getReply(event.getChannel(), event.getAuthor());
				if (r != null)
					r.cancel();
			} catch (ModuleUnavailableException e) {
			}
			executeCommand(commandMap.get(cmdName), event, args);
		}
	}

	/**
	 * Gets the commandMap
	 *
	 * @return Map&lt;String,Command&gt;
	 */
	public Map<String, Command> getCommandMap() {
		return commandMap;
	}

}
