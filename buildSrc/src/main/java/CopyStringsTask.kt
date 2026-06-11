import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.collections.orEmpty
import kotlin.io.copyTo

open class CopyStringsTask : DefaultTask() {
    @get:Input lateinit var sourceDir: String
    @get:Input lateinit var targetDir: String

    @TaskAction
    fun run() {
        // DOT_MATCHES_ALL + lazy quantifier so that multi-line strings are also
        // matched and thus quoted — otherwise aapt2 fails on unescaped apostrophes
        val stringRegex = Regex("<string name=\"([a-zA-Z0-9_]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        for (dir in File(sourceDir).listFiles().orEmpty()) {
            if (dir.isDirectory) {
                val stringsFile = File(dir, "strings.xml")
                if (stringsFile.exists()) {
                    val stringsFileText = stringsFile.readText()
                    val newStringsFileText = stringRegex.replace(stringsFileText) {
                        val key = it.groupValues[1]
                        val value = it.groupValues[2]
                        "<string name=\"$key\">\"${value.escapeXml().replace("\"", "\\\"")}\"</string>"
                    }

                    val targetStringsDir = File(targetDir, dir.name)
                    val targetStringsFile = File(targetStringsDir, stringsFile.name)

                    targetStringsDir.mkdirs()
                    targetStringsFile.writeText(newStringsFileText)
                }
            }
        }
    }
}
