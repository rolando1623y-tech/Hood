package com.panahoops.next;

import android.content.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.json.*;

final class Cloud {
  static boolean settingsApplied = false;
  final FirebaseApp app;
  final FirebaseAuth auth;
  final FirebaseFirestore db;
  final String webClient;

  Cloud(Context c) throws Exception {
    String config = c.getSharedPreferences("next.profile", 0).getString("cloudConfig", "");
    JSONObject root;
    if (config.isEmpty()) {
      try (java.io.InputStream in = c.getAssets().open("cloud-config.json")) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        root = new JSONObject(out.toString("UTF-8"));
      }
    } else root = new JSONObject(config);
    String project = root.getString("projectId"),
        appid = root.getString("applicationId"),
        key = root.getString("apiKey");
    webClient = root.optString("webClientId");
    if (project.isEmpty() || appid.isEmpty() || key.isEmpty())
      throw new IllegalArgumentException("Configuración incompleta");
    FirebaseApp found = null;
    for (FirebaseApp a : FirebaseApp.getApps(c))
      if (a.getName().equals("PanaHoops")) {
        found = a;
        break;
      }
    if (found != null && !found.getOptions().getProjectId().equals(project))
      throw new IllegalStateException("Reinicia la app para cambiar de proyecto");
    app =
        found != null
            ? found
            : FirebaseApp.initializeApp(
                c,
                new FirebaseOptions.Builder()
                    .setProjectId(project)
                    .setApplicationId(appid)
                    .setApiKey(key)
                    .build(),
                "PanaHoops");
    auth = FirebaseAuth.getInstance(app);
    db = FirebaseFirestore.getInstance(app);
    // Persistent cloud caching is disabled so sign-out does not leave readable group data.
    synchronized (Cloud.class) {
      if (!settingsApplied) {
        db.setFirestoreSettings(
            new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        settingsApplied = true;
      }
    }
  }

  static JSONObject parseConfig(String raw, String packageId) throws Exception {
    JSONObject source = new JSONObject(raw);
    if (source.has("private_key") || source.optString("type").equals("service_account"))
      throw new Exception("Usa google-services.json, nunca una cuenta de servicio");
    if (!source.has("project_info")) return source;
    JSONObject client = null;
    JSONArray clients = source.getJSONArray("client");
    for (int i = 0; i < clients.length(); i++) {
      JSONObject c = clients.getJSONObject(i);
      if (c.getJSONObject("client_info")
          .getJSONObject("android_client_info")
          .getString("package_name")
          .equals(packageId)) {
        client = c;
        break;
      }
    }
    if (client == null) throw new Exception("El archivo no contiene una app para " + packageId);
    String web = "";
    JSONArray oauth = client.optJSONArray("oauth_client");
    if (oauth != null)
      for (int i = 0; i < oauth.length(); i++) {
        JSONObject c = oauth.getJSONObject(i);
        if (c.optInt("client_type") == 3) web = c.getString("client_id");
      }
    return new JSONObject()
        .put("projectId", source.getJSONObject("project_info").getString("project_id"))
        .put("applicationId", client.getJSONObject("client_info").getString("mobilesdk_app_id"))
        .put("apiKey", client.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
        .put("webClientId", web);
  }

  String uid() {
    return auth.getCurrentUser().getUid();
  }

  String name() {
    FirebaseUser u = auth.getCurrentUser();
    if (u == null) return "Invitado";
    String n = u.getDisplayName();
    return n == null || n.isEmpty() ? "Jugador" : n;
  }
}
