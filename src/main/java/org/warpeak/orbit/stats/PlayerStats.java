package org.warpeak.orbit.stats;

public class PlayerStats {

    public int kills = 0;
    public int deaths = 0;
    public long coins = 0;
    public long playTimeSeconds = 0; // накопленное время до текущей сессии
    public long sessionStart = System.currentTimeMillis(); // момент захода в этой сессии

    public long getTotalPlayTimeSeconds() {
        long sessionSeconds = (System.currentTimeMillis() - sessionStart) / 1000;
        return playTimeSeconds + sessionSeconds;
    }

    public String getFormattedPlayTime() {
        long total = getTotalPlayTimeSeconds();
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        return hours + "ч " + minutes + "м";
    }
}