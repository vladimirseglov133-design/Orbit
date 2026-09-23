package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.abilities.DebugLog;
import org.warpeak.orbit.abilities.PlayerAbilityData;
import org.warpeak.orbit.duel.DuelManager;

/**
 * MONITOR = выполняется САМЫМ ПОСЛЕДНИМ, после абсолютно всех остальных плагинов и листенеров.
 * Показывает ИТОГОВОЕ состояние события — было ли оно отменено и с какой
 * finalDamage — плюс состояние ЖЕРТВЫ в момент хита для атрибуции причины.
 *
 * Это "источник правды" для верификации фикса багов неуязвимости:
 * на хитах, прилетевших в окне 1–3 секунд после Teleport Swap, строка
 * DAMAGE должна показывать cancelled=false и finalDamage > 0
 * (msSinceSwap в диапазоне [1000..3000]).
 *
 * Полный формат и алгоритм атрибуции (DODGE-T3 / UI-RNG / TELEPORT-INVULN /
 * DAMAGE) описаны в javadoc класса {@link DebugLog}.
 */
public class DamageDebugListener implements Listener {

    private final DuelManager duelManager;

    public DamageDebugListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!duelManager.isInDuel(victim) || !duelManager.isInDuel(attacker)) return;

        PlayerAbilityData data = Orbit.get().getAbilityManager().getData(victim);
        long now = System.currentTimeMillis();

        String tier3 = (data != null && data.tier3 != null) ? data.tier3.name() : "none";
        String tier4 = (data != null && data.tier4 != null) ? data.tier4.name() : "none";
        boolean instinctActive = data != null && data.ultraInstinctActive;
        int noDamageTicks = DebugLog.getNoDamageTicks(Orbit.get(), victim);

        long msSinceSwap = (data != null && data.lastSwapAt > 0) ? now - data.lastSwapAt : -1;
        long msSinceDodgeTl = (data != null && data.lastDodgeTeleportAt > 0) ? now - data.lastDodgeTeleportAt : -1;

        DebugLog.log(Orbit.get(), "DAMAGE",
                "attacker=" + attacker.getName() + " victim=" + victim.getName()
                        + " | cancelled=" + event.isCancelled()
                        + " | finalDamage=" + event.getFinalDamage()
                        + " | victimTier3=" + tier3
                        + " | victimTier4=" + tier4
                        + " | victimInstinctActive=" + instinctActive
                        + " | victimNoDamageTicks=" + noDamageTicks
                        + " | msSinceSwap=" + msSinceSwap
                        + " | msSinceDodgeTeleport=" + msSinceDodgeTl);
    }
}
