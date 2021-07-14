package com.couricraft.jda;

import com.couricraft.CouriCraft;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.OfflinePlayer;
import org.slf4j.Logger;

import java.awt.*;
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
        UUID uuid = Optional.ofNullable(plugin.whitelist.getString(event.getUser().getId())).map(UUID::fromString).orElse(null); // uuid or null
        if (uuid == null) return;
        plugin.whitelist.set(event.getUser().getId(), null);
        OfflinePlayer player = CouriCraft.instance.getServer().getOfflinePlayer(uuid);
        if (!plugin.whitelist.getValues(false).containsValue(uuid)) {
            player.setWhitelisted(false);
            logger.info("User %s left | unwhitelisted acc %s (%s)".formatted(event.getUser().getId(), player.getName(), uuid));
        } else {
            logger.info("User %s left | didnt unwhitelist acc %s (%s)".formatted(event.getUser(), player.getName(), uuid));
        }
        plugin.jda.getTextChannelById(plugin.config.getString("channels.logs")).sendMessageEmbeds(
            new EmbedBuilder()
                .setTitle("User Left")
                .setColor(Color.RED)
                .setAuthor(event.getUser().getAsTag(), event.getUser().getEffectiveAvatarUrl())
                .setFooter("CouriCraft")
                .setTimestamp(Instant.now())
                .setDescription("User %s left, their account `%s` was unwhitelisted.\nUUID: `%s`".formatted(event.getUser().getAsMention(), player.getName(), uuid))
                .build()
        ).queue();
    }
}
