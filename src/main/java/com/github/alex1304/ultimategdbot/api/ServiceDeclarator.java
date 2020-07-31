package com.github.alex1304.ultimategdbot.api;

import java.util.Set;

import com.github.alex1304.rdi.config.ServiceDescriptor;

/**
 * Declares one or more services to register to the bot.
 */
public interface ServiceDeclarator {
	
	/**
	 * Declares service descriptors to register to the bot's service container.
	 * 
	 * @param botConfig the bot configuration
	 * @return a Set containing services to declare
	 */
	Set<ServiceDescriptor> declareServices(BotConfig botConfig);
}
