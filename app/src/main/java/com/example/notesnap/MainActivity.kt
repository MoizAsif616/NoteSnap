package com.example.notesnap

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import android.Manifest
import android.os.Environment
import android.widget.ImageView
import android.widget.ViewSwitcher
import androidx.lifecycle.lifecycleScope
import com.example.notesnap.ui.theme.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private lateinit var listAdapter: ArrayAdapter<String>
    private val STORAGE_PERMISSION_CODE = 101

    private lateinit var folderAdapter: FolderAdapter
    private val collections = mutableListOf<Folder>()

    @SuppressLint("MissingInflatedId", "WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTheme(R.style.Theme_NoteSnap)

        // Status bar customization
        window.statusBarColor = ContextCompat.getColor(this, R.color.orange)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // Initialize RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.collections_list)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // 2 columns
        folderAdapter = FolderAdapter(collections)
        recyclerView.adapter = folderAdapter

        // Load folders
        loadFolders()

        ensureNoteSnapDirectoryExists()
         ////Setup add button
        findViewById<ImageButton>(R.id.add_btn).setOnClickListener {
            ////check for storage permissions(optionl)
            showAddCollectionDialog()
        }

    }

    data class Folder(val name: String, val path: String)

    class FolderAdapter(private val folders: List<Folder>) :
        RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

        class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.collection_name)
            val imageView: ImageView = view.findViewById(R.id.collection_image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.collection, parent, false)
            return FolderViewHolder(view)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            val displayMetrics = holder.itemView.context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Set width (47%) and height (20%)
            val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
            layoutParams.width = (screenWidth * 0.485).toInt()
            layoutParams.height = (screenHeight * 0.20).toInt()

            // Set alternating margins
            layoutParams.marginStart = if (position % 2 == 0) (screenWidth * 0.01).toInt() else (screenWidth * 0.005).toInt()
            layoutParams.marginEnd = if (position % 2 == 0) (screenWidth * 0.005).toInt() else (screenWidth * 0.01).toInt()

            holder.itemView.layoutParams = layoutParams

            holder.nameView.text = folders[position].name
            // You can customize the image here if needed
        }

        override fun getItemCount() = folders.size
    }

//    private fun loadFolders() {
//        Log.d("MainActivity", "Loading folders...")
//        lifecycleScope.launch(Dispatchers.IO) {
//            val loadedFolders = getFoldersFromStorage()
//            runOnUiThread {
//                collections.clear()
//                collections.addAll(loadedFolders)
//                folderAdapter.notifyDataSetChanged()
//            }
//        }
//    }

    private fun loadFolders() {
        lifecycleScope.launch(Dispatchers.IO) {
            val loadedFolders = getFoldersFromStorage()
            runOnUiThread {
                collections.clear()
                collections.addAll(loadedFolders)
                folderAdapter.notifyDataSetChanged()

                // Show empty state if no items (NEW)
                findViewById<ViewSwitcher>(R.id.viewSwitcher).displayedChild =
                    if (collections.isEmpty()) 1 else 0 // 0=RecyclerView, 1=Empty state
            }
        }
    }

    private fun getFoldersFromStorage(): List<Folder> {
        return try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

            // Filter for direct subfolders of NoteSnap (not nested)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?"
            val args = arrayOf(
                "Pictures/NoteSnap/%/%",  // Folders inside NoteSnap
                "Pictures/NoteSnap/%/%/%" // Exclude deeper nested folders
            )

            val folders = mutableListOf<Folder>()
            contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIndex) // e.g., "Pictures/NoteSnap/FolderName/"
                    val name = cursor.getString(nameIndex) // e.g., "FolderName"

                    // Extract the direct subfolder name
                    if (path != null && name != null) {
                        // Ensure we're only getting immediate children of NoteSnap
                        if (path.count { it == '/' } == 3) { // "Pictures/NoteSnap/FolderName/" has 3 slashes
                            folders.add(Folder(name, path))
                        }
                    }
                }
            }
            folders.distinctBy { it.name } // Remove duplicates
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun showAddCollectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.add_collection_popup, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Set dialog window properties
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            attributes = attributes.apply {
                // Convert 400dp to pixels
                val widthInPixels = (400 * resources.displayMetrics.density).toInt()
                width = widthInPixels
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_create).setOnClickListener {
            val name = dialogView.findViewById<TextInputEditText>(R.id.edit_collection_name).text.toString().trim()
            val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.text_input_layout)

            if (createCollectionFolder(name, textInputLayout)) {
                dialog.dismiss()

            }
        }

        dialog.show()
    }

    private fun createCollectionFolder(name: String, textInputLayout: TextInputLayout): Boolean {
        ensureNoteSnapDirectoryExists()

        if (name.isBlank()) {
            textInputLayout.error = "Name cannot be empty"
            return false
        }

        return when {
            collectionExists(name) -> {
                textInputLayout.error = "Collection '$name' already exists"
                false
            }
            createCollectionSubfolder(name) -> {
                textInputLayout.error = null
                Toast.makeText(this, "Collection created!", Toast.LENGTH_SHORT).show()
                loadFolders()
                true
            }
            else -> {
                textInputLayout.error = "Creation failed. Try another name."
                false
            }
        }
    }

    private fun collectionExists(name: String): Boolean {
        // 2. Verify physical existence via MediaStore
        return try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("%NoteSnap/$name/%")

            contentResolver.query(uri, projection, selection, args, null)?.use {
                it.count > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }







    // Shared pref key for first run check
    private val PREF_FIRST_RUN = "first_run"
    private val NOTE_SNAP_DIR = "Pictures/NoteSnap/"

    private fun ensureNoteSnapDirectoryExists() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Only check/create on first run OR if folder might have been deleted
        if (prefs.getBoolean(PREF_FIRST_RUN, true) || !noteSnapFolderExists()) {
            createNoteSnapRootFolder()
            prefs.edit().putBoolean(PREF_FIRST_RUN, false).apply()
        }
    }

    private fun noteSnapFolderExists(): Boolean {
        return try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("%NoteSnap/%")

            contentResolver.query(uri, projection, selection, args, null)?.use {
                it.count > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun createNoteSnapRootFolder(): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, ".nomedia") // Hidden file
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, NOTE_SNAP_DIR)
                }
            }

            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun createCollectionSubfolder(name: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${name}_cover.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "$NOTE_SNAP_DIR$name/")
                }
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) != null
        } catch (e: Exception) {
            false
        }
    }

}