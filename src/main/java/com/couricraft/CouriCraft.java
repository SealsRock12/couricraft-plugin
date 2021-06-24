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
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import okhttp3.OkHttpClient;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class CouriCraft extends JavaPlugin implements Listener, EventListener {

    public static CouriCraft instance;
    public FileConfiguration config;
    public ProtocolManager protocolManager;
    public JDA jda;
    public OkHttpClient httpClient;

    @Override
    public void onEnable() {
        instance = this;
        config = getConfig();
        protocolManager = ProtocolLibrary.getProtocolManager();
        httpClient = new OkHttpClient();

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
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerJoin(PlayerJoinEvent event) {
        event.joinMessage(Component.empty());
        event.getPlayer().sendPlayerListHeaderAndFooter(Component.text("Couriway Minecraft"), Component.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerLeave(PlayerQuitEvent event) {
        event.quitMessage(Component.empty());
    }

    @EventHandler
    public void msgCommand(MsgCommandEvent event) {
        event.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Pee Pee Pants");
            return true;
        }

        if (command.getName().equalsIgnoreCase("msg")) {
            Player target = Bukkit.getPlayer(args[0]);
            Player source = (Player) sender;
            args[0] = ""; // drop first element hack
            if (target == null) {
                source.sendMessage(Component.text("That player doesn't exist or is offline.").color(NamedTextColor.RED));
                return true;
            }

            target.sendMessage(source,
                LegacyComponentSerializer.legacyAmpersand().deserialize("&d[&aFrom &6" + source.getName() + "&d]&f" + String.join(" ", args))
                    .clickEvent(ClickEvent.suggestCommand("/msg " + source.getName() + " ")),
            MessageType.CHAT);


            sender.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&d[&aTo &6" + target.getName() + "&d]&f" + String.join(" ", args))
                    .clickEvent(ClickEvent.suggestCommand("/msg " + target.getName() + " "))
            );

            jda.getTextChannelById(getConfig().getString("channels.messages")).sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Message Sent")
                .setColor(Color.CYAN)
                .setAuthor(sender.getName())
                .setFooter(target.getName())
                .setDescription(String.join(" ", args))
                .setTimestamp(Instant.now())
                .addField("Author", formatPlayer(source), false)
                .addField("Recipient", formatPlayer(target), false)
                .build()
            ).complete();
            return true;
        }
        return false;
    }

    @Override
    public void onEvent(GenericEvent event) {
        try {
            this.getClass().getMethod("handleEvent", event.getClass()).invoke(this, event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {}
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

    private static String formatPlayer(Player p) {
        return "`" + p.getName() + "`\n`" + p.getUniqueId() + "`";
    }
}
