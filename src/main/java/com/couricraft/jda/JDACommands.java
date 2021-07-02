package com.couricraft.jda;

import com.couricraft.CouriCraft;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class JDACommands {

    private final JDAEvents events;
    private final CouriCraft plugin;
    private final Logger logger;
    private final Server server;
    private final YamlConfiguration whitelist;
    private final FileConfiguration config;


    public JDACommands(JDAEvents events) {
        this.events = events;
        this.plugin = events.plugin;
        this.logger = events.plugin.getSLF4JLogger();
        this.server = events.plugin.getServer();
        this.whitelist = events.plugin.whitelist;
        this.config = events.plugin.config;
    }


    public void doWhitelisting(GuildMessageReceivedEvent event) throws Exception {
        UUID uuid = server.getPlayerUniqueId(event.getMessage().getContentRaw());
        if (uuid == null) {
            event.getChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Error")
                    .setColor(Color.RED)
                    .setFooter("CouriCraft")
                    .setTimestamp(Instant.now())
                    .setDescription("Could not find player `" + event.getMessage().getContentRaw() + "`")
                    .build()
            ).reference(event.getMessage()).queue();
            return;
        }

        UUID old = Optional.ofNullable(whitelist.getString(event.getAuthor().getId())).map(UUID::fromString).orElse(null);
        whitelist.set(event.getAuthor().getId(), uuid.toString());
        whitelist.save(new File(plugin.getDataFolder(), "whitelist.yml"));
        OfflinePlayer player = server.getOfflinePlayer(uuid);
        if (!whitelist.getValues(false).containsValue(old)) {
            OfflinePlayer p = server.getOfflinePlayer(old);
            logger.info("Unwhitelisted acc %s (%s) | user %s switched to %s (%s)".formatted(p.getName(), old, event.getAuthor().getId(), player.getName(), uuid));
            p.setWhitelisted(false);
        } else {
            logger.info("Whitelisted acc %s (%s) via user %s".formatted(player.getName(), uuid, event.getAuthor().getId()));
        }
        player.setWhitelisted(true);
        event.getChannel().sendMessageEmbeds(
            new EmbedBuilder()
                .setTitle("Success")
                .setColor(Color.GREEN)
                .setFooter("CouriCraft")
                .setTimestamp(Instant.now())
                .setDescription("Your account `%s` was whitelisted successfully\nUUID: `%s`".formatted(player.getName(), uuid))
                .setThumbnail("https://crafatar.com/renders/head/%s.png?overlay=true".formatted(uuid))
                .build()
        ).reference(event.getMessage()).queue();
    }

    public void refreshCommand(GuildMessageReceivedEvent event) {
        event.getMessage().addReaction("U+1F44D").queue(); // thumbs up
        logger.info("Refresh Started by %s".formatted(event.getAuthor().getId()));
        TextChannel channel = event.getJDA().getTextChannelById(config.getString("channels.whitelist"));
        whitelist.getValues(false).forEach((u, p) -> {
            logger.trace("[REFRESH] Found User %s UUID %s".formatted(u, p));
            event.getGuild().retrieveMemberById(u).queue(mem -> {
                if (!channel.canTalk(mem)) {
                    OfflinePlayer player = server.getOfflinePlayer(UUID.fromString((String) p));
                    if (!whitelist.getValues(false).containsValue(p)) {
                        player.setWhitelisted(false);
                        logger.info("[REFRESH] User %s no longer has access | unwhitelisted acc %s (%s)".formatted(u, player.getName(), p));
                    } else {
                        logger.info("[REFRESH] User %s no longer has access | didnt unwhitelist acc %s (%s)".formatted(u, player.getName(), p));
                    }
                }
            }, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MEMBER, e -> {
                OfflinePlayer player = server.getOfflinePlayer(UUID.fromString((String) p));
                if (!whitelist.getValues(false).containsValue(p)) {
                    player.setWhitelisted(false);
                    logger.info("[REFRESH] User %s left | unwhitelisted acc %s (%s)".formatted(u, player.getName(), p));
                } else {
                    logger.info("[REFRESH] User %s left | didnt unwhitelist acc %s (%s)".formatted(u, player.getName(), p));
                }
            }));
        });
        logger.info("");
    }
}
