package dev.xverlxrd.batterycapacity.data.datasource.sysfs

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Абстракция чтения sysfs — чтобы unit-тесты подсовывали фикстуры
 * Pixel/Samsung/Xiaomi без реального устройства.
 */
interface SysfsReader {
    fun listDirs(root: String): List<String>
    fun exists(path: String): Boolean
    fun readText(path: String): String?
}

@Singleton
class RealSysfsReader @Inject constructor() : SysfsReader {
    override fun listDirs(root: String): List<String> =
        File(root).listFiles { f -> f.isDirectory }?.map { it.absolutePath } ?: emptyList()

    override fun exists(path: String): Boolean = File(path).exists()

    override fun readText(path: String): String? = runCatching {
        File(path).readText().trim()
    }.getOrNull()
}
