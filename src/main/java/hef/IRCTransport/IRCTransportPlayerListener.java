package hef.IRCTransport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handle events for all Player related events.
 * @author hef
 */
 public final class IRCTransportPlayerListener extends PlayerListener {
    /** Maps to retrieve associated IrcAggent from player. */
    private HashMap<Player, IrcAgent> bots;
    /** Reference to the parent plugin. */
    private final IRCTransport plugin;

    /**
     * @param instance A reference to the plugin.
     */
    public IRCTransportPlayerListener(final IRCTransport instance) {
        this.bots = instance.getBots();
        plugin = instance;
    }

    @Override
    public void onPlayerChat(final PlayerChatEvent event) {
        IrcAgent bot = this.bots.get(event.getPlayer());
        bot.sendMessage(event.getMessage());
        // prevent messages from being displayed twice.
        event.setCancelled(true);
    }

    @Override
    public void onPlayerJoin(final PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            Connection conn = plugin.makeSqlConn();
            PreparedStatement stmt = conn.prepareStatement("SELECT uo.user, u.enabled FROM " +
                    "user_options AS uo JOIN users AS u ON u.id = uo.user WHERE " +
                    "uo.minecraft_username = ?");
            stmt.setString(1, player.getName());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next() || !rs.getBoolean(2)) {
                player.kickPlayer("Not authorized. See http://socialgamer.net/mc/");
                rs.close();
                stmt.close();
                conn.close();
                return;
            }
            rs.close();
            stmt.close();
            conn.close();
        } catch (Exception e) {
            player.kickPlayer("Unable to verify account. See http://socialgamer.net/mc/");
            return;
        }
        this.bots.put(player, new IrcAgent(plugin, player));
    }

    @Override
    public void onPlayerQuit(final PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (this.bots.containsKey(player)) {
            this.bots.get(player).shutdown();
            this.bots.get(player).dispose();
            this.bots.remove(player);
        }
    }
}
