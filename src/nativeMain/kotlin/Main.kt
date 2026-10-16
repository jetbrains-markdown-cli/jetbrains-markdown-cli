import com.github.ajalt.clikt.core.main
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fread
import platform.posix.stdin

fun main(args: Array<String>) = MarkdownCli(readInput = ::readStdin).main(args)

@OptIn(ExperimentalForeignApi::class)
private fun readStdin(): String {
    var data = ByteArray(0)
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = buffer.usePinned { fread(it.addressOf(0), 1u, buffer.size.convert(), stdin) }.toInt()
        if (n <= 0) break
        data += buffer.copyOf(n)
    }
    return data.decodeToString()
}
