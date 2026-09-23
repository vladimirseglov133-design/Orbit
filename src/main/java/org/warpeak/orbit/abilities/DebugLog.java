package org.warpeak.orbit.abilities;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Централизованный отладочный лог для атрибуции "неожиданной отмены урона".
 *
 * Раньше три ПОЛНОСТЬЮ РАЗНЫХ корневых причины давали визуально идентичный
 * симптом ("урон отменился"):
 *
 *   1. Додж тир3 (F) — баг тайминга кулдауна: спам F бесконечно продлевал
 *      окно вооружения → все удары в окне отменялись (баг #1).
 *   2. Ультра Инстинкт (тир4) — легальный 50% RNG-уворот на каждый хит.
 *   3. "Обычное" окно неуязвимости Paper после Entity#teleport():
 *      noDamageTicks выставляется неявно, и входящий урон глушится вообще
 *      без нашего кода (баг #2 после Teleport Swap + любой телепорт в бою).
 *
 * Все строки имеют единый префикс [OrbitDebug][TAG], где TAG — причина:
 *
 *   DODGE-T3-ACTIVATE  — игрок нажал F, окно вооружения открыто, кулдаун
 *                        выставлен В МОМЕНТ АКТИВАЦИИ (см. баг #1).
 *   DODGE-T3-REJECT    — нажатие F отбито как "on cooldown". Множество таких
 *                        строк подряд = спам, который больше НЕ даёт ничего.
 *   DODGE-T3-EXPIRE    — окно вооружения истекло без попадания.
 *   DODGE-T3-DODGE     — хит потреблён доджем тир3. НЕСКОЛЬКО таких строк
 *                        одной жертве внутри одного 5-секундного кулдауна
 *                        (или несколько ACTIVATE там же) = сигнатура
 *                        эксплуатанта. После фикса это невозможно.
 *   UI-RNG             — КАЖДЫЙ ролл Ультра Инстинкта: victim, значение
 *                        ролла, результат (dodged=true/false/N/A) и флаг
 *                        ultraInstinctActive В МОМЕНТ ХИТА.
 *   TELEPORT-INVULN    — прямое доказательство "обычного окна Paper":
 *                        ненулевой noDamageTicks, найденный после
 *                        teleport() (сразу или на следующем тике) и снятый
 *                        стандартным двойным reset.
 *   TELEPORT-SWAP      — обмен местами (тир3): оба игрока телепортированы,
 *                        окно неуязвимости снято двойным reset.
 *   DAMAGE             — MONITOR-строка: ИТОГОВОЕ состояние события
 *                        (cancelled/finalDamage) + состояние жертвы в момент
 *                        хита, включая msSinceSwap / msSinceDodgeTeleport.
 *
 * Алгоритм атрибуции для тест-сессии:
 *   1. cancelled=true + строка DODGE-T3-DODGE той же жертвы/атакующего тем
 *      же тиком → додж тир3. Легитимен, если ему предшествует ровно один
 *      DODGE-T3-ACTIVATE и REJECT-строк в кулдауне нет; если доджей больше
 *      одного за 5 секунд → эксплуатант (баг #1).
 *   2. cancelled=true + строка UI-RNG тем же тиком с active=true dodged=true
 *      → легальный 50% ролл Ультра Инстинкта.
 *   3. cancelled=true, но victimInstinctActive=false в DAMAGE-строке и
 *      DODGE-T3-DODGE строк нет → ОТДЕЛЬНЫЙ баг (не Ультра Инстинкт!
 *      атрибировать его к UI без строки с active=true запрещено).
 *   4. Хит "исчез" (DAMAGE-строки нет) после телепорта + строка
 *      TELEPORT-INVULN рядом → неявное окно неуязвимости Paper (баг #2).
 *      После фикса такое окно снимается двойным reset и DAMAGE-строки
 *      в окне 1–3 секунд после swap должны показывать cancelled=false,
 *      finalDamage>0.
 */
public final class DebugLog {

    /** false — выключить весь отладочный лог одной строкой, не удаляя код. */
    public static volatile boolean ENABLED = true;

    public static final String PREFIX = "[OrbitDebug]";

    private DebugLog() {
    }

    public static void log(Plugin plugin, String tag, String message) {
        if (!ENABLED) return;
        plugin.getLogger().info(PREFIX + "[" + tag + "] " + message);
    }

    /**
     * Читает Entity#noDamageTicks через рефлексию.
     *
     * Сеттер вызывается напрямую (он есть в compile-класспасе проекта —
     * spigot-api 1.21.4-R0.1-SNAPSHOT), а геттер нужен только для
     * отладочной атрибуции, поэтому жёсткую зависимость от него не создаём:
     * на Paper метод есть и лог покажет фактическое значение окна,
     * на сборках, где его нет, — вернёт -1 ("недоступно") и не сломает билд.
     */
    public static int getNoDamageTicks(Player p) {
        if (p == null || !p.isOnline()) return -1;
        Method m = noDamageTicksMethod();
        if (m == null) return -1;
        try {
            Object value = m.invoke(p);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static volatile boolean probed = false;
    private static volatile Method cachedGetNoDamageTicksMethod;

    private static Method noDamageTicksMethod() {
        if (!probed) {
            try {
                cachedGetNoDamageTicksMethod = Entity.class.getMethod("getNoDamageTicks");
            } catch (NoSuchMethodException e) {
                cachedGetNoDamageTicksMethod = null;
            }
            probed = true;
        }
        return cachedGetNoDamageTicksMethod;
    }
}
