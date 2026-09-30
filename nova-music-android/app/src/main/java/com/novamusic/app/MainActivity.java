package com.novamusic.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_DOWNLOAD_FOLDER = 2001;
    private static final int PICK_MUSIC_FOLDER = 2002;
    private static final int MAX_SCAN_DEPTH = 5;
    private static final String PREFS = "nova_music_phone";
    private static final String KEY_DOWNLOAD_TREE_URI = "download_tree_uri";
    private static final String KEY_MUSIC_TREE_URI = "music_tree_uri";
    private static final String DISCORD = "https://discord.gg/jHfHdRGHFj";

    private final int black = Color.rgb(0, 0, 0);
    private final int white = Color.rgb(255, 255, 255);
    private final int panel = Color.rgb(14, 14, 14);
    private final int panel2 = Color.rgb(22, 22, 22);
    private final int line = Color.rgb(42, 42, 42);
    private final int muted = Color.rgb(170, 170, 170);

    private SharedPreferences prefs;
    private LinearLayout content;
    private ProgressBar progress;
    private EditText search;
    private TextView nowPlaying;
    private TextView playerMeta;
    private Button playButton;
    private MediaPlayer mediaPlayer;
    private final ArrayList<TrackItem> library = new ArrayList<>();
    private int currentIndex = -1;
    private boolean playerPreparing;

    private static final class TrackItem {
        final Uri uri;
        final String title;

        TrackItem(Uri uri, String title) {
            this.uri = uri;
            this.title = title;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildShell();
        loadLibraryFromSavedFolder();
        showHome();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        int flags = data.getFlags()
            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(uri, flags);

        if (requestCode == PICK_DOWNLOAD_FOLDER) {
            prefs.edit().putString(KEY_DOWNLOAD_TREE_URI, uri.toString()).apply();
            toast("Download folder saved");
            showDownloads();
            return;
        }

        if (requestCode == PICK_MUSIC_FOLDER) {
            prefs.edit().putString(KEY_MUSIC_TREE_URI, uri.toString()).apply();
            loadLibrary(uri);
            toast("Music folder loaded: " + library.size() + " songs");
            showLocalMusic();
        }
    }

    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(black);
        setContentView(root);

        LinearLayout header = row();
        header.setPadding(dp(18), dp(14), dp(18), dp(8));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView logo = text("NOVA\nMUSIC", 18, true, white);
        logo.setLetterSpacing(0.08f);
        header.addView(logo, new LinearLayout.LayoutParams(dp(92), -2));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(white);
        search.setHintTextColor(muted);
        search.setHint("Search music...");
        search.setTextSize(14);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(makeBg(panel, line, dp(22)));
        header.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button go = pill("Go");
        header.addView(go, new LinearLayout.LayoutParams(dp(64), dp(46)));
        go.setOnClickListener(v -> searchMusic());

        HorizontalScrollView navWrap = new HorizontalScrollView(this);
        navWrap.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = row();
        nav.setPadding(dp(12), dp(4), dp(12), dp(10));
        navWrap.addView(nav);
        root.addView(navWrap, new LinearLayout.LayoutParams(-1, -2));

        addTab(nav, "Home", v -> showHome());
        addTab(nav, "Local Music", v -> showLocalMusic());
        addTab(nav, "Search", v -> showSearch());
        addTab(nav, "Downloads", v -> showDownloads());
        addTab(nav, "Settings", v -> showSettings());
        addTab(nav, "About", v -> showAbout());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        content = column();
        content.setBackgroundColor(black);
        stage.addView(content, new FrameLayout.LayoutParams(-1, -1));

        root.addView(playerBar(), new LinearLayout.LayoutParams(-1, dp(88)));
    }

    private View playerBar() {
        LinearLayout player = row();
        player.setGravity(Gravity.CENTER_VERTICAL);
        player.setPadding(dp(16), dp(10), dp(16), dp(12));
        player.setBackgroundColor(panel);

        LinearLayout copy = column();
        nowPlaying = text("Ready to play", 14, true, white);
        playerMeta = text("Choose Local Music folder", 12, false, muted);
        copy.addView(nowPlaying);
        copy.addView(playerMeta);
        player.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        Button previous = pill("<<");
        previous.setOnClickListener(v -> playPrevious());
        player.addView(previous, new LinearLayout.LayoutParams(dp(54), dp(44)));

        playButton = pill("Play");
        playButton.setOnClickListener(v -> togglePlayback());
        player.addView(playButton, new LinearLayout.LayoutParams(dp(72), dp(44)));

        Button next = pill(">>");
        next.setOnClickListener(v -> playNext());
        player.addView(next, new LinearLayout.LayoutParams(dp(54), dp(44)));
        return player;
    }

    private void showHome() {
        showNative();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(18), dp(18), dp(22));
        scroll.addView(box);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        TextView hero = text("NOVA MUSIC", 36, true, white);
        hero.setLetterSpacing(0.06f);
        box.addView(hero);
        box.addView(text("Phone edition with clean local player. No ads. No video page.", 14, false, muted));
        box.addView(space(18));

        LinearLayout actions = row();
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        Button local = pill("Local Music");
        actions.addView(local, new LinearLayout.LayoutParams(0, dp(48), 1));
        local.setOnClickListener(v -> showLocalMusic());
        Button searchLocal = pill("Search");
        actions.addView(searchLocal, new LinearLayout.LayoutParams(0, dp(48), 1));
        searchLocal.setOnClickListener(v -> showSearch());

        box.addView(space(20));
        box.addView(card("Local Player", "Pick a folder and play MP3, FLAC, M4A, AAC, OGG, OPUS, WAV, and WEBM files."));
        box.addView(card("Clean Playback", "No embedded YouTube video page, no web player clutter, and no ad-bypass code."));
        box.addView(card("Downloads", "Direct file downloads ask for a folder and save there. Protected stream ripping is not included."));
    }

    private void showLocalMusic() {
        showNative();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        scroll.addView(box);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        box.addView(text("Local Music", 30, true, white));
        box.addView(text(library.size() + " songs loaded", 14, false, muted));
        box.addView(space(14));

        LinearLayout actions = row();
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        Button choose = pill("Choose Folder");
        choose.setOnClickListener(v -> chooseMusicFolder());
        actions.addView(choose, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button rescan = pill("Rescan");
        rescan.setOnClickListener(v -> {
            loadLibraryFromSavedFolder();
            showLocalMusic();
        });
        actions.addView(rescan, new LinearLayout.LayoutParams(0, dp(50), 1));
        box.addView(space(16));

        if (library.isEmpty()) {
            box.addView(card("No local songs yet", "Tap Choose Folder and select the folder where your music files are saved."));
            return;
        }

        for (int i = 0; i < library.size(); i++) {
            int index = i;
            TrackItem item = library.get(i);
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(makeBg(index == currentIndex ? panel2 : panel, line, dp(8)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(params);

            LinearLayout copy = column();
            copy.addView(text(item.title, 15, true, white));
            copy.addView(text("Local file", 12, false, muted));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button play = pill(index == currentIndex && isPlaying() ? "Pause" : "Play");
            play.setOnClickListener(v -> playTrack(index));
            row.addView(play, new LinearLayout.LayoutParams(dp(78), dp(42)));
            box.addView(row);
        }
    }

    private void showSearch() {
        showNative();
        content.removeAllViews();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(28), dp(18), dp(18));
        content.addView(box, new LinearLayout.LayoutParams(-1, -1));
        box.addView(text("Search", 30, true, white));
        box.addView(text("Type above and press Go. Results filter your local music library.", 14, false, muted));
        box.addView(space(18));
        Button button = pill("Search Local Library");
        box.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
        button.setOnClickListener(v -> searchMusic());
    }

    private void showDownloads() {
        showNative();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        scroll.addView(box);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        box.addView(text("Downloads", 30, true, white));
        box.addView(text("Choose a folder once. Direct file downloads save there.", 14, false, muted));
        box.addView(space(16));

        box.addView(text("Folder: " + currentFolderLabel(KEY_DOWNLOAD_TREE_URI), 13, false, muted));
        box.addView(space(10));

        Button choose = pill("Choose Download Folder");
        box.addView(choose, new LinearLayout.LayoutParams(-1, dp(52)));
        choose.setOnClickListener(v -> chooseDownloadFolder());
        box.addView(space(16));

        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("Direct file URL...");
        url.setTextColor(white);
        url.setHintTextColor(muted);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.setPadding(dp(16), 0, dp(16), 0);
        url.setBackground(makeBg(panel, line, dp(10)));
        box.addView(url, new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(space(10));

        Button save = pill("Download File");
        box.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        save.setOnClickListener(v -> queueDownload(url.getText().toString().trim(), null));
        box.addView(space(16));
        box.addView(card("Legal mode", "This app does not rip protected services. It saves direct files you own or have permission to download."));
    }

    private void showSettings() {
        showNative();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        scroll.addView(box);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        box.addView(text("Settings", 30, true, white));
        box.addView(space(14));
        box.addView(card("Theme", "NOVA MUSIC stays black and white for the premium look."));
        box.addView(card("Local Music Folder", currentFolderLabel(KEY_MUSIC_TREE_URI)));
        box.addView(card("Download Folder", currentFolderLabel(KEY_DOWNLOAD_TREE_URI)));
        box.addView(card("Online Music", "YouTube web/video playback is removed in this Android build. It keeps the app clean and avoids protected-stream bypassing."));
    }

    private void showAbout() {
        showNative();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        scroll.addView(box);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        box.addView(text("About NOVA MUSIC", 30, true, white));
        box.addView(text("A premium music app for PC and phone.", 14, false, muted));
        box.addView(space(18));
        box.addView(owner("D", "DIVYANG", "Owner of NOVA MUSIC", "divyang0991__"));
        box.addView(owner("R", "RAHUL BHAI", "Co-owner", "rahul_00012"));
        box.addView(space(12));
        Button discord = pill("Open Discord Server");
        box.addView(discord, new LinearLayout.LayoutParams(-1, dp(52)));
        discord.setOnClickListener(v -> openUrl(DISCORD));
    }

    private void searchMusic() {
        String q = search.getText().toString().trim();
        if (q.isEmpty()) {
            toast("Type something first");
            return;
        }
        showSearchResults(q);
    }

    private void showSearchResults(String query) {
        showNative();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout box = column();
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        scroll.addView(box);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        box.addView(text("Search", 30, true, white));
        box.addView(text("Results for \"" + query + "\"", 14, false, muted));
        box.addView(space(14));

        String needle = query.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (int i = 0; i < library.size(); i++) {
            TrackItem item = library.get(i);
            if (!item.title.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            hits++;
            int index = i;
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(makeBg(index == currentIndex ? panel2 : panel, line, dp(8)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(params);

            LinearLayout copy = column();
            copy.addView(text(item.title, 15, true, white));
            copy.addView(text("Local result", 12, false, muted));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button play = pill(index == currentIndex && isPlaying() ? "Pause" : "Play");
            play.setOnClickListener(v -> playTrack(index));
            row.addView(play, new LinearLayout.LayoutParams(dp(78), dp(42)));
            box.addView(row);
        }

        if (hits == 0) {
            box.addView(card("No matches", "Choose or rescan your Local Music folder, then search again."));
        }
    }

    private void chooseDownloadFolder() {
        chooseTree(PICK_DOWNLOAD_FOLDER);
    }

    private void chooseMusicFolder() {
        chooseTree(PICK_MUSIC_FOLDER);
    }

    private void chooseTree(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void loadLibraryFromSavedFolder() {
        String saved = prefs.getString(KEY_MUSIC_TREE_URI, null);
        if (saved != null) {
            loadLibrary(Uri.parse(saved));
        }
    }

    private void loadLibrary(Uri treeUri) {
        library.clear();
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            scanFolder(treeUri, rootId, 0);
            Collections.sort(library, Comparator.comparing(item -> item.title.toLowerCase(Locale.ROOT)));
        } catch (Exception error) {
            toast("Cannot scan folder: " + error.getMessage());
        }
    }

    private void scanFolder(Uri treeUri, String parentId, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] projection = {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                Uri document = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                if (Document.MIME_TYPE_DIR.equals(mime)) {
                    scanFolder(treeUri, id, depth + 1);
                } else if (isAudio(name, mime)) {
                    library.add(new TrackItem(document, cleanTitle(name)));
                }
            }
        } catch (Exception ignored) {
            // Some storage providers hide folders or individual files. Keep scanning what is readable.
        }
    }

    private boolean isAudio(String name, String mime) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return true;
        }
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3")
            || lower.endsWith(".flac")
            || lower.endsWith(".m4a")
            || lower.endsWith(".aac")
            || lower.endsWith(".ogg")
            || lower.endsWith(".opus")
            || lower.endsWith(".wav")
            || lower.endsWith(".webm");
    }

    private String cleanTitle(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unknown Track";
        }
        return name.replaceFirst("\\.[^.]+$", "");
    }

    private void playTrack(int index) {
        if (index < 0 || index >= library.size()) {
            return;
        }
        if (index == currentIndex && isPlaying()) {
            togglePlayback();
            return;
        }
        currentIndex = index;
        TrackItem item = library.get(index);
        releasePlayer();
        mediaPlayer = new MediaPlayer();
        playerPreparing = true;
        try {
            mediaPlayer.setDataSource(this, item.uri);
            mediaPlayer.setOnPreparedListener(player -> {
                playerPreparing = false;
                player.start();
                updatePlayerUi(item.title, "Playing local music");
            });
            mediaPlayer.setOnCompletionListener(player -> playNext());
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                playerPreparing = false;
                toast("Cannot play this file");
                playNext();
                return true;
            });
            updatePlayerUi(item.title, "Loading...");
            mediaPlayer.prepareAsync();
        } catch (Exception error) {
            playerPreparing = false;
            toast("Cannot play: " + error.getMessage());
            playNext();
        }
    }

    private void togglePlayback() {
        if (mediaPlayer == null) {
            if (!library.isEmpty()) {
                playTrack(currentIndex >= 0 ? currentIndex : 0);
            } else {
                toast("Choose Local Music folder first");
            }
            return;
        }
        if (playerPreparing) {
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playerMeta.setText("Paused");
            playButton.setText("Play");
        } else {
            mediaPlayer.start();
            playerMeta.setText("Playing local music");
            playButton.setText("Pause");
        }
    }

    private void playNext() {
        if (library.isEmpty()) {
            return;
        }
        int next = currentIndex < 0 ? 0 : (currentIndex + 1) % library.size();
        playTrack(next);
    }

    private void playPrevious() {
        if (library.isEmpty()) {
            return;
        }
        int previous = currentIndex <= 0 ? library.size() - 1 : currentIndex - 1;
        playTrack(previous);
    }

    private boolean isPlaying() {
        return mediaPlayer != null && !playerPreparing && mediaPlayer.isPlaying();
    }

    private void updatePlayerUi(String title, String meta) {
        nowPlaying.setText(title);
        playerMeta.setText(meta);
        playButton.setText(isPlaying() || playerPreparing ? "Pause" : "Play");
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playerPreparing = false;
        if (playButton != null) {
            playButton.setText("Play");
        }
    }

    private void queueDownload(String sourceUrl, String suggestedName) {
        if (sourceUrl == null || !sourceUrl.toLowerCase(Locale.ROOT).startsWith("http")) {
            toast("Valid URL paste kar");
            return;
        }
        String tree = prefs.getString(KEY_DOWNLOAD_TREE_URI, null);
        if (tree == null) {
            toast("Pehle download folder choose kar");
            chooseDownloadFolder();
            return;
        }
        String name = suggestedName != null ? suggestedName : URLUtil.guessFileName(sourceUrl, null, null);
        toast("Download started");
        new Thread(() -> downloadToTree(Uri.parse(tree), sourceUrl, cleanName(name))).start();
    }

    private void downloadToTree(Uri treeUri, String sourceUrl, String fileName) {
        try {
            ContentResolver resolver = getContentResolver();
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
            Uri target = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", fileName);
            if (target == null) {
                throw new IllegalStateException("cannot create target file");
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.connect();
            if (connection.getResponseCode() >= 400) {
                throw new IllegalStateException("server refused download");
            }

            try (InputStream input = connection.getInputStream();
                 OutputStream output = resolver.openOutputStream(target, "w")) {
                if (output == null) {
                    throw new IllegalStateException("cannot open target file");
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                connection.disconnect();
            }
            runOnUiThread(() -> toast("Download saved: " + fileName));
        } catch (Exception error) {
            runOnUiThread(() -> toast("Download failed: " + error.getMessage()));
        }
    }

    private String cleanName(String value) {
        String name = value == null || value.trim().isEmpty() ? "nova-music-file" : value.trim();
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String currentFolderLabel(String key) {
        String value = prefs.getString(key, null);
        return value == null ? "not selected" : "selected";
    }

    private void showNative() {
        content.setVisibility(View.VISIBLE);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            toast("No browser found");
        }
    }

    private void addTab(LinearLayout nav, String label, View.OnClickListener listener) {
        Button tab = pill(label);
        tab.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42));
        params.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(tab, params);
    }

    private View card(String title, String detail) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(makeBg(panel, line, dp(8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        card.addView(text(title, 18, true, white));
        card.addView(space(4));
        card.addView(text(detail, 14, false, muted));
        return card;
    }

    private View owner(String initial, String name, String role, String username) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(makeBg(panel, line, dp(8)));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(outer);

        TextView avatar = text(initial, 24, true, black);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(makeBg(white, white, dp(30)));
        card.addView(avatar, new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout copy = column();
        copy.setPadding(dp(14), 0, 0, 0);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        copy.addView(text(name, 17, true, white));
        copy.addView(text(role, 13, false, muted));
        copy.addView(text("@" + username, 13, false, white));
        return card;
    }

    private Button pill(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(white);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(makeBg(Color.rgb(20, 20, 20), line, dp(22)));
        return button;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(true);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(black);
        return scroll;
    }

    private View space(int dp) {
        View view = new View(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(1, dp(dp)));
        return view;
    }

    private android.graphics.drawable.Drawable makeBg(int fill, int stroke, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
