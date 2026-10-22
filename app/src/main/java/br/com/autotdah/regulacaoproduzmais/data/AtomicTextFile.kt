package br.com.autotdah.regulacaoproduzmais.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Gravação atômica com cópia de segurança:
 * 1. grava em ".tmp" e força a sincronização com o disco;
 * 2. copia o arquivo atual (último estado bom) para ".bak";
 * 3. renomeia ".tmp" sobre o arquivo principal.
 * Uma queda de energia no meio deixa sempre o principal ou o ".bak" íntegro.
 */
class AtomicTextFile(private val file: File) {
    private val bak = File(file.path + ".bak")
    private val tmp = File(file.path + ".tmp")

    @Throws(IOException::class)
    fun write(text: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (file.exists()) file.copyTo(bak, overwrite = true)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Falha ao gravar ${file.name}")
        }
    }

    /** Conteúdos candidatos em ordem de preferência: principal, depois cópia de segurança. */
    fun candidates(): List<String> = listOf(file, bak).filter { it.exists() }.mapNotNull {
        try { it.readText(Charsets.UTF_8) } catch (e: IOException) { null }
    }

    fun anyExists(): Boolean = file.exists() || bak.exists()

    /** Preserva arquivos ilegíveis para análise, em vez de apagá-los. */
    fun quarantine(stamp: Long) {
        listOf(file, bak).filter { it.exists() }.forEach { f ->
            f.renameTo(File(f.path + ".corrompido-$stamp"))
        }
    }

    fun delete() {
        file.delete(); bak.delete(); tmp.delete()
    }
}
