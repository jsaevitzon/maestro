package maestro.utils

import com.github.romankh3.image.comparison.ImageComparison
import maestro.Driver
import maestro.ViewHierarchy
import okio.Buffer
import okio.Sink
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.time.LocalDateTime

class ScreenshotUtils {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ScreenshotUtils::class.java)

        fun takeScreenshot(out: Sink, compressed: Boolean, driver: Driver) {
            LOGGER.info("Taking screenshot to output sink")

            driver.takeScreenshot(out, compressed)
        }

        fun takeScreenshot(compressed: Boolean, driver: Driver): ByteArray {
            LOGGER.info("Taking screenshot to byte array")

            val buffer = Buffer()
            takeScreenshot(buffer, compressed, driver)

            return buffer.readByteArray()
        }

        fun tryTakingScreenshot(driver: Driver) = try {
            ImageIO.read(takeScreenshot(true, driver).inputStream())
        } catch (e: Exception) {
            LOGGER.warn("Failed to take screenshot", e)
            null
        }

        fun waitForAppToSettle(initialHierarchy: ViewHierarchy?, driver: Driver): ViewHierarchy {
            var latestHierarchy = initialHierarchy ?: viewHierarchy(driver)
            repeat(10) {
                val hierarchyAfter = viewHierarchy(driver)
                if (latestHierarchy == hierarchyAfter) {
                    val isLoading = latestHierarchy.root.attributes.getOrDefault("is-loading", "false").toBoolean()
                    if (!isLoading) {
                        LOGGER.info("View loaded " + LocalDateTime.now())
                        println("View loaded " + LocalDateTime.now())
                        return hierarchyAfter
                    }
                }
                latestHierarchy = hierarchyAfter

                MaestroTimer.sleep(MaestroTimer.Reason.WAIT_TO_SETTLE, 200)
            }

            return latestHierarchy
        }

        fun waitUntilScreenIsStatic(timeoutMs: Long, threshold: Double, driver: Driver): Boolean {
            return MaestroTimer.retryUntilTrue(timeoutMs) {
                val startScreenshot: BufferedImage? = tryTakingScreenshot(driver)
                val endScreenshot: BufferedImage? = tryTakingScreenshot(driver)

                if (startScreenshot != null &&
                    endScreenshot != null &&
                    startScreenshot.width == endScreenshot.width &&
                    startScreenshot.height == endScreenshot.height
                ) {
                    val imageDiff = ImageComparison(
                        startScreenshot,
                        endScreenshot
                    ).compareImages().differencePercent

                    return@retryUntilTrue imageDiff <= threshold
                }

                return@retryUntilTrue false
            }
        }

        private fun viewHierarchy(driver: Driver): ViewHierarchy {
            return ViewHierarchy.from(driver)
        }
    }
}