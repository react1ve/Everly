package app.ui.text

import app.core.model.IntervalUnit

object Ru : Strings() {
    override val code = "ru"

    private val monthsShort = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")
    private val monthsGenitive = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа",
        "сентября", "октября", "ноября", "декабря")
    override val monthsLong = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август",
        "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    override val weekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    override val weekdaysLong = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    override fun shortDate(day: Int, month: Int) = "$day ${monthsShort[month]}"
    override fun longDate(weekday: Int, day: Int, month: Int, year: Int) = "${weekdaysLong[weekday]}, $day ${monthsGenitive[month]} $year"
    override fun weekdayDate(weekday: Int, day: Int, month: Int) = "${weekdaysShort[weekday]}, ${shortDate(day, month)}"
    override fun at(date: String, time: String) = "$date в $time"

    override fun unitName(u: IntervalUnit) = when (u) {
        IntervalUnit.DAY -> "День"; IntervalUnit.WEEK -> "Неделя"; IntervalUnit.MONTH -> "Месяц"; IntervalUnit.YEAR -> "Год"
    }
    override fun unitCount(u: IntervalUnit, n: Int) = when (u) {
        IntervalUnit.DAY -> ruPlural(n, "день", "дня", "дней")
        IntervalUnit.WEEK -> ruPlural(n, "неделя", "недели", "недель")
        IntervalUnit.MONTH -> ruPlural(n, "месяц", "месяца", "месяцев")
        IntervalUnit.YEAR -> ruPlural(n, "год", "года", "лет")
    }
    /** Accusative after "раз в N": "раз в 21 неделю", "раз в 3 недели". */
    private fun unitAcc(u: IntervalUnit, n: Int) = when (u) {
        IntervalUnit.WEEK -> ruPlural(n, "неделю", "недели", "недель")
        else -> unitCount(u, n)
    }
    override val daily = "каждый день"
    override val weekly = "раз в неделю"
    override val monthly = "раз в месяц"
    override val yearly = "раз в год"
    override fun every(n: Int, u: IntervalUnit) = "раз в $n ${unitAcc(u, n)}"
    override fun perDay(n: Int) = "$n " + ruPlural(n, "раз", "раза", "раз") + " в день"
    override fun timesThatDay(n: Int) = ruPlural(n, "раз в день", "раза в день", "раз в день")
    override fun overdueDays(n: Int) = "просрочено на $n ${unitCount(IntervalUnit.DAY, n)}"
    override val dueToday = "сегодня"
    override val dueTomorrow = "завтра"
    override fun dueOn(date: String) = "к $date"
    override fun daysBefore(n: Int) = if (n == 0) "В тот же день" else "За $n ${unitCount(IntervalUnit.DAY, n)}"
    override val off = "Выкл."

    override val stDone = "сделано"
    override fun stPartial(n: Int, m: Int) = "сделано $n из $m"
    override val stCovered = "не нужно, покрыто прошлым разом"
    override val stDue = "пора"
    override val stOverdue = "просрочено"
    override val stIdle = "ничего не нужно"
    override val cToday = "сегодня"
    override val cHasNote = "есть заметка"
    override val cReminder = "есть напоминание"

    override val close = "Закрыть"
    override val save = "Сохранить"
    override val ok = "ОК"
    override val cancel = "Отмена"
    override val delete = "Удалить"
    override val edit = "Изменить"
    override val decrease = "Меньше"
    override val increase = "Больше"
    override val none = "Нет"
    override val colour = "Цвет"
    override val folder = "Папка"
    override val reminder = "Напоминание"
    override val nextReminder = "Следующее напоминание"

    override val opensDetails = "Открыть подробности"
    override val editFolder = "Изменить папку"
    override fun habitsCount(n: Int) = "$n " + ruPlural(n, "дело", "дела", "дел")
    override val collapsed = "свёрнута"
    override val expanded = "развёрнута"
    override val scrollToToday = "Перейти к сегодня"
    override val today = "Сегодня"
    override val newHabit = "Новое дело"
    override val organiseHabits = "Упорядочить"
    override val settings = "Настройки"
    override val emptyTitle = "Пока ничего не повторяется"
    override val emptyBody = "Добавьте то, что возвращается снова: таблетку, фильтр, продление страховки. " +
        "Отмечайте сделанное — следующая дата посчитается сама."
    override val addFirst = "Добавить первое"
    override val orStartFrom = "Или начните с одного из этих"
    override val templates = listOf(
        "Витамины каждый день" to "💊 Витамины",
        "Свежее бельё · раз в 2 недели" to "🛏️ Сменить постельное бельё",
        "Автостраховка · раз в год" to "🚗 Продлить автостраховку",
    )

    override fun notifComingUp(days: Int, date: String) = "Через $days ${unitCount(IntervalUnit.DAY, days)}, $date."
    override val notifDueTomorrow = "Завтра."
    override val notifDueToday = "Сегодня. Отметьте день в сетке, когда сделаете."
    override fun notifStillWaiting(date: String) = "Ждёт с $date. Отметьте, когда сделаете."
    override val notifDone = "Готово"
    override val channelName = "Напоминания"
    override val channelDescription = "Когда что-то пора сделать"

    override val yearView = "Год"
    override val previousYear = "Предыдущий год"
    override val nextYear = "Следующий год"
    override fun daysDoneIn(n: Int, year: Int) = "В $year: $n " + ruPlural(n, "день", "дня", "дней")
    override val repeats = "Повтор"
    override val nextDue = "Следующий раз"
    override val entries = "Записи"
    override val addEntry = "Добавить запись"
    override fun nothingLogged(year: Int) = "В $year году записей пока нет."
    override val noteOnly = "только заметка"

    override val whatHappened = "Как всё прошло?"
    override val deleteEntry = "Удалить запись"
    override val discardChanges = "Отменить изменения?"
    override val discard = "Отменить"
    override val keepEditing = "Продолжить"
    override val checked = "отмечено"
    override val notChecked = "не отмечено"
    override val doneLabel = "Сделано"

    override val newFolder = "Новая папка"
    override val editFolderTitle = "Изменить папку"
    override val folderPlaceholder = "Название, например «Питомцы»"
    override val deleteFolder = "Удалить папку"
    override fun deleteFolderTitle(name: String) = "Удалить «$name»?"
    override val folderEmpty = "Папка пустая."
    override fun folderHabitsStay(n: Int) = "$n ${ruPlural(n, "дело останется", "дела останутся", "дел останутся")} без папки. История не потеряется."
    override val noFolder = "Без папки"
    override val newFolderEllipsis = "Новая папка…"
    override val create = "Создать"
    override val folderExample = "например «Питомцы»"

    override val newHabitTitle = "Новое дело"
    override val editHabitTitle = "Изменить дело"
    override val namePlaceholder = "Что повторяется? Например 🦷 Стоматолог"
    override val nameError = "Добавьте название"
    override val repeatsEvery = "Повторять раз в"
    override val deleteHabit = "Удалить дело"
    override val deleteHabitTitle = "Удалить это дело?"
    override val deleteHabitBody = "Вся его история тоже удалится. Это нельзя отменить."
    override val keepIt = "Оставить"
    override val remindMe = "Напоминать"
    override val time = "Время"
    override val whenLabel = "Когда"
    override fun reminderTimeA11y(time: String) = "Время напоминания $time"
    override val notificationsOffWarning = "Уведомления для приложения выключены, напоминания не появятся. Включите их в настройках телефона."
    override val reminderTimeTitle = "Время напоминания"

    override val organise = "Порядок"
    override val moveFolderUp = "Папку выше"
    override val moveFolderDown = "Папку ниже"
    override val empty = "Пусто"
    override val moveUp = "Выше"
    override val moveDown = "Ниже"
    override val moveToFolder = "В папку"
    override val dragToReorder = "Перетащите, чтобы изменить порядок"
    override val folderOptions = "Действия с папкой"

    override val appearance = "Оформление"
    override val themeAuto = "Авто"
    override val themeLight = "Светлая"
    override val themeDark = "Тёмная"
    override val language = "Язык"
    override val languageAuto = "Как в телефоне"
    override val reminders = "Напоминания"
    override val notifications = "Уведомления"
    override val allowed = "Разрешены"
    override val offTapToAllow = "Выкл. — нажмите, чтобы разрешить"
    override val openNotificationSettings = "Открыть настройки уведомлений"
    override val onTimeReminders = "Точное время"
    override val on = "Вкл."
    override val batteryOptimisation = "Оптимизация батареи"
    override val notRestricted = "Без ограничений"
    override val restricted = "Ограничено"
    override val batteryHint = "Без точного времени напоминания могут приходить на несколько минут позже. На некоторых " +
        "телефонах оптимизация батареи их вообще останавливает — если они пропадают, снимите для приложения ограничения."
    override val yourData = "Ваши данные"
    override val exportBackup = "Сохранить резервную копию"
    override val restoreBackup = "Восстановить из копии"
    override val deleteEverything = "Удалить всё"
    override val dataHint = "Всё хранится только на этом устройстве. Резервная копия — один файл; с ним можно переехать на новый телефон."
    override val shareUsage = "Отправлять анонимную статистику"
    override val shareUsageHint = "По умолчанию выключено. Только какие функции используются — без названий, заметок и дат."
    override val about = "О приложении"
    override val version = "Версия"
    override val designCatalog = "Каталог дизайна"
    override val backupSaved = "Копия сохранена."
    override fun restored(habits: Int, entries: Int) = "Восстановлено: $habits ${ruPlural(habits, "дело", "дела", "дел")}, $entries ${ruPlural(entries, "запись", "записи", "записей")}."
    override val everythingDeleted = "Всё удалено."
    override fun somethingWrong(detail: String) = "Что-то пошло не так: $detail"
    override val replaceEverything = "Заменить всё?"
    override val replaceBody = "Восстановление заменит все дела, папки и историю на этом устройстве данными из копии."
    override val restore = "Восстановить"
    override val deleteEverythingTitle = "Удалить всё?"
    override val deleteEverythingBody = "Все дела, папки, заметки и напоминания на этом устройстве. Сначала сохраните копию, если они могут понадобиться."
    override val backupNewer = "Эта копия создана более новой версией приложения."
    override val backupInvalid = "Этот файл не является копией из этого приложения."

    override fun toDo(n: Int) = "осталось $n"
    override val widgetAddHabit = "Добавьте дело в приложении, и оно появится здесь."
    override val nothingDueToday = "На сегодня ничего."
    override val allCaughtUp = "На сегодня всё сделано."

    override val skip = "Пропустить"
    override val next = "Далее"
    override val getStarted = "Начать"
    override val onb1Title = "Для того, что повторяется"
    override val onb1Body = "Таблетка дважды в день, фильтр раз в месяц, страховка раз в год. У каждого свой ритм — и всё в одной сетке."
    override val onb2Title = "Опоздать — не страшно"
    override val onb2Body = "Отмечайте день, когда действительно сделали. Следующая дата считается от него — без сорванных серий и чувства вины."
    override val onb3Title = "Начните с нескольких"
    override val onb3Body = "Выберите, что добавить сейчас. Потом всё можно изменить или удалить."
    override val onbReminders = "Напоминать, когда пора"
    override val onbRemindersHint = "Телефон попросит разрешение."
}
