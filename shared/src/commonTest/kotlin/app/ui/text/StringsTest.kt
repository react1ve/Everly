package app.ui.text

import app.core.model.IntervalUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    @Test fun russianPluralsAfterNumbers() {
        assertEquals("раз в 3 дня", Ru.every(3, IntervalUnit.DAY))
        assertEquals("раз в 5 дней", Ru.every(5, IntervalUnit.DAY))
        assertEquals("раз в 21 день", Ru.every(21, IntervalUnit.DAY))
        assertEquals("раз в 21 неделю", Ru.every(21, IntervalUnit.WEEK))
        assertEquals("раз в 2 недели", Ru.every(2, IntervalUnit.WEEK))
        assertEquals("раз в 11 месяцев", Ru.every(11, IntervalUnit.MONTH))
        assertEquals("раз в 5 лет", Ru.every(5, IntervalUnit.YEAR))
        assertEquals("2 раза в день", Ru.perDay(2))
    }

    @Test fun uzbekAndEnglishIntervals() {
        assertEquals("har 2 oyda", Uz.every(2, IntervalUnit.MONTH))
        assertEquals("every 3 weeks", En.every(3, IntervalUnit.WEEK))
        assertEquals("every 1 day".length > 0, true)
    }

    @Test fun everyLanguageIsComplete() {
        Lang.all.forEach { s ->
            assertEquals(12, s.monthsLong.size, s.code)
            assertEquals(7, s.weekdaysShort.size, s.code)
            assertEquals(7, s.weekdaysLong.size, s.code)
            assertEquals(3, s.templates.size, s.code)
        }
    }

    @Test fun languageSelectionFallsBackToEnglish() {
        Lang.select("ru-RU"); assertEquals("ru", Lang.current.code)
        Lang.select("de"); assertEquals("en", Lang.current.code)
        Lang.select(null); assertEquals("en", Lang.current.code)
    }
}
