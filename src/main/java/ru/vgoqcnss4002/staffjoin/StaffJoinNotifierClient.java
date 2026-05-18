private static String getPlayerName(PlayerListEntry listEntry) {
    GameProfile profile = listEntry.getProfile();
    if (profile != null && profile.name() != null && !profile.name().isBlank()) {
        return profile.name();
    }
    return "unknown";
}
