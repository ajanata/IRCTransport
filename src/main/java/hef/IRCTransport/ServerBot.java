package hef.IRCTransport;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

class ServerBot extends PircBot {
    private static final Logger log = Logger.getLogger("Minecraft");
    IRCTransport plugin;

    ServerBot(IRCTransport instance, String nick) {
        plugin = instance;

        setLogin("minecraft");
        setName(nick);
        setAutoNickChange(true);
        try {
            connect(plugin.getIrcServer(), plugin.getIrcPort(), plugin.getIrcPassword());
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("[%s] Server connection unable to connect: %s %s",
                    plugin.getDescription().getName(), e.getClass(), e.getMessage()));
            return;
        }

        if (plugin.getAutoJoinKey().equals("")) {
            joinChannel(plugin.getAutoJoin());
        } else {
            joinChannel(plugin.getAutoJoin(), plugin.getAutoJoinKey());
        }
    }

    public void shutdown() {
        if (isConnected()) {
            disconnect();
        }
    }

    @Override
    public void onMessage(String channel, String sender, String login,
            String hostname, String message) {
        // We only want commands from ops.
        User[] users = this.getUsers(channel);
        User senderUser = null;
        for (User user : users) {
            if (user.getNick().equalsIgnoreCase(sender)) {
                senderUser = user;
                break;
            }
        }
        if (senderUser == null || !senderUser.isOp()) {
            return;
        }

        String cmd = message.split(" ")[0].toLowerCase();
        if (cmd.equals("*mem")) {
            sendMessage(channel, getMemString());
        } else if (cmd.equals("*gc")) {
            String before = getMemString();
            System.runFinalization();
            System.gc();
            String after = getMemString();
            sendMessage(channel, String.format("Before: %s After: %s", before, after));
        }
    }

    private String getMemString() {
        Runtime runtime = Runtime.getRuntime();
        return String.format("Used/Free/total/max: %s/%s/%s/%s",
                (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024,
                runtime.freeMemory() / 1024 / 1024, runtime.totalMemory() / 1024 / 1024,
                runtime.maxMemory() / 1024 / 1024);
    }
}
