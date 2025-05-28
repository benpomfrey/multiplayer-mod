package com.multiplayermod;

import java.util.HashSet;
import java.util.Set;

public class InviteManager {
    private static final Set<String> invitedPlayers = new HashSet<>();

    public static void invite(String name) {
        invitedPlayers.add(name);
    }

    public static boolean isInvited(String name) {
        return invitedPlayers.contains(name);
    }
}
