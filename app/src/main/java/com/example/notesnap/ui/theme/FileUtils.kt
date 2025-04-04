package com.example.notesnap.ui.theme

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileWriter

object FileUtils {
    fun getOrCreateNoteSnapDirectory(): File {
        val baseDir = Environment.getExternalStorageDirectory() // Root storage
        val noteSnapDir = File(baseDir, "NoteSnap")
        if (!noteSnapDir.exists()) {
            noteSnapDir.mkdirs()
            // Create a dummy file to ensure visibility
            File(noteSnapDir, "README.txt").createNewFile().let {
                if (it) {
                    FileWriter(File(noteSnapDir, "README.txt")).use { writer ->
                        writer.write("NoteSnap app data folder")
                    }
                }
            }
        }
        return noteSnapDir
    }
}