import com.github.ajalt.clikt.core.main
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.stdin

fun main(args: Array<String>) = MarkdownCli(readStdin = ::readStdin, readFile = ::readFile, writeFile = ::writeFile).main(args)

@OptIn(ExperimentalForeignApi::class)
private fun readStdin(): String = readAll(stdin!!).decodeToString()

/** The content of the file at [path], or null if it cannot be opened or read. */
@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): String? {
    val file = fopen(path, "rb") ?: return null
    try {
        val data = readAll(file)
        return if (ferror(file) != 0) null else data.decodeToString()
    } finally {
        fclose(file)
    }
}

/** Writes [text] as UTF-8 to the file at [path], replacing it. Returns false if that fails. */
@OptIn(ExperimentalForeignApi::class)
private fun writeFile(path: String, text: String): Boolean {
    val file = fopen(path, "wb") ?: return false
    val data = text.encodeToByteArray()
    val written = if (data.isEmpty()) 0 else data.usePinned { fwrite(it.addressOf(0), 1u, data.size.convert(), file) }.toLong()
    // fclose flushes, so it can fail for a full disk even if fwrite did not.
    return fclose(file) == 0 && written == data.size.toLong()
}

@OptIn(ExperimentalForeignApi::class)
private fun readAll(file: CPointer<FILE>): ByteArray {
    var data = ByteArray(0)
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = buffer.usePinned { fread(it.addressOf(0), 1u, buffer.size.convert(), file) }.toInt()
        if (n <= 0) break
        data += buffer.copyOf(n)
    }
    return data
}
