package com.couricraft.jda;

import com.couricraft.CouriCraft;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
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
            event.getMessage().replyEmbeds(
                new EmbedBuilder()
                    .setTitle("Error")
                    .setColor(Color.RED)
                    .setFooter("CouriCraft")
                    .setTimestamp(Instant.now())
                    .setDescription("Could not find player `" + event.getMessage().getContentRaw() + "`")
                    .build()
            ).queue();
            return;
        }
        OfflinePlayer player = server.getOfflinePlayer(uuid);
        if (whitelist.getValues(false).containsValue(uuid.toString())) {
            logger.info("User %s tried to whitelist acc %s (%s) but it was already whitelisted".formatted(event.getAuthor().getId(), player.getName(), uuid));
            event.getMessage().replyEmbeds(
                new EmbedBuilder()
                    .setTitle("Error")
                    .setColor(Color.RED)
                    .setFooter("CouriCraft")
                    .setTimestamp(Instant.now())
                    .setDescription("Player `" + event.getMessage().getContentRaw() + "` is already whitelisted. Please contact a mod if you believe that this is in error")
                    .build()
            ).queue();
            return;
        }

        whitelist.set(event.getAuthor().getId(), uuid.toString());
        whitelist.save(new File(plugin.getDataFolder(), "whitelist.yml"));
        logger.info("Whitelisted acc %s (%s) via user %s".formatted(player.getName(), uuid, event.getAuthor().getId()));
        player.setWhitelisted(true);
        event.getMessage().replyEmbeds(
            new EmbedBuilder()
                .setTitle("Success")
                .setColor(Color.GREEN)
                .setFooter("CouriCraft")
                .setTimestamp(Instant.now())
                .setDescription("Your account `%s` was whitelisted successfully\nUUID: `%s`".formatted(player.getName(), uuid))
                .setThumbnail("https://crafatar.com/renders/head/%s.png?overlay=true".formatted(uuid))
                .build()
        ).queue();
    }

    public void refreshCommand(GuildMessageReceivedEvent event) throws IOException {
        event.getMessage().addReaction("U+1F44D").queue(); // thumbs up
        logger.info("Refresh Started by %s".formatted(event.getAuthor().getId()));
        TextChannel channel = event.getJDA().getTextChannelById(config.getString("channels.whitelist"));
        whitelist.getValues(false).forEach((u, p) -> {
            logger.debug("[REFRESH] Found User %s UUID %s".formatted(u, p));
            event.getGuild().retrieveMemberById(u).queue(mem -> {
                OfflinePlayer player = server.getOfflinePlayer(UUID.fromString((String) p));
                if (!channel.canTalk(mem)) {
                    player.setWhitelisted(false);
                    whitelist.set(u, null);
                    logger.info("[REFRESH] User %s no longer has access | unwhitelisted acc %s (%s)".formatted(u, player.getName(), p));
                } else {
                    player.setWhitelisted(true); // ensure whitelist.json is up to date
                }
            }, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MEMBER, e -> {
                OfflinePlayer player = server.getOfflinePlayer(UUID.fromString((String) p));
                player.setWhitelisted(false);
                whitelist.set(u, null);
                logger.info("[REFRESH] User %s left | unwhitelisted acc %s (%s)".formatted(u, player.getName(), p));
            }));
        });
        logger.info("Refresh complete");
        whitelist.save(new File(plugin.getDataFolder(), "whitelist.yml"));
        event.getChannel().sendMessage("Refresh complete.").reference(event.getMessage()).queue();
    }

    public void minecraftCommand(GuildMessageReceivedEvent event) {
        String args = event.getMessage().getContentRaw().trim().substring(11).trim();
        UUID uuid = server.getPlayerUniqueId(args);
        if (uuid == null) {
            event.getMessage().reply("Couldn't find minecraft player `%s`".formatted(args)).queue();
            return;
        }

        OfflinePlayer player = server.getOfflinePlayer(uuid);
        String id = whitelist.getValues(false).entrySet().stream().filter(e -> e.getValue().equals(uuid.toString())).findFirst().map(Map.Entry::getKey).map("<@%s>"::formatted).orElse("Not whitelisted");

        event.getMessage().replyEmbeds(
            new EmbedBuilder()
                .setTitle("Player %s".formatted(player.getName()))
                .setColor(Color.BLUE)
                .setFooter("CouriCraft")
                .setTimestamp(Instant.now())
                .setDescription("Discord: %s\nMinecraft: `%s`".formatted(id, player.getName()))
                .setThumbnail("https://crafatar.com/renders/head/%s.png?overlay=true".formatted(uuid))
                .build()
        ).queue();
    }

    public void discordCommand(GuildMessageReceivedEvent event) {
        String args = event.getMessage().getContentRaw().trim().substring(9).trim();
        UUID uuid = Optional.ofNullable(whitelist.getString(args)).map(UUID::fromString).orElse(null);
        if (uuid == null) {
            event.getMessage().reply("They don't have a whitelisted account").queue();
            return;
        }
        OfflinePlayer player = server.getOfflinePlayer(uuid);
        event.getMessage().replyEmbeds(
            new EmbedBuilder()
                .setTitle("Player %s".formatted(player.getName()))
                .setColor(Color.MAGENTA)
                .setFooter("CouriCraft")
                .setTimestamp(Instant.now())
                .setDescription("Discord: <@%s>\nMinecraft: `%s`".formatted(args, player.getName()))
                .setThumbnail("https://crafatar.com/renders/head/%s.png?overlay=true".formatted(uuid))
                .build()
        ).queue();
    }

    public void unlinkCommand(GuildMessageReceivedEvent event) throws IOException {
        String args = event.getMessage().getContentRaw().trim().substring(8).trim();
        UUID uuid = Optional.ofNullable(whitelist.getString(args)).map(UUID::fromString).orElse(server.getPlayerUniqueId(args));
        if (uuid == null) {
            event.getMessage().reply("Couldn't find a user with `%s`".formatted(args)).queue();
            return;
        }

        OfflinePlayer player = server.getOfflinePlayer(uuid);
        player.setWhitelisted(false);
        String id = whitelist.getValues(false).entrySet().stream().filter(e -> e.getValue().equals(uuid.toString())).findFirst().map(Map.Entry::getKey).orElse("Error");
        whitelist.set(id, null);
        whitelist.save(new File(plugin.getDataFolder(), "whitelist.yml"));
        logger.info("User {} unlinked {} ({}) with user {}", event.getAuthor().getId(), player.getName(), uuid, id);

        event.getMessage().reply("Unlinked user <@%s> | `%s`".formatted(id, player.getName())).queue();
    }
}
