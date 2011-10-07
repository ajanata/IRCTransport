package hef.IRCTransport;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;

/**
 * Connection Task This class us a Runnable class for connecting to the irc
 * server. It will reschedule a connection
 * @author hef
 */
public final class Connect implements Runnable {
    /**
     * Time between connection retries after a failed connection attempt.
     * 4oo is 20 seconds.
     */
    public static final int RETRY_RATE = 400;
    /**
     * Amount of time to wait to reconnect if a connection was already
     * established, but was then lost.
     * 100 is 5 seconds
     */
    public static final int INITIAL_RETRY_DELAY = 100;
    /** Log object. */
    private static final Logger LOG = Logger.getLogger("Minecraft");
    /**
     * Parent IRC agent. The class will perform a lot of actions on the agent.
     */
    private IrcAgent agent;

    /**
     * Create a Connection instance Pass this from IrcAgent so we have access.
     * @param parent
     *            pass in this from the calling IrcAgent
     */
    public Connect(final IrcAgent parent) {
        this.agent = parent;
    }

    /**
     * Run the connection task This task will schedule another connection task
     * if it fails to connect.
     */
    @Override
    public void run() {
        // If a task where scheduled, and the user disconnected, this would
        // continually keep the agent alive.
        if (!agent.isShuttingDown()) {
            try {
                // If we never set the server i.e. havn't connected yet
                if (agent.getServer() == null) {
                    IRCTransport plugin = agent.getPlugin();
                    if (plugin.getWebIrcPassword().equals("")) {
                        agent.connect(plugin.getIrcServer(), plugin.getIrcPort(),
                                plugin.getIrcPassword());
                    } else {
                        String webhost = agent.getPlayer().getAddress().getHostName();
                        String webip = agent.getPlayer().getAddress().getAddress().toString();
                        // The part before the / is a hostname if it is already known; we really
                        // want it so we look it up manually and get rid of it here.
                        webip = webip.split("/")[1];
                        agent.connect(plugin.getIrcServer(), plugin.getIrcPort(),
                                plugin.getIrcPassword(), plugin.getWebIrcPassword(), webhost,
                                webip);
                    }
                } else {
                    // reconnect should recycle settings the user already has
                    agent.reconnect();
                }
            } catch (NickAlreadyInUseException e) {
                // This should not happen, the agent is set to auto retry new
                // names
                LOG.log(Level.SEVERE, e.getMessage(), e);
            } catch (IOException e) {
                if (e.getMessage().equalsIgnoreCase("Connection refused")) {
                    agent.getPlayer().sendMessage(
                            ChatColor.YELLOW
                                    + "Failed to connect to Chat Server.");
                    // 400 seems to be 20 seconds
                    agent.getPlugin()
                            .getServer()
                            .getScheduler()
                            .scheduleAsyncDelayedTask(agent.getPlugin(), this,
                                    RETRY_RATE);
                } else if
                    (e.getMessage().equalsIgnoreCase("Connection reset")) {
                    agent.getPlayer()
                            .sendMessage(
                                    ChatColor.YELLOW
                                            + "Connection reset while "
                                            + "connecting to Chat Server");
                    agent.getPlugin()
                            .getServer()
                            .getScheduler()
                            .scheduleAsyncDelayedTask(agent.getPlugin(), this,
                                    INITIAL_RETRY_DELAY);
                } else {
                    LOG.log(Level.SEVERE, e.getMessage(), e);
                }
            } catch (IrcException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
            }

            // The player may have not gotten then name they wanted.
            agent.getPlayer().setDisplayName(agent.getNick());
            agent.getSettings().setIrcNick(agent.getNick());
            if (!agent.getPlugin().getAutoJoin().equals("")) {
                // if no channel key is set
                if (agent.getPlugin().getAutoJoinKey().equals("")) {
                    agent.joinChannel(agent.getPlugin().getAutoJoin());
                } else {
                    agent.joinChannel(agent.getPlugin().getAutoJoin(), agent
                            .getPlugin().getAutoJoinKey());
                }
                agent.setActiveChannel(agent.getPlugin().getAutoJoin());
            }
        }
    }

}
