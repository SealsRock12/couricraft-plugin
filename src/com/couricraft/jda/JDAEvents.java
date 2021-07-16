package com.couricraft.jda;

import com.couricraft.CouriCraft;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.OfflinePlayer;
import org.slf4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class JDAEvents extends ListenerAdapter {

    final CouriCraft plugin;
    private final JDACommands commands;
    private final Logger logger;

    public JDAEvents(CouriCraft plugin) {
        this.plugin = plugin;
        this.commands = new JDACommands(this);
        this.logger = plugin.getSLF4JLogger();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        try {
            if (event.isWebhookMessage() || event.getAuthor().isSystem() || event.getAuthor().isBot()) return;
            if (event.getChannel().getId().contentEquals(plugin.config.getString("channels.whitelist"))) {
                commands.doWhitelisting(event);
            } else if (event.getChannel().getId().contentEquals(plugin.config.getString("channels.commands"))) {
                if (event.getMessage().getContentRaw().trim().equalsIgnoreCase("-refresh")) {
                    commands.refreshCommand(event);
                } else if (event.getMessage().getContentRaw().trim().toLowerCase().startsWith("-minecraft ")) {
                    commands.minecraftCommand(event);
                } else if (event.getMessage().getContentRaw().trim().toLowerCase().startsWith("-discord ")) {
                    commands.discordCommand(event);
                } else if (event.getMessage().getContentRaw().trim().toLowerCase().startsWith("-unlink ")) {
                    commands.unlinkCommand(event);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex); // shouldnt happen, if it does jda can handle + log
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        try {
            UUID uuid = Optional.ofNullable(plugin.whitelist.getString(event.getUser().getId())).map(UUID::fromString).orElse(null);
            if (uuid == null) return;
            plugin.whitelist.set(event.getUser().getId(), null);
            plugin.whitelist.save(new File(plugin.getDataFolder(), "whitelist.yml"));
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
            player.setWhitelisted(false);
            logger.info("User %s left | unwhitelisted acc %s (%s)".formatted(event.getUser().getId(), player.getName(), uuid));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
