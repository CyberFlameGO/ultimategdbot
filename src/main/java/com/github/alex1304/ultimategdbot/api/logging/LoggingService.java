package com.github.alex1304.ultimategdbot.api.logging;

import com.github.alex1304.ultimategdbot.api.BotConfig;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.discordjson.json.ImmutableMessageCreateRequest;
import discord4j.discordjson.possible.Possible;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class LoggingService {
	
	public static final String CONFIG_RESOURCE_NAME = "logging";
	
	private static final Logger LOGGER = Loggers.getLogger(LoggingService.class);
	
	private final DiscordClient rest;
	private final Snowflake debugLogChannelId;

	public LoggingService(BotConfig botConfig, DiscordClient rest) {
		this.rest = rest;
		this.debugLogChannelId = botConfig.resource(CONFIG_RESOURCE_NAME)
				.readOptional("debug_log_channel_id")
				.map(Snowflake::of)
				.orElse(null);
	}
	
	public Mono<Void> log(String message) {
		return Mono.defer(() -> {
			if (debugLogChannelId == null) {
				return Mono.empty();
			}
			return Mono.just(debugLogChannelId)
					.map(rest::getChannelById)
					.flatMap(c -> c.createMessage(ImmutableMessageCreateRequest.builder().content(Possible.of(message)).build()))
					.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Failed to send a message to log channel: " + message, e)))
					.then();
		});
	}
}
