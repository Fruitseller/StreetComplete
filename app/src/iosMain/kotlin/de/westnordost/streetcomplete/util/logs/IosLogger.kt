package de.westnordost.streetcomplete.util.logs

/** Logs to standard output (visible in the Xcode console and `simctl launch --console`).
 *
 *  Note: deliberately uses [println] rather than `NSLog`. `NSLog` is a C variadic
 *  function and passing Kotlin strings as `%@` arguments from Kotlin/Native crashes
 *  (EXC_BAD_ACCESS in `_NSDescriptionWithStringProxyFunc`). */
class IosLogger : Logger {
    override fun v(tag: String, message: String) = println("V/$tag: $message")

    override fun d(tag: String, message: String) = println("D/$tag: $message")

    override fun i(tag: String, message: String) = println("I/$tag: $message")

    override fun w(tag: String, message: String, exception: Throwable?) =
        println("W/$tag: $message" + (exception?.let { "\n" + it.stackTraceToString() } ?: ""))

    override fun e(tag: String, message: String, exception: Throwable?) =
        println("E/$tag: $message" + (exception?.let { "\n" + it.stackTraceToString() } ?: ""))
}
