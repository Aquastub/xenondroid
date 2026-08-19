package com.cyank.xenondroidcc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import android.widget.Button
import android.widget.TextView
import java.io.File

/**
 * XenonDroidCC — compiles C/C++ from a user-chosen folder into a
 * powerpc-xenon-elf .xex, using a cross-compiler that was built for
 * *this device's* CPU by the GitHub Actions workflow and shipped
 * inside the APK's assets.
 *
 * IMPORTANT: this activity assumes the toolchain assets described in
 * app/build.gradle.kts (assets/toolchain-raw/xenon-android-toolchain/...)
 * were actually present at build time. If you built the APK before the
 * toolchain workflow succeeded, compilation will fail with a clear
 * "toolchain not found" message rather than silently doing nothing.
 */
class MainActivity : AppCompatActivity() {

    private var sourceTreeUri: Uri? = null
    private var outputTreeUri: Uri? = null

    private lateinit var txtSourcePath: TextView
    private lateinit var txtOutputPath: TextView
    private lateinit var txtLog: TextView

    private val pickSource =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                sourceTreeUri = uri
                txtSourcePath.text = uri.path
            }
        }

    private val pickOutput =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                outputTreeUri = uri
                txtOutputPath.text = uri.path
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtSourcePath = findViewById(R.id.txtSourcePath)
        txtOutputPath = findViewById(R.id.txtOutputPath)
        txtLog = findViewById(R.id.txtLog)

        findViewById<Button>(R.id.btnPickSource).setOnClickListener {
            pickSource.launch(null)
        }
        findViewById<Button>(R.id.btnPickOutput).setOnClickListener {
            pickOutput.launch(null)
        }
        findViewById<Button>(R.id.btnCompile).setOnClickListener {
            runCatching { compile() }.onFailure { log("ERROR: ${it.message}") }
        }
    }

    private fun log(line: String) {
        runOnUiThread { txtLog.append("\n$line") }
    }

    /** Copies the bundled toolchain out of assets into app-private
     *  storage and marks the binaries executable. Android will not
     *  execute files straight out of the APK's assets. Only needs to
     *  run once; skips work if already installed. */
    private fun installToolchainIfNeeded(): File {
        val installDir = File(filesDir, "xenon-android-toolchain")
        val marker = File(installDir, ".installed")
        if (marker.exists()) return installDir

        log("Installing bundled toolchain (first run only)...")
        val assetRoot = "toolchain-raw/xenon-android-toolchain"
        copyAssetDirRecursive(assetRoot, installDir)

        // Make every file under bin/ executable.
        File(installDir, "bin").listFiles()?.forEach { it.setExecutable(true) }
        marker.createNewFile()
        log("Toolchain installed at ${installDir.absolutePath}")
        return installDir
    }

    private fun copyAssetDirRecursive(assetPath: String, destDir: File) {
        destDir.mkdirs()
        val entries = assets.list(assetPath) ?: return
        if (entries.isEmpty()) {
            // It's a file, not a directory.
            assets.open(assetPath).use { input ->
                File(destDir.parentFile, destDir.name).outputStream().use { input.copyTo(it) }
            }
            return
        }
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            val childDest = File(destDir, entry)
            val subEntries = assets.list(childAssetPath)
            if (subEntries.isNullOrEmpty()) {
                assets.open(childAssetPath).use { input ->
                    childDest.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDirRecursive(childAssetPath, childDest)
            }
        }
    }

    /** Copies every .c/.cpp/.h from the chosen SAF source tree into a
     *  plain app-private folder the compiler process can actually see. */
    private fun stageSources(): File {
        val stageDir = File(cacheDir, "src-stage").apply { deleteRecursively(); mkdirs() }
        val treeUri = sourceTreeUri ?: error("Pick a source folder first.")
        val tree = DocumentFile.fromTreeUri(this, treeUri) ?: error("Cannot open source folder.")

        var copied = 0
        for (doc in tree.listFiles()) {
            val name = doc.name ?: continue
            if (name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h")) {
                contentResolver.openInputStream(doc.uri)?.use { input ->
                    File(stageDir, name).outputStream().use { output -> input.copyTo(output) }
                }
                copied++
            }
        }
        log("Staged $copied source file(s) from chosen folder.")
        if (copied == 0) error("No .c/.cpp files found in the chosen folder.")
        return stageDir
    }

    private fun compile() {
        val toolchainDir = installToolchainIfNeeded()
        val compiler = File(toolchainDir, "bin/powerpc-xenon-elf-g++")
        if (!compiler.exists()) {
            error(
                "Compiler binary not found at ${compiler.path}. " +
                "This means the GitHub Actions toolchain build didn't " +
                "produce a working binary for this run — check the " +
                "'Cross-build gcc stage' job log and iterate on the " +
                "workflow before rebuilding the APK."
            )
        }

        val stageDir = stageSources()
        val sources = stageDir.listFiles { f -> f.extension in listOf("c", "cpp") } ?: emptyArray()
        val outElf = File(cacheDir, "out.elf")

        log("Running compiler...")
        val cmd = mutableListOf(compiler.absolutePath, "-o", outElf.absolutePath)
        cmd.addAll(sources.map { it.absolutePath })

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .directory(stageDir)
            .start()
        process.inputStream.bufferedReader().forEachLine { log(it) }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            log("Compile FAILED (exit $exitCode). See log above.")
            return
        }
        log("Compile succeeded: ${outElf.name}")

        // TODO: elf -> xex conversion step (elf2xex / imagexex from the
        // xenon-toolchain project) still needs to run here before this
        // is a real, loadable XenDroid executable — not wired up yet.
        exportToOutputFolder(outElf, "out.xex-PLACEHOLDER-elf")
    }

    private fun exportToOutputFolder(file: File, destName: String) {
        val treeUri = outputTreeUri ?: error("Pick an output folder first.")
        val tree = DocumentFile.fromTreeUri(this, treeUri) ?: error("Cannot open output folder.")
        tree.findFile(destName)?.delete()
        val outDoc = tree.createFile("application/octet-stream", destName)
            ?: error("Could not create output file.")
        contentResolver.openOutputStream(outDoc.uri)?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        }
        log("Copied result to output folder as $destName")
    }
}
