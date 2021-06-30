package com.couricraft;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import dev.thejakwe.tuinity.event.AnvilRenameEvent;
import dev.thejakwe.tuinity.event.MsgCommandEvent;
import dev.thejakwe.tuinity.event.SignTextEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class CouriCraft extends JavaPlugin implements Listener, EventListener {

    public static CouriCraft instance;
    public FileConfiguration config;
    public YamlConfiguration whitelist;
    public ProtocolManager protocolManager;
    public JDA jda;
    public Map<UUID, BukkitTask> tasks = new HashMap<>();

    public final Function<String, String> automod = s -> {
        s = s.replaceAll("[^ -~]", ""); // all non ascii chars
        for (String regex : config.getStringList("automod")) {
            s = s.replaceAll(regex, "#"); // go thru each regex and replace with # to censor | regex not public dont bypass it Okayge
        }
        return s;
    };

    @Override
    public void onEnable() {
        instance = this;
        config = getConfig();
        protocolManager = ProtocolLibrary.getProtocolManager();
        whitelist = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "whitelist.yml"));

        getServer().getPluginManager().registerEvents(this, this);

        try {
            jda = JDABuilder.createLight(config.getString("token"), GatewayIntent.GUILD_MESSAGES).addEventListeners(this).build().awaitReady();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "JDA build exception", e);
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
        tasks.put(event.getPlayer().getUniqueId(), Bukkit.getScheduler().runTaskTimer(this, () -> {
            event.getPlayer().sendPlayerListHeaderAndFooter(
                Component.text("Couri", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text("Craft", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&6TPS: %s".formatted(Bukkit.getTPS()[0]))
            );
        }, 20, 20));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerLeave(PlayerQuitEvent event) {
        event.quitMessage(null);
        tasks.get(event.getPlayer().getUniqueId()).cancel();
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

    @EventHandler
    public void itemRename(AnvilRenameEvent event) {
        event.name(automod.apply(event.name()));
    }

    @EventHandler
    public void signCreate(SignTextEvent event) {
        event.lines(Arrays.stream(event.lines()).map(automod).toArray(String[]::new));
    }

    @Override
    public void onEvent(GenericEvent event) {
        try {
            this.getClass().getMethod("handleEvent", event.getClass()).invoke(this, event);
        } catch (NoSuchMethodException ex) {
            // ignore
        } catch (IllegalAccessException ex) {
            getLogger().log(Level.SEVERE, "IllegalAccessException invoking JDA event", ex);
        } catch (InvocationTargetException ex) { // exception within event handling
            throw new RuntimeException(ex); // move up stack, JDA will handle
        }
    }

    public void handleEvent(GuildMessageReceivedEvent event) throws IOException { // ioex cuz i dont care lmao
        if (event.isWebhookMessage() || event.getAuthor().isSystem() || event.getAuthor().isBot()) return;
        if (event.getChannel().getId().contentEquals(config.getString("channels.whitelist"))) {
            UUID uuid = getServer().getPlayerUniqueId(event.getMessage().getContentRaw());
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
            whitelist.save(new File(getDataFolder(), "whitelist.yml"));
            OfflinePlayer player = getServer().getOfflinePlayer(uuid);
            if (!whitelist.getValues(false).containsValue(old)) {
                OfflinePlayer p = getServer().getOfflinePlayer(old);
                getLogger().info("Unwhitelisted acc %s (%s) | user %s switched to %s (%s)".formatted(p.getName(), old, event.getAuthor().getId(), player.getName(), uuid));
                p.setWhitelisted(false);
            } else {
                getLogger().info("Whitelisted acc %s (%s) via user %s".formatted(player.getName(), uuid, event.getAuthor().getId()));
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
    }

    public void handleEvent(GuildMemberRemoveEvent event) {
        UUID uuid = Optional.ofNullable(whitelist.getString(event.getUser().getId())).map(UUID::fromString).orElse(null);
        if (uuid == null) return;
        whitelist.set(event.getUser().getId(), null);
        OfflinePlayer player = getServer().getOfflinePlayer(uuid);
        if (!whitelist.getValues(false).containsValue(uuid)) {
            player.setWhitelisted(false);
            getLogger().info("User %s left | unwhitelisted acc %s (%s)".formatted(event.getUser().getId(), player.getName(), uuid));
        } else {
            getLogger().info("User %s left | didnt unwhitelist acc %s (%s)".formatted(event.getUser(), player.getName(), uuid));
        }
        jda.getTextChannelById(config.getString("channels.logs")).sendMessageEmbeds(
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
