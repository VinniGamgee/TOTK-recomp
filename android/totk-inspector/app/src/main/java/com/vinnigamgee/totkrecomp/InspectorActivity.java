package com.vinnigamgee.totkrecomp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.DocumentsContract;
import android.view.WindowInsets;
import android.graphics.Insets;
import java.io.ByteArrayOutputStream;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InspectorActivity extends Activity {
    private static final int PICK = 10, SAVE = 11;
    private static final String[] MODULES = {"main", "main.npdm", "rtld", "sdk", "subsdk0"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button pick, save;
    private String report;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackgroundColor(Color.rgb(16, 24, 25));
        TextView title = new TextView(this);
        title.setText("TOTK Recomp"); title.setTextSize(28); title.setTextColor(Color.rgb(125, 224, 188));
        layout.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Preparação · versão 1.4.3\n\nSelecione a pasta exefs ou a pasta que contém exefs. A análise lê os cinco módulos e confere a versão. Não copia nem altera os arquivos.\n\nEste APK é de diagnóstico: ainda não recompila nem executa o jogo.");
        intro.setTextSize(16); intro.setPadding(0, 16, 0, 24); layout.addView(intro);
        pick = new Button(this); pick.setText("Selecionar pasta"); layout.addView(pick);
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PICK));
        save = new Button(this); save.setText("Salvar relatório JSON"); save.setEnabled(false); layout.addView(save);
        save.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "totk-143-report.json"), SAVE));
        status = new TextView(this); status.setTextSize(15); status.setTextIsSelectable(true);
        status.setPadding(0, 24, 0, 24); status.setText("Aguardando arquivos."); layout.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        scroll.requestApplyInsets();
        if (state != null) {
            report = state.getString("report");
            status.setText(state.getBoolean("busy") ? "Análise interrompida pela mudança de tela. Selecione a pasta novamente." : state.getString("status", "Aguardando arquivos."));
            save.setEnabled(report != null);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("report", report); state.putString("status", status.getText().toString()); state.putBoolean("busy", busy);
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }

    private void message(String s) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) status.setText(s); });
    }
    private void finished() {
        runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            busy = false; pick.setEnabled(true); save.setEnabled(report != null);
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK) {
            report = null; busy = true; pick.setEnabled(false); save.setEnabled(false);
            worker.execute(() -> { try { analyze(uri); } catch (Exception e) { message("Não foi possível analisar: " + e.getMessage()); } finally { finished(); } });
        } else if (request == SAVE && report != null) {
            String snapshot = report; busy = true; pick.setEnabled(false); save.setEnabled(false);
            worker.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new java.io.IOException("Destino indisponível");
                    out.write(snapshot.getBytes(StandardCharsets.UTF_8)); message("Relatório salvo. Você pode anexar esse JSON à conversa.\n\nEle contém hashes e metadados, sem código ou dados do jogo.");
                } catch (Exception e) { message("Falha ao salvar: " + e.getMessage()); }
                finally { finished(); }
            });
        }
    }

    private static final class Entry {
        final Uri uri; final boolean directory;
        Entry(Uri uri, boolean directory) { this.uri = uri; this.directory = directory; }
    }
    private Map<String, Entry> children(Uri tree, String id) throws Exception {
        Map<String, Entry> found = new LinkedHashMap<>();
        Uri listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = getContentResolver().query(listing, columns, null, null, null)) {
            if (c == null) throw new java.io.IOException("Não foi possível ler a pasta");
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (found.containsKey(name)) throw new java.io.IOException("Nomes duplicados na pasta: " + name);
                found.put(name, new Entry(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)),
                    DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))));
            }
        }
        return found;
    }
    private void analyze(Uri tree) throws Exception {
        message("Lendo a pasta…");
        Map<String, Entry> files = children(tree, DocumentsContract.getTreeDocumentId(tree));
        boolean romfsSeen = files.containsKey("romfs") && files.get("romfs").directory;
        if (files.containsKey("exefs") && files.get("exefs").directory)
            files = children(tree, DocumentsContract.getDocumentId(files.get("exefs").uri));
        JSONObject profile;
        try (InputStream in = getAssets().open("totk-1.4.3.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1;) bytes.write(buffer, 0, n);
            profile = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
        JSONObject expected = profile.getJSONObject("exefs_sha256");
        JSONObject result = new JSONObject();
        result.put("schema_version", 1).put("target", "TOTK 1.4.3").put("title_id", profile.getString("title_id"));
        result.put("reference", profile.getJSONObject("source"));
        result.put("device", new JSONObject().put("model", Build.MODEL).put("android_api", Build.VERSION.SDK_INT).put("abis", new JSONArray(Build.SUPPORTED_ABIS)));
        result.put("romfs", romfsSeen ? "folder_seen_not_validated" : "not_checked");
        JSONArray modules = new JSONArray();
        StringBuilder summary = new StringBuilder();
        boolean all = true;
        for (String name : MODULES) {
            if (Thread.currentThread().isInterrupted()) return;
            message(summary + "\nLendo " + name + "…");
            JSONObject row = new JSONObject().put("name", name);
            Entry entry = files.get(name);
            if (entry == null || entry.directory) {
                row.put("status", "missing"); all = false; summary.append(name).append(": ausente\n");
            } else {
                try (InputStream in = getContentResolver().openInputStream(entry.uri)) {
                    if (in == null) throw new java.io.IOException("Arquivo indisponível");
                    ModuleInspector.Result r = ModuleInspector.inspect(name, in, expected.getString(name));
                    row.put("sha256", r.sha256).put("expected_sha256", expected.getString(name)).put("size", r.fileSize);
                    row.put("status", r.match ? "match" : "mismatch");
                    if (r.buildId != null) row.put("build_id", r.buildId).put("image_size", r.imageSize).put("flags", r.flags).put("segments", new JSONArray(r.segments));
                    if (r.error != null) row.put("error", r.error);
                    all &= r.match; summary.append(name).append(r.match ? ": confere\n" : ": não corresponde à referência\n");
                } catch (java.io.InterruptedIOException e) { return; }
                catch (Exception e) {
                    all = false; row.put("status", "read_error");
                    // Provider error text may include a private path: don't include it in the exported report.
                    row.put("error", e.getClass().getSimpleName()); summary.append(name).append(": erro de leitura\n");
                }
            }
            modules.put(row);
        }
        result.put("modules", modules).put("exefs_matches_reference", all).put("runtime_status", "not_ported");
        final String json = result.toString(2);
        summary.append(all ? "\nExeFS corresponde à referência 1.4.3." : "\nHá arquivos ausentes ou diferentes. Isso pode indicar outra versão, arquivos modificados ou outra representação do NSO.");
        summary.append("\n\nRomFS não foi validado. A correspondência dos arquivos não confirma que o jogo funciona no Android.\n\nSalve o relatório JSON para continuar o port.");
        final String text = summary.toString();
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) { report = json; status.setText(text); } });
    }
}
