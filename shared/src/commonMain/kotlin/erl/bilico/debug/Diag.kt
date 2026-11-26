package erl.bilico.debug

/**
 * 临时诊断输出。真机上用 `xcrun devicectl device process launch --console` 抓 stdout，
 * 定位完之后整份文件删掉。
 */
fun diag(tag: String, message: () -> String) {
    println("DIAG|$tag|${message()}")
}
