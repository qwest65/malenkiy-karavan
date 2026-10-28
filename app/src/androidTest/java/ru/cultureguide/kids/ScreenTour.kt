package ru.cultureguide.kids

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Не проверка, а «экскурсия» по экранам для снимков: главная, выбор маршрута, свой маршрут,
 * прогулка по карте, точка, финал и альбом. Снимки складываются в files/screens приложения,
 * их забирает workflow «Screenshots» (.github/workflows/screenshots.yml).
 */
@RunWith(AndroidJUnit4::class)
class ScreenTour {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val dir = File(context.filesDir, "screens")

    @Before
    fun start() {
        dir.deleteRecursively()
        dir.mkdirs()
        // Геолокация разрешена заранее, чтобы системный диалог не закрывал экран.
        val automation = instrumentation.uiAutomation
        automation.grantRuntimePermission(context.packageName, android.Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(context.packageName, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        // С чистого листа: без альбома и прогулки на паузе.
        context.getSharedPreferences("journey2", 0).edit().clear().commit()
        context.startActivity(
            Intent(context, KaravanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.wait(Until.hasObject(By.text("🐫 Начать прогулку")), 20_000)
    }

    @Test
    fun tour() {
        shot("01-home")
        tap(By.text("🐫 Начать прогулку"))
        shot("02-choose")
        scroll(Direction.DOWN)
        shot("03-choose-more")
        scroll(Direction.UP)
        tap(By.text("Изменить"))
        shot("04-builder")
        device.pressBack()
        // «Крепость и собор» — короткий маршрут из трёх точек.
        scrollTo(By.text("Крепость и собор"))
        val idem = device.findObjects(By.text("🐫 Идём!"))
        (idem.getOrNull(1) ?: idem.first()).click()
        pause(3_000)
        shot("05-walk")
        pause(4_000)
        shot("05b-walk-collapsed")
        repeat(3) { n ->
            // Панель внизу сама сворачивается — тогда остаётся круглая кнопка 🔔.
            (find(By.text("🔔 Мы на месте!")) ?: find(By.text("🔔")) ?: error("Нет кнопки прибытия")).click()
            pause(1_500)
            if (n == 0) shot("06-stop")
            find(By.text("Показать находку"))?.click()
            pause(5_000)
            if (n == 0) shot("07-stop-found")
            scrollTo(By.textStartsWith("❓"))
            if (n == 0) shot("08-quiz")
            scrollTo(By.text("Готово! Идём дальше"), By.text("Ура! Завершить прогулку"))
            if (n == 0) shot("09-stop-bottom")
            (find(By.text("Готово! Идём дальше")) ?: find(By.text("Ура! Завершить прогулку")))?.click()
            pause(2_500)
        }
        pause(6_000)
        shot("10-finale")
        scroll(Direction.DOWN)
        shot("11-finale-more")
        scrollTo(By.text("Мой альбом"))
        tap(By.text("Мой альбом"))
        shot("12-album")
        scroll(Direction.DOWN)
        shot("13-album-more")
        device.pressBack()
        pause(1_500)
        shot("14-home-after")
    }

    private fun find(selector: BySelector): UiObject2? = device.wait(Until.findObject(selector), 5_000)

    private fun tap(selector: BySelector) {
        (find(selector) ?: error("Нет на экране: $selector")).click()
        pause(1_500)
    }

    private fun pause(ms: Long) {
        device.waitForIdle(1_000)
        Thread.sleep(ms)
    }

    private fun scroll(direction: Direction) {
        device.findObject(By.scrollable(true))?.scroll(direction, 0.8f)
        pause(800)
    }

    /** Листает страницу вниз, пока не покажется что-то из [selectors]. */
    private fun scrollTo(vararg selectors: BySelector) {
        repeat(12) {
            if (selectors.any { device.hasObject(it) }) return
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f) ?: return
            pause(300)
        }
    }

    private fun shot(name: String) {
        pause(700)
        device.takeScreenshot(File(dir, "$name.png"))
    }
}
