package com.couricraft;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import dev.thejakwe.tuinity.event.MsgCommandEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class CouriCraft extends JavaPlugin implements Listener, EventListener {

    public static CouriCraft instance;
    public FileConfiguration config;
    public ProtocolManager protocolManager;
    public JDA jda;

    @Override
    public void onEnable() {
        instance = this;
        config = getConfig();
        protocolManager = ProtocolLibrary.getProtocolManager();

        getServer().getPluginManager().registerEvents(this, this);

        try {
            jda = JDABuilder.createLight(config.getString("token"), GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(this).build().awaitReady();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "jda build exception", e);
        }
        getLogger().info("CouriCraft Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        jda.shutdown();
        getLogger().info("CouriCraft Plugin Disabled!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerListPing(ServerListPingEvent event) {
        String motdString = "&5&lCouri&6&lCraft &7| &bdiscord&7.&egg&7/&dcouriway&r\n&a";
        List<String> suffix = config.getStringList("motds");
        motdString += suffix.get(ThreadLocalRandom.current().nextInt(suffix.size()));
        event.motd(LegacyComponentSerializer.legacyAmpersand().deserialize(motdString));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void incomingChatMessage(AsyncChatEvent event) {
        event.getPlayer().sendMessage(Component.text("In game chat is disabled. You can /msg a player or talk to them via Discord.", NamedTextColor.RED));
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        event.getPlayer().sendPlayerListHeaderAndFooter(Component.text("Couriway Minecraft"), Component.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerLeave(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    @EventHandler
    public void msgCommand(MsgCommandEvent event) {
        UUID uuid = new UUID(0L, 0L);
        if (event.getSender() instanceof Player p) uuid = p.getUniqueId();

        jda.getTextChannelById(getConfig().getString("channels.messages")).sendMessageEmbeds(new EmbedBuilder()
            .setTitle("Message Sent")
            .setColor(Color.CYAN)
            .setAuthor(event.getSender().getName())
            .setFooter(event.getTarget().getName())
            .setDescription(((TextComponent) event.getMessage()).content())
            .setTimestamp(Instant.now())
            .addField("Author", "`%s`\n`%s`".formatted(event.getSender().getName(), uuid), false)
            .addField("Recipient", "`%s`\n`%s`".formatted(event.getTarget().getName(), event.getTarget().getUniqueId()), false)
            .build()
        ).complete();
    }

    @Override
    public void onEvent(GenericEvent event) {
        try {
            this.getClass().getMethod("handleEvent", event.getClass()).invoke(this, event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {}
    }

    public void handleeEvent(GuildMessageReceivedEvent event) {
        if (event.isWebhookMessage() || event.getAuthor().isSystem() || event.getAuthor().isBot()) return;
        if (event.getChannel().getId().contentEquals(config.getString("channels.whitelist"))) {
            Player player = getServer().getPlayerExact(event.getMessage().getContentRaw());
            if (player == null) {
                event.getChannel().sendMessageEmbeds(
                    new EmbedBuilder()
                        .setTitle("Error")
                        .setColor(Color.RED)
                        .setFooter("CouriCraft")
                        .setTimestamp(Instant.now())
                        .setDescription("Player `" + event.getMessage().getContentRaw() + "` was not found.")
                        .build()
                ).complete();
                return;
            }
            if (player.isWhitelisted()) {
                event.getChannel().sendMessageEmbeds(
                    new EmbedBuilder()
                        .setTitle("Error")
                        .setColor(Color.RED)
                        .setFooter("CouriCraft")
                        .setTimestamp(Instant.now())
                        .setDescription("You are already whitelisted!")
                        .build()
                ).complete();
                return;
            }

            event.getChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Success")
                    .setColor(Color.GREEN)
                    .setFooter("CouriCraft")
                    .setTimestamp(Instant.now())
                    .setDescription("Your account `" + player.getName() + "` was whitelisted successfully\nUUID: `" + player.getUniqueId() + "`")
                    .setThumbnail("https://crafatar.com/renders/head/" + player.getUniqueId())
                    .build()
            ).complete();
        }
    }
}
