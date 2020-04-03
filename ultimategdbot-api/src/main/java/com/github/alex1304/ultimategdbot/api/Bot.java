package com.github.alex1304.ultimategdbot.api;

import static java.util.Collections.synchronizedSet;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

import com.github.alex1304.ultimategdbot.api.command.CommandKernel;
import com.github.alex1304.ultimategdbot.api.database.Database;
import com.github.alex1304.ultimategdbot.api.util.PropertyReader;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildEmoji;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.UserData;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import discord4j.rest.util.Snowflake;
import discord4j.store.api.mapping.MappingStoreService;
import discord4j.store.caffeine.CaffeineStoreService;
import discord4j.store.jdk.JdkStoreService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * Represents the bot itself.
 */
public class Bot {
	private static final Logger LOGGER = Loggers.getLogger(Bot.class);
	
	private final BotConfig config;
	private final DiscordClient rest;
	private final Database database = new Database();
	private final CommandKernel cmdKernel = new CommandKernel(this);
	private final PropertyReader pluginProperties;
	private final Set<Plugin> plugins = synchronizedSet(new HashSet<>());
	private final Mono<Snowflake> ownerId;
	
	private volatile GatewayDiscordClient gateway;

	private Bot(BotConfig config, DiscordClient rest, PropertyReader pluginProperties) {
		this.config = config;
		this.rest = rest;
		this.pluginProperties = pluginProperties;
		this.ownerId = rest.getApplicationInfo()
				.map(ApplicationInfoData::owner)
				.map(UserData::id)
				.map(Snowflake::of)
				.cache();
	}
	
	/**
	 * Gets the config of the bot.
	 * 
	 * @return the config
	 */
	public BotConfig getConfig() {
		return config;
	}

	/**
	 * Gets the database of the bot.
	 * 
	 * @return the database
	 */
	public Database getDatabase() {
		return database;
	}

	/**
	 * Gets the command kernel of this bot.
	 * 
	 * @return the command kernel
	 */
	public CommandKernel getCommandKernel() {
		return cmdKernel;
	}

	/**
	 * Gets the REST client of the bot.
	 * 
	 * @return the Discord client
	 */
	public DiscordClient getRest() {
		return rest;
	}

	/**
	 * Gets the gateway connection of the bot.
	 * 
	 * @return the gateway
	 * @throws IllegalStateException if the bot is not logged in
	 */
	public GatewayDiscordClient getGateway() {
		if (gateway == null) {
			throw new IllegalStateException("Gateway connection not initialized");
		}
		return gateway;
	}

	/**
	 * Gets the plugin-specific properties.
	 * 
	 * @return the plugin properties
	 */
	public PropertyReader getPluginProperties() {
		return pluginProperties;
	}

	/**
	 * Gets a Set containing all successfully loaded plugins.
	 * 
	 * @return a Set of Plugin
	 */
	public Set<Plugin> getPlugins() {
		return unmodifiableSet(plugins);
	}
	
	/**
	 * Gets the owner ID by querying application info. Subsequent calls will
	 * directly emit the ID without performing the application info query again.
	 * 
	 * @return a Mono emitting the ID of the bot owner
	 */
	public Mono<Snowflake> getOwnerId() {
		return ownerId;
	}
	
	/**
	 * Sends a message into the debug log channel.
	 * 
	 * @param message the message to send
	 * @return a Mono completing when the log message is sent
	 */
	public Mono<Void> log(String message) {
		return log(mcs -> mcs.setContent(message));
	}

	/**
	 * Sends a message into the debug log channel.
	 * 
	 * @param spec the spec of the message to send
	 * @return a Mono completing when the log message is sent
	 */
	public Mono<Void> log(Consumer<MessageCreateSpec> spec) {
		var specInstance = new MessageCreateSpec();
		spec.accept(specInstance);
		return Mono.justOrEmpty(config.getDebugLogChannelId())
				.map(rest::getChannelById)
				.flatMap(c -> c.createMessage(specInstance.asRequest()))
				.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Failed to send a message to log channel", e)))
				.then();
	}
	
	/**
	 * Gets the String representation of an emoji installed on one of the emoji
	 * servers. If the emoji is not found, the returned value is the given name
	 * wrapped in colons.
	 * 
	 * @param emojiName the name of the emoji to look for
	 * @return a Mono emitting the emoji code corresponding to the given name
	 */
	public Mono<String> getEmoji(String emojiName) {
		var defaultVal = ":" + emojiName + ":";
		if (gateway == null) {
			return Mono.just(defaultVal);
		}
		return Flux.fromIterable(config.getEmojiGuildIds())
				.flatMap(gateway::getGuildById)
				.flatMap(Guild::getEmojis)
				.filter(emoji -> emoji.getName().equalsIgnoreCase(emojiName))
				.next()
				.map(GuildEmoji::asFormat)
				.defaultIfEmpty(defaultVal).onErrorReturn(defaultVal);
	}

	public static Bot buildFromProperties(Properties botProperties, Properties pluginProperties) {
		requireNonNull(botProperties);
		requireNonNull(pluginProperties);
		
		var config = BotConfig.fromProperties(botProperties);
		LOGGER.info("Created bot config: {}", config);
		
		var discordClient = DiscordClient.builder(config.getToken())
				.onClientResponse(ResponseFunction.emptyIfNotFound())
				.onClientResponse(ResponseFunction.emptyOnErrorStatus(RouteMatcher.route(Routes.REACTION_CREATE), 400))
				.onClientResponse(request -> response -> response.timeout(Duration.ofSeconds(10)))
				.build();
		
		return new Bot(config, discordClient, new PropertyReader(pluginProperties));
	}

	public void start() {
		gateway = rest.gateway()
				.setInitialStatus(shard -> config.getStatus())
				.setStoreService(MappingStoreService.create()
						.setMapping(new CaffeineStoreService(builder -> {
							var maxSize = config.getMessageCacheMaxSize();
							if (maxSize >= 1) {
								builder.maximumSize(maxSize);
							}
							return builder;
						}), MessageData.class)
						.setFallback(new JdkStoreService()))
				.setEventDispatcher(new DebugBufferingEventDispatcher(Schedulers.boundedElastic()))
				.setAwaitConnections(false)
				.connect()
				.single()
				.block();
		
		var databaseMappingResources = synchronizedSet(new HashSet<String>());
		Flux.fromIterable(ServiceLoader.load(PluginBootstrap.class))
				.flatMap(pluginBootstrap -> pluginBootstrap.setup(this)
						.single()
						.doOnError(e -> LOGGER.error("Failed to setup plugin " + pluginBootstrap.getClass().getName(), e)))
				.doOnNext(plugins::add)
				.doOnNext(plugin -> databaseMappingResources.addAll(plugin.getDatabaseMappingResources()))
				.doOnNext(plugin -> cmdKernel.addProvider(plugin.getCommandProvider()))
				.doOnNext(plugin -> LOGGER.debug("Plugin {} is providing commands: {}", plugin.getName(), plugin.getCommandProvider()))
				.then(Mono.fromRunnable(() -> {
					database.configure(databaseMappingResources);
					cmdKernel.start();
				}))
				.thenEmpty(Flux.fromIterable(plugins).flatMap(Plugin::runOnReady))
				.then(gateway.onDisconnect())
				.then(Mono.delay(Duration.ofSeconds(10)))
				.block();
	}
}
