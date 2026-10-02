package com.orlauf.rook

/** Точка маршрута: сколько км от начала маршрута и цитата из произведения. */
data class Stop(val name: String, val km: Double, val quote: String)

data class Route(val title: String, val emoji: String, val source: String, val stops: List<Stop>) {
    val lengthKm: Double get() = stops.last().km
}

/** Достигнутая точка: маршрут и сама точка. */
data class Reached(val route: Route, val stop: Stop)

/**
 * Где пользователь на цепочке маршрутов. Маршруты проходятся по очереди,
 * в зачёт идёт вся дистанция за всё время.
 */
data class JourneyState(
    val completed: List<Route>,
    /** null — все маршруты пройдены. */
    val current: Route?,
    val kmInRoute: Double,
    val lastStop: Stop?,
    val nextStop: Stop?,
    /** Сколько точек достигнуто всего, по всем маршрутам. Растёт монотонно. */
    val reachedCount: Int,
) {
    val kmToNext: Double get() = nextStop?.let { it.km - kmInRoute } ?: 0.0
    val routeProgress: Double get() = current?.let { (kmInRoute / it.lengthKm).coerceIn(0.0, 1.0) } ?: 1.0
}

/**
 * Расстояния — фанатские оценки по картам (у Толкина и Мартина точных цифр почти нет),
 * у Форреста — по фильму (~15 248 миль). Цитаты — в нашем переводе.
 */
object Journey {

    val ROUTES = listOf(
        Route(
            "Туда и обратно", "🏔", "«Хоббит»",
            listOf(
                Stop("Хоббитон", 0.0, "В норе под землёй жил-был хоббит."),
                Stop("Логово троллей", 560.0, "Вчера баранина, сегодня баранина, и, чтоб мне провалиться, завтра опять баранина!"),
                Stop("Ривенделл", 740.0, "Последний Домашний Приют к востоку от Моря."),
                Stop("Мглистые горы", 830.0, "Что у меня в кармашке?"),
                Stop("Дом Беорна", 950.0, "Он меняет шкуру: иногда он огромный чёрный медведь, иногда — сильный черноволосый человек."),
                Stop("Лихолесье", 1010.0, "Не сходите с тропы! Если сойдёте — один шанс на тысячу, что вы её снова отыщете."),
                Stop("Озёрный город", 1430.0, "Король под Горой, король резных камней, владыка серебряных ключей вернётся в свой дом!"),
                Stop("Одинокая гора", 1530.0, "Если бы больше из нас ценили еду, веселье и песни выше накопленного золота, мир был бы веселее."),
            ),
        ),
        Route(
            "В Мордор", "🌋", "«Властелин колец»",
            listOf(
                Stop("Шир", 0.0, "Опасное это дело, Фродо, — выходить за порог."),
                Stop("Бри", 217.0, "Не всё то золото, что блестит, не всяк заблудился, кто странствует."),
                Stop("Заверть", 380.0, "Гил-гэлад был эльфийский король, последний, кто правил свободной землёй."),
                Stop("Ривенделл", 737.0, "Я возьму Кольцо, хотя и не знаю дороги."),
                Stop("Мория", 1200.0, "Бегите, глупцы!"),
                Stop("Лориэн", 1480.0, "Даже самый маленький человек может изменить ход будущего."),
                Stop("Амон Хен", 2107.0, "Я дал обещание, мистер Фродо. Обещание."),
                Stop("Мёртвые топи", 2350.0, "Не смотри на огоньки!"),
                Stop("Чёрные врата", 2560.0, "Нельзя просто так взять и войти в Мордор."),
                Stop("Кирит Унгол", 2700.0, "В этом мире есть добро, мистер Фродо, и за него стоит бороться."),
                Stop("Роковая гора", 2863.0, "Я не могу нести его за вас, но я могу нести вас!"),
            ),
        ),
        Route(
            "Вдоль Стены", "🧊", "«Песнь льда и пламени»",
            listOf(
                Stop("Восточный Дозор", 0.0, "Зима близко."),
                Stop("Чёрный замок", 240.0, "Ночь собирается, и начинается мой дозор."),
                Stop("Сумеречная башня", 420.0, "Ты ничего не знаешь, Джон Сноу."),
                Stop("Западный край Стены", 480.0, "И теперь его дозор окончен."),
            ),
        ),
        Route(
            "Как Форрест Гамп", "🏃", "«Форрест Гамп»",
            listOf(
                Stop("Гринбоу, Алабама", 0.0, "Беги, Форрест, беги!"),
                Stop("Тихий океан", 3300.0, "Раз уж я забежал так далеко, подумал я, можно и обратно побежать."),
                Stop("Атлантический океан", 7600.0, "Мама всегда говорила: жизнь — как коробка конфет. Никогда не знаешь, какая начинка тебе попадётся."),
                Stop("Снова Тихий океан", 11900.0, "Дурак тот, кто поступает как дурак."),
                Stop("Снова Атлантика", 16200.0, "Я просто чувствовал, что хочу бежать."),
                Stop("Долина монументов", 24500.0, "Я очень устал. Пожалуй, пойду домой."),
            ),
        ),
    )

    /** Все точки, до которых дошёл человек с суммарной дистанцией totalKm, по порядку. */
    fun reached(totalKm: Double, routes: List<Route> = ROUTES): List<Reached> {
        val result = mutableListOf<Reached>()
        var offset = 0.0
        for (r in routes) {
            for (s in r.stops) if (offset + s.km <= totalKm) result += Reached(r, s) else return result
            offset += r.lengthKm
        }
        return result
    }

    fun state(totalKm: Double, routes: List<Route> = ROUTES): JourneyState {
        var offset = 0.0
        val completed = mutableListOf<Route>()
        for (r in routes) {
            if (totalKm < offset + r.lengthKm) {
                val inRoute = totalKm - offset
                return JourneyState(
                    completed = completed,
                    current = r,
                    kmInRoute = inRoute,
                    lastStop = r.stops.lastOrNull { it.km <= inRoute },
                    nextStop = r.stops.firstOrNull { it.km > inRoute },
                    reachedCount = reached(totalKm, routes).size,
                )
            }
            completed += r
            offset += r.lengthKm
        }
        return JourneyState(completed, null, 0.0, routes.lastOrNull()?.stops?.lastOrNull(), null, reached(totalKm, routes).size)
    }
}
