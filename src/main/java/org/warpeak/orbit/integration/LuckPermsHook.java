package org.warpeak.orbit.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.entity.Player;

public class LuckPermsHook {

    private final LuckPerms luckPerms;

    public static final int CASE_PREFIX_PRIORITY = 100;

    public LuckPermsHook() {
        this.luckPerms = LuckPermsProvider.get();
    }

    public void setPrefix(Player player, String prefix) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            applyPrefix(user, prefix);
            return;
        }
        luckPerms.getUserManager().loadUser(player.getUniqueId())
                .thenAccept(loaded -> applyPrefix(loaded, prefix));
    }

    public void clearPrefix(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            removeCasePrefix(user);
            return;
        }
        luckPerms.getUserManager().loadUser(player.getUniqueId())
                .thenAccept(this::removeCasePrefix);
    }

    private void applyPrefix(User user, String prefix) {
        if (user == null) return;
        removeCasePrefix(user);

        PrefixNode node = PrefixNode.builder(prefix, CASE_PREFIX_PRIORITY).build();
        user.data().add(node);

        luckPerms.getUserManager().saveUser(user);
    }

    private void removeCasePrefix(User user) {
        if (user == null) return;
        user.data().clear(node -> node.getType() == NodeType.PREFIX
                && ((PrefixNode) node).getPriority() == CASE_PREFIX_PRIORITY);
        luckPerms.getUserManager().saveUser(user);
    }
}