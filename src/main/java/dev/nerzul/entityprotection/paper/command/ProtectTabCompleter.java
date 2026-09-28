package dev.nerzul.entityprotection.paper.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProtectTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("trust", "untrust", "trustall", "untrustall", "info", "remove");
    private static final Set<String> NEEDS_PLAYER_ARG = Set.of("trust", "untrust", "trustall", "untrustall");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startingWith(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && NEEDS_PLAYER_ARG.contains(args[0].toLowerCase(Locale.ROOT))) {
            return startingWith(onlinePlayerNames(), args[1]);
        }
        return List.of();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> startingWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
