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
 *   API-CHECK          — ОДНА строка при первом чтении noDamageTicks: есть ли
 *                        в рантайме геттер Entity#getNoDamageTicks.
 *   SWAP-UNCANCEL      — LATEST-enforcer (SwapDamageEnforcerListener) снял
 *                        ВНЕШНЕЕ отменение урона в окне 0–3с после Teleport
 *                        Swap: урон принудительно включён. Строка следует за
 *                        DAMAGE-строкой с cancelled=true, если чужой плагин
 *                        отменил событие (доказательство внешнего отменения).
 *   SWAP-AUDIT         — состояние каждого участника swap раз в секунду в
 *                        3-секундном окне форсинга: health, noDamageTicks /
 *                        invulnerable ДО сброса, уровень RESISTANCE. Ровный
 *                        health + нули флагов + нет DAMAGE-строк = "невидимая"
 *                        предсобытийная неуязвимость (глубже noDamageTicks).
 *   UI-ACTIVATE        — активация Ультра Инстинкта (старт окна 15с).
 *   UI-EXPIRE          — окончание окна Ультра Инстинкта (через 15с).
 *   BUILD              — ОДНА строка при старте: build=<commit>, версия API,
 *                        Java. Сверять с HEAD ветки: если build= старее —
 *                        на сервере крутится СТАРЫЙ jar (нужна пересборка +
 *                        рестарт). build= дублируется в TELEPORT-SWAP.
 *   INVULN-API         — ОДНА строка: есть ли в рантайме API 1.21.2+
 *                        invulnerable-causes (полный список методов).
 *   INVULN-CAUSE(S)    — найдена и снята cause-based неуязвимость (Paper
 *                        1.21.2+): кто, когда (reason/phase) и какая причина.
 *                        Строки появляются ТОЛЬКО при реальной находке.
 *   В SWAP-AUDIT поле distOpponent: дистанция до оппонента. Если она
 *                        велика (10+) — "бесмертие" = восприятие (дистанция),
 *                        а не блокировка урона.
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
 *      После фикса такое окно снимается 60-тиковым форсингом (каждый тик
 *      окна 3с) и DAMAGE-строки в окне 1–3 секунд после swap должны
 *      показывать cancelled=false, finalDamage>0.
 *   5. В окне 0–3с после swap DAMAGE с cancelled=true, а после неё строка
 *      SWAP-UNCANCEL → урон ВНЕШНЕ отменялся чужим плагином/серверным
 *      кодом (не Paper-окно!): enforcer включил его принудительно.
 *   6. В окне swap health из SWAP-AUDIT ровный (не падает), все флаги нули,
 *      DAMAGE-строк нет → неуязвимость на уровне ВЫШЕ noDamageTicks.
 *      В этом случае нужен точный билд Paper и список плагинов.
 */
public final class DebugLog {

    /** false — выключить весь отладочный лог одной строкой, не удаляя код. */
    public static volatile boolean ENABLED = true;

    public static final String PREFIX = "[OrbitDebug]";

    /**
     * Отпечаток СБОРКИ (commit, из которого собран jar). Обновлять при каждом
     * релизе. Строка [BUILD] при старте сервера и build= в TELEPORT-SWAP
     * позволяют ПО ЛОГУ определить, какой именно код реально крутится на
     * сервере (актуальный jar или старый — без рестарта/пересборки поведение
     * не меняется).
     */
    public static final String BUILD = "6adfe0c";

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
     *
     * ПЕРВЫЙ вызов пишет строку API-CHECK (getter=available/unavailable) —
     * если в логах тест-сессии стоит "unavailable", это значит, что значения
     * noDamageTicks в TELEPORT-INVULN/DAMAGE строках видны как -1 и окно
     * Paper нельзя подтвердить по значению — ориентироваться надо только
     * на факты "урон не прошёл" (отсутствие DAMAGE-строк).
     */
    public static int getNoDamageTicks(Plugin plugin, Player p) {
        if (p == null || !p.isOnline()) return -1;
        Method m = noDamageTicksMethod(plugin);
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

    private static Method noDamageTicksMethod(Plugin plugin) {
        if (!probed) {
            try {
                cachedGetNoDamageTicksMethod = Entity.class.getMethod("getNoDamageTicks");
            } catch (NoSuchMethodException e) {
                cachedGetNoDamageTicksMethod = null;
            }
            probed = true;
            log(plugin, "API-CHECK",
                    "Entity#getNoDamageTicks getter="
                            + (cachedGetNoDamageTicksMethod != null ? "available" : "UNAVAILABLE (noDamageTicks в логах будет -1)"));
        }
        return cachedGetNoDamageTicksMethod;
    }

    // ==================== Paper 1.21.2+: cause-based invulnerability ====================
    // На новых сборках окно неуязвимости (в т.ч. после teleport()) может
    // храниться НЕ в noDamageTicks/invulnerable, а в таблице "причин"
    // (invulnerable causes). setNoDamageTicks(0)/setInvulnerable(false) такое
    // окно не снимают. Пробираем API через рефлексию (нет compile-зависимости
    // от версии Paper): на старых сборках все методы отсутствуют и это
    // безобидный no-op; строка INVULN-API говорит, есть ли API в рантайме.
    private static volatile boolean invulnApiProbed = false;
    private static volatile Method cachedInvulnCauseGetter;
    private static volatile Method cachedInvulnCausesGetter;
    private static volatile Method cachedInvulnCauseSetter;
    private static volatile Method cachedInvulnCausesSetter;

    private static void probeInvulnCauseApi(Plugin plugin) {
        if (invulnApiProbed) return;
        invulnApiProbed = true;
        try {
            for (Method m : Entity.class.getMethods()) {
                String n = m.getName();
                if (m.getParameterCount() == 0 && n.equals("getInvulnerableCause")) {
                    cachedInvulnCauseGetter = m;
                } else if (m.getParameterCount() == 0 && n.equals("getInvulnerableCauses")) {
                    cachedInvulnCausesGetter = m;
                } else if (m.getParameterCount() == 1 && n.equals("setInvulnerableCause")) {
                    cachedInvulnCauseSetter = m;
                } else if (m.getParameterCount() == 1 && n.equals("setInvulnerableCauses")) {
                    cachedInvulnCausesSetter = m;
                }
            }
        } catch (Throwable ignored) { }
        boolean none = cachedInvulnCauseGetter == null && cachedInvulnCausesGetter == null
                && cachedInvulnCauseSetter == null && cachedInvulnCausesSetter == null;
        log(plugin, "INVULN-API",
                "getInvulnerableCause=" + (cachedInvulnCauseGetter != null ? "available" : "absent")
                        + " getInvulnerableCauses=" + (cachedInvulnCausesGetter != null ? "available" : "absent")
                        + " setInvulnerableCause=" + (cachedInvulnCauseSetter != null ? "available" : "absent")
                        + " setInvulnerableCauses=" + (cachedInvulnCausesSetter != null ? "available" : "absent")
                        + (none ? " -> API 1.21.2+ в рантайме НЕТ (cause-based неуязвимость исключена)"
                                : " -> API 1.21.2+ найден, cause-окна будут сниматься"));
    }

    /**
     * Пытается снять cause-based неуязвимость (Paper 1.21.2+).
     * Строки INVULN-CAUSE/INVULN-CAUSES пишутся ТОЛЬКО если причина реально
     * найдена (иначе шум). На сборках без API — silent no-op.
     */
    public static void clearInvulnCauses(Plugin plugin, Player p, String reason, String phase) {
        probeInvulnCauseApi(plugin);
        if (cachedInvulnCauseGetter == null && cachedInvulnCausesGetter == null) return;
        try {
            if (cachedInvulnCauseGetter != null) {
                Object cause = cachedInvulnCauseGetter.invoke(p);
                if (cause != null) {
                    log(plugin, "INVULN-CAUSE",
                            "victim=" + p.getName() + " reason=" + reason + " phase=" + phase
                                    + " cause=" + cause + " -> clearing");
                    if (cachedInvulnCauseSetter != null) {
                        cachedInvulnCauseSetter.invoke(p, (Object) null);
                    }
                }
            }
            if (cachedInvulnCausesGetter != null) {
                Object causes = cachedInvulnCausesGetter.invoke(p);
                if (causes != null) {
                    String s = String.valueOf(causes);
                    if (!s.isEmpty() && !s.equals("[]")) {
                        log(plugin, "INVULN-CAUSES",
                                "victim=" + p.getName() + " reason=" + reason + " phase=" + phase
                                        + " causes=" + s + " -> clearing");
                        if (cachedInvulnCausesSetter != null) {
                            cachedInvulnCausesSetter.invoke(p, java.util.Collections.emptyList());
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
    }
}
