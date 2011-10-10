package hef.IRCTransport;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

/**
 * @author hef
 */
public final class IrcAgent extends PircBot {
    private static final Logger log = Logger.getLogger("Minecraft");
    private String activeChannel;
    private Player player;
    final private IRCTransport plugin;
    private AgentSettings settings;
    // flag to indicate we should not reconnect
    private boolean shuttingDown;

    public IrcAgent(IRCTransport instance, Player player) {
        this.plugin = instance;
        this.player = player;
        this.shuttingDown = false;
        setLogin(String.format(player.getName()));
        super.setAutoNickChange(true);

        // init player settings
        setSettings(plugin.getDatabase().find(AgentSettings.class,
                player.getName()));
        if (null == getSettings()) {
            setSettings(new AgentSettings(player));
            getSettings().setIrcNick(
                    String.format("%s%s%s", plugin.getNickPrefix(),
                            player.getName(), plugin.getNickSuffix()));
        } else {
            log.log(Level.INFO, String.format(
                    "Player '%s' using persistent IRC nick '%s'",
                    player.getName(), getSettings().getIrcNick()));
        }
        setNick(getSettings().getIrcNick());
        player.setDisplayName(getSettings().getIrcNick());
        new Connect(this).run();
    }

    public String getActiveChannel() {
        return this.activeChannel;
    }

    public Player getPlayer() {
        return player;
    }

    public IRCTransport getPlugin() {
        return plugin;
    }

    /**
     * @return the settings
     */
    public AgentSettings getSettings() {
        return settings;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public void log(String line) {
        if (getPlugin().isVerbose()) {
            log.log(Level.INFO, line);
        }
    }

    protected void names() {
        names(activeChannel);
    }

    protected void names(String channel) {
        sendRawLine("NAMES " + channel);
    }

    @Override
    public void onAction(String sender, String login, String hostname,
            String target, String action) {
        getPlayer().sendMessage(formatMessage(sender, action, true));
    }

    @Override
    public void onDisconnect() {
        getPlayer().sendMessage("ChatService Disconnected.");
        if (!shuttingDown) {
            new Connect(this).run();
        }
    }

    protected void onErrorMessage(String channel, String message) {
        getPlayer().sendMessage(
                ChatColor.YELLOW + String.format("[%s] %s", channel, message));
    }

    @Override
    public void onJoin(String channel, String sender, String login,
            String hostname) {
        // if I joined, change active channel.
        if (sender.equals(getNick()))
            activeChannel = channel;
        getPlayer()
                .sendMessage(
                        ChatColor.YELLOW
                                + String.format("[%s] %s has joined.", channel,
                                        sender));
    }

    @Override
    protected void onKick(String channel, String kickerNick,
            String kickerLogin, String kickerHostname, String recipientNick,
            String reason) {
        player.sendMessage(ChatColor.YELLOW
                + String.format("[%s] %s kicked by %s: %s", channel,
                        recipientNick, kickerNick, reason));
    }

    /**
     * Formats a message string based on originating server.
     * @param sender The IRC nick which sent the message.
     * @param message The message which was sent.
     * @param isAction Whether this message is an action.
     * @return Formatted string to display to the Minecraft player.
     */
    private String formatMessage(String sender, String message, boolean isAction) {
        // Check for SocialGamer server prefixes in the nick.
        String server = "IRC";
        Configuration config = plugin.getConfig();
        List<String> keys = config.getKeys("SocialGamer.ServerPrefixes");
        for (String key : keys) {
            if (sender.startsWith(key + "_")) {
                server = config.getString("SocialGamer.ServerPrefixes." + key);
                sender = sender.substring(key.length() + 1);
            }
        }
        String prefix = " ";
        Player p = player.getServer().getPlayer(sender);
        if (p != null) {
            if (p.hasPermission("irctransport.prefix.admin")) {
                prefix = "~";
            } else if (p.hasPermission("irctransport.prefix.op")) {
                prefix = "@";
            } else if (p.hasPermission("irctransport.prefix.voice")) {
                prefix = "+";
            }
        }
        if (isAction) {
            return String.format("[%s%s%s] %s * %s%s %s",
                    ChatColor.AQUA, server, ChatColor.WHITE, ChatColor.DARK_PURPLE,
                    (prefix.equals(" ") ? "" : prefix), sender, ColorMap.fromIrc(message));
        } else {
            return String.format("[%s%s%s] <%s%s> %s",
                    ChatColor.AQUA, server, ChatColor.WHITE, prefix, sender,
                    ColorMap.fromIrc(message));
        }
    }

    @Override
    public void onMessage(String channel, String sender, String login,
            String hostname, String message) {
        // TODO: replace channel names with numbers
        // TODO(ajanata): Do something about other channels?
        getPlayer().sendMessage(formatMessage(sender, message, false));
    }

    @Override
    protected void onNickChange(String oldNick, String login, String hostname,
            String newNick) {
        if (oldNick.equals(getPlayer().getDisplayName())) {
            getPlayer().setDisplayName(newNick);
            getSettings().setIrcNick(newNick);
            saveSettings();
        }
        getPlayer().sendMessage(
                String.format("%s is now known as %s", oldNick, newNick));
    }

    @Override
    public void onPart(String channel, String sender, String login,
            String hostname) {
        getPlayer()
                .sendMessage(
                        ChatColor.YELLOW
                                + String.format("[%s] %s has parted.", channel,
                                        sender));
    }

    @Override
    public void onPrivateMessage(String sender, String login, String hostname,
            String message) {
        // TODO: check validity of recipient or check for error response
        getPlayer().sendMessage(String.format("%s: %s", sender, message));
    }

    @Override
    public void onQuit(String sourceNick, String sourceLogin,
            String sourceHostname, String reason) {
        getPlayer().sendMessage(
                ChatColor.YELLOW
                        + String.format("%s has quit: %s", sourceNick, reason));
    }

    /**
     * Handles response codes not handled by pircbot This methods handles irc
     * response codes, slices up the response, and then calls the appropriate
     * method
     * 
     * @param code
     *            the irc response code.
     * @param response
     *            The message that came with the response
     */
    @Override
    protected void onServerResponse(int code, String response) {
        Pattern responsePattern = Pattern.compile("(\\S*) (\\S*) :(.*)");
        Matcher responseMatcher = responsePattern.matcher(response);
        responseMatcher.find();
        switch (code) {
        case ERR_NOSUCHNICK: // TODO this needs a clearer error message
        case ERR_NOSUCHCHANNEL:
        case ERR_NICKNAMEINUSE:
        case ERR_INVITEONLYCHAN:
        case ERR_BADCHANNELKEY:
            onErrorMessage(responseMatcher.group(2), responseMatcher.group(3));
            break;

        }
    }

    @Override
    protected void onTopic(String channel, String topic, String setBy,
            long date, boolean changed) {
        if (changed) {
            getPlayer().sendMessage(
                    ChatColor.YELLOW
                            + String.format("[%s] Topic changed: %s", channel,
                                    topic));
        } else {
            getPlayer().sendMessage(
                    ChatColor.YELLOW
                            + String.format("[%s] Topic: %s", channel, topic));
        }
    }

    @Override
    protected void onUserList(String channel, User[] users) {
        String usersString = "";
        for (User user : users) {
            usersString += user.toString() + " ";
        }
        getPlayer().sendMessage(
                String.format("%s members: %s", channel, usersString));
    }

    protected void saveSettings() {
        plugin.getDatabase().save(getSettings());
    }

    /**
     * Used for outgoing actions.
     * @param action
     */
    public void sendAction(String action) {
        if (isConnected()) {
            // TODO: check ativeChannel for NULL, then just pick a random channel.
            sendAction(activeChannel, action);
            getPlayer().sendMessage(formatMessage(getPlayer().getDisplayName(), action, true));
        }
    }

    /**
     * Used for outgoing messages.
     * @param message
     */
    public void sendMessage(String message) {
        if (isConnected()) {
            // TODO: check ativeChannel for NULL, then just pick a random channel.
            sendMessage(activeChannel, message);
            getPlayer().sendMessage(formatMessage(getPlayer().getDisplayName(), message, false));
        }
    }

    public void setActiveChannel(String channel) {
        this.activeChannel = channel;
    }

    // sets the nick to attempt to use.
    //
    /**
     * Set name to attempt to use at login This function is not the same as
     * changeNick(String name) you probably don't want this function
     * 
     * @param name
     *            the name to attempt to use.
     */
    public void setNick(String name) {
        super.setName(name);
    }

    /**
     * @param settings
     *            the settings to set
     */
    public void setSettings(AgentSettings settings) {
        this.settings = settings;
    }

    protected void setTopic(String topic) {
        setTopic(activeChannel, topic);
    }

    public void shutdown() {
        shuttingDown = true;
        disconnect();
    }

    protected void topic() {
        sendRawLine(String.format("TOPIC %s", activeChannel));
    }
}
