package com.example.simplemusic

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private class Row(val title: String, val sub: String)

    private var allSongs: List<Song> = emptyList()
    private var tab = 0                       // 0 = Folders, 1 = All Songs
    private var openFolder: String? = null
    private var shownSongs: List<Song>? = null
    private var folderKeys: List<String> = emptyList()

    private lateinit var list: ListView
    private lateinit var header: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var playBtn: Button
    private lateinit var tabFolders: Button
    private lateinit var tabAll: Button

    private val audioPerm =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val onPlayerChange: () -> Unit = { updateBar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.list)
        header = findViewById(R.id.header)
        nowPlaying = findViewById(R.id.nowPlaying)
        playBtn = findViewById(R.id.btnPlay)
        tabFolders = findViewById(R.id.tabFolders)
        tabAll = findViewById(R.id.tabAll)

        tabFolders.setOnClickListener { tab = 0; openFolder = null; render() }
        tabAll.setOnClickListener { tab = 1; render() }
        header.setOnClickListener { openFolder = null; render() }

        list.setOnItemClickListener { _, _, pos, _ ->
            val songs = shownSongs
            if (songs != null) {
                Player.play(this, songs, pos)
            } else {
                openFolder = folderKeys[pos]
                render()
            }
        }

        findViewById<Button>(R.id.btnPrev).setOnClickListener { Player.prev() }
        playBtn.setOnClickListener { Player.toggle() }
        findViewById<Button>(R.id.btnNext).setOnClickListener { Player.next() }

        if (checkSelfPermission(audioPerm) == PackageManager.PERMISSION_GRANTED) {
            load()
        } else {
            val perms = if (Build.VERSION.SDK_INT >= 33)
                arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(audioPerm)
            requestPermissions(perms, 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(audioPerm) == PackageManager.PERMISSION_GRANTED) {
            load()
        } else {
            Toast.makeText(this, "Allow music access to see your songs", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        Player.listeners.add(onPlayerChange)
        updateBar()
    }

    override fun onStop() {
        Player.listeners.remove(onPlayerChange)
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (tab == 0 && openFolder != null) {
            openFolder = null
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun load() {
        allSongs = loadSongs()
        if (allSongs.isEmpty()) Toast.makeText(this, "No songs found", Toast.LENGTH_LONG).show()
        render()
    }

    @Suppress("DEPRECATION")
    private fun loadSongs(): List<Song> {
        val out = ArrayList<Song>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        )
        contentResolver.query(base, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)
            ?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val artist = c.getString(iArtist)
                    out.add(
                        Song(
                            title = c.getString(iTitle) ?: File(path).nameWithoutExtension,
                            artist = if (artist == null || artist == "<unknown>") "" else artist,
                            folder = File(path).parent ?: "",
                            uri = ContentUris.withAppendedId(base, c.getLong(iId))
                        )
                    )
                }
            }
        out.sortBy { it.title.lowercase() }
        return out
    }

    private fun pretty(path: String) = path.replace("/storage/emulated/0", "Internal")

    private fun songRow(s: Song) = Row(s.title, s.artist.ifEmpty { File(s.folder).name })

    private fun render() {
        tabFolders.alpha = if (tab == 0) 1f else 0.5f
        tabAll.alpha = if (tab == 1) 1f else 0.5f
        val f = openFolder
        val rows: List<Row>
        if (tab == 1) {
            header.visibility = View.GONE
            shownSongs = allSongs
            rows = allSongs.map { songRow(it) }
        } else if (f == null) {
            header.visibility = View.GONE
            shownSongs = null
            val groups = allSongs.groupBy { it.folder }
            folderKeys = groups.keys.sortedBy { File(it).name.lowercase() }
            rows = folderKeys.map { Row(File(it).name, "${pretty(it)}  •  ${groups[it]!!.size} songs") }
        } else {
            val songs = allSongs.filter { it.folder == f }
            header.visibility = View.VISIBLE
            header.text = "◀  " + File(f).name
            shownSongs = songs
            rows = songs.map { songRow(it) }
        }
        list.adapter = RowAdapter(rows)
    }

    private fun updateBar() {
        nowPlaying.text = Player.current?.title ?: "Nothing playing"
        playBtn.text = if (Player.isPlaying) "⏸" else "▶"
    }

    private inner class RowAdapter(val rows: List<Row>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView
                ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            v.findViewById<TextView>(android.R.id.text1).text = rows[position].title
            v.findViewById<TextView>(android.R.id.text2).text = rows[position].sub
            return v
        }
    }
}
