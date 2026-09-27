package org.warpeak.orbit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.warpeak.orbit.Orbit;
import org.warpeak.orbit.abilities.DebugLog;
import org.warpeak.orbit.abilities.PlayerAbilityData;
import org.warpeak.orbit.duel.Duel;
import org.warpeak.orbit.duel.DuelManager;

/**
 * Последний рубеж защиты от "невидимой" неуязвимости после Teleport Swap.
 *
 * Отчёт пользователя: даже после 60-тикового форсинга noDamageTicks/
 * invulnerable swap всё равно выглядел как ~2 секунды неуязвимости. Если
 * форсинг отработал (SWAP-AUDIT показывает чистое состояние), а урон всё
 * равно не проходит, источник — ВНЕШНЕЕ отменение события: чужой плагин
 * (например, "teleport invincibility" из твик-пакетов) или серверный код,
 * которые отменяют EntityDamageByEntityEvent в том же тике после телепорта.
 *
 * LATEST — самая поздняя приоритетная точка Paper (после MONITOR): если на
 * её выходе событие всё ещё отменено, но это swap-окно (0 < msSinceSwap
 * <= 3000) и наш Orbit в этом же тике НЕ уклонялся — принудительно
 * включаем урон и пишем SWAP-UNCANCEL. Это единственная строка, которая
 * доказывает "внешнее отменение" по факту (а не по подозрению).
 *
 * Защита от ложных срабатываний:
 *   - только PvP внутри одной дуэли (жертва и атакующий — оппоненты);
 *   - НЕ трогаем наши собственные Orbit-уклонения в этом же тике
 *     (lastDodgeTeleportAt: тир3/УИ-телепорт; phoenixUsedAt: Феникс).
 */
public class SwapDamageEnforcerListener implements Listener {

    /** Окно после Teleport Swap, в котором принудительно включаем урон. */
    private static final long SWAP_WINDOW_MS = 3000;
    /** Порог "тот же тик" для исключения наших собственных уклонений. */
    private static final long SAME_TICK_MS = 100;

    private final DuelManager duelManager;

    public SwapDamageEnforcerListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @EventHandler(priority = EventPriority.LATEST)
    public void onDamageLatest(EntityDamageByEntityEvent event) {
        if (!event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!duelManager.isInDuel(victim) || !duelManager.isInDuel(attacker)) return;

        Duel duel = duelManager.getDuel(victim);
        if (duel == null) return;
        if (!duel.getOpponent(victim).equals(attacker)) return;

        PlayerAbilityData data = Orbit.get().getAbilityManager().getData(victim);
        if (data == null) return;

        long now = System.currentTimeMillis();
        if (data.lastSwapAt <= 0) return;
        long msSinceSwap = now - data.lastSwapAt;
        if (msSinceSwap < 0 || msSinceSwap > SWAP_WINDOW_MS) return;

        // НЕ переопределяем наши собственные Orbit-уклонения в этом же тике
        if (now - data.lastDodgeTeleportAt < SAME_TICK_MS) return;
        if (data.phoenixUsed && now - data.phoenixUsedAt < SAME_TICK_MS) return;

        event.setCancelled(false);
        DebugLog.log(Orbit.get(), "SWAP-UNCANCEL",
                "victim=" + victim.getName() + " attacker=" + attacker.getName()
                        + " msSinceSwap=" + msSinceSwap
                        + " -> damage re-enabled (overriding external cancel)");
    }
}
