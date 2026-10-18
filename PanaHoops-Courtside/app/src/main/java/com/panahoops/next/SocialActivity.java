package com.panahoops.next;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;
import org.json.*;

public final class SocialActivity extends ComponentActivity {
  Cloud cloud;
  TrainingStore store;
  LinearLayout body;
  ListenerRegistration listener, groupsListener, statsListener;
  String group;
  boolean dead = false;
  CancellationSignal credentialCancel;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    store = new TrainingStore(this);
    try {
      cloud = new Cloud(this);
    } catch (Exception ignored) {
    }
    home();
  }

  void clean() {
    if (listener != null) listener.remove();
    if (groupsListener != null) groupsListener.remove();
    if (statsListener != null) statsListener.remove();
    listener = null;
    groupsListener = null;
    statsListener = null;
  }

  void layout(String title) {
    clean();
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    Ui.insets(this, root, false);
    setContentView(root);
    ScrollView s = new ScrollView(this);
    body = Ui.col(this);
    Ui.pad(body, 20, 16, 20, 30);
    s.addView(body);
    root.addView(s, new LinearLayout.LayoutParams(-1, -1));
    Ui.add(
        body,
        Ui.button(
            this,
            "‹ Volver",
            Ui.WHITE,
            Ui.BLUE,
            () -> {
              if (group != null) {
                group = null;
                home();
              } else finish();
            }));
    Ui.gap(body, 16);
    Ui.add(body, Ui.title(this, title, 36));
    Ui.gap(body, 16);
  }

  void text(String s) {
    Ui.add(body, Ui.text(this, s, 14, Ui.MUTED, false));
    Ui.gap(body, 12);
  }

  void button(String title, Runnable r) {
    Ui.add(body, Ui.button(this, title, Ui.BLUE, Ui.WHITE, r));
    Ui.gap(body, 10);
  }

  void error(Exception e) {
    if (!dead)
      new AlertDialog.Builder(this)
          .setTitle("No se completó la operación")
          .setMessage(
              e.getLocalizedMessage() == null
                  ? "Comprueba la conexión y la configuración del servicio."
                  : e.getLocalizedMessage())
          .setPositiveButton("Cerrar", null)
          .show();
  }

  void home() {
    if (dead) return;
    group = null;
    layout("Tu equipo.\nTu progreso.");
    if (cloud == null) {
      text(
          "El entrenamiento local está disponible. Para cuentas, mensajes y grupos, el"
              + " administrador debe conectar un proyecto Firebase. Esta versión todavía no tiene"
              + " un servidor configurado.");
      button("Conectar servicio", this::configure);
      button("Importar google-services.json", this::importConfig);
      button("Continuar sin cuenta", this::finish);
      return;
    }
    if (cloud.auth.getCurrentUser() == null) {
      text(
          "Comparte estadísticas confirmadas y conversa con tu grupo. Las imágenes de cámara se"
              + " procesan en tu teléfono.");
      button("Iniciar sesión con Google", this::google);
      button("Iniciar sesión con correo", () -> email(false));
      button("Crear cuenta con correo", () -> email(true));
      button("Restablecer contraseña", this::reset);
      button("Entrenar sin cuenta", this::finish);
      button("Configurar servicio", this::configure);
      return;
    }
    text("Conectado como " + cloud.name());
    button(
        "Cambiar mi nombre",
        () ->
            input(
                "Nombre (1–40 caracteres)",
                n -> {
                  if (n.isEmpty() || n.length() > 40) return;
                  cloud
                      .auth
                      .getCurrentUser()
                      .updateProfile(
                          new UserProfileChangeRequest.Builder().setDisplayName(n).build())
                      .addOnSuccessListener(v -> home())
                      .addOnFailureListener(this::error);
                }));
    if (!cloud.auth.getCurrentUser().isEmailVerified())
      button(
          "Enviar verificación de correo",
          () ->
              cloud
                  .auth
                  .getCurrentUser()
                  .sendEmailVerification()
                  .addOnSuccessListener(v -> Ui.toast(this, "Correo enviado"))
                  .addOnFailureListener(this::error));
    button(
        "Crear grupo",
        () ->
            input(
                "Nombre del grupo",
                name -> {
                  if (name.isEmpty() || name.length() > 60) {
                    Ui.toast(this, "Usa entre 1 y 60 caracteres");
                    return;
                  }
                  String id = UUID.randomUUID().toString().replace("-", "");
                  Map<String, Object> g = new HashMap<>();
                  g.put("name", name);
                  g.put("owner", cloud.uid());
                  g.put("members", Arrays.asList(cloud.uid()));
                  g.put("created", FieldValue.serverTimestamp());
                  cloud
                      .db
                      .collection("groups")
                      .document(id)
                      .set(g)
                      .addOnSuccessListener(v -> openGroup(id, name))
                      .addOnFailureListener(this::error);
                }));
    button(
        "Unirme con código",
        () ->
            input(
                "Código privado de 32 caracteres",
                id -> {
                  if (!id.matches("[a-f0-9]{32}")) {
                    Ui.toast(this, "Código no válido");
                    return;
                  }
                  cloud
                      .db
                      .collection("groups")
                      .document(id)
                      .update("members", FieldValue.arrayUnion(cloud.uid()))
                      .addOnSuccessListener(v -> openGroup(id, "Mi grupo"))
                      .addOnFailureListener(this::error);
                }));
    Ui.add(body, Ui.title(this, "Mis grupos", 24));
    LinearLayout list = Ui.col(this);
    Ui.add(body, list);
    groupsListener =
        cloud
            .db
            .collection("groups")
            .whereArrayContains("members", cloud.uid())
            .addSnapshotListener(
                (snap, e) -> {
                  if (dead) return;
                  if (e != null) {
                    error(e);
                    return;
                  }
                  list.removeAllViews();
                  if (snap != null)
                    for (DocumentSnapshot d : snap)
                      Ui.add(
                          list,
                          Ui.button(
                              this,
                              d.getString("name"),
                              Ui.WHITE,
                              Ui.BLUE,
                              () -> openGroup(d.getId(), d.getString("name"))));
                });
    button(
        "Cerrar sesión",
        () -> {
          clean();
          cloud.auth.signOut();
          CredentialManager.create(this)
              .clearCredentialStateAsync(
                  new ClearCredentialStateRequest(),
                  null,
                  ContextCompat.getMainExecutor(this),
                  new CredentialManagerCallback<Void, ClearCredentialException>() {
                    public void onResult(Void v) {}

                    public void onError(ClearCredentialException e) {}
                  });
          home();
        });
  }

  void configure() {
    LinearLayout b = Ui.col(this);
    Ui.pad(b, 16, 8, 16, 8);
    EditText json = new EditText(this);
    json.setMinLines(7);
    json.setText(store.prefs.getString("cloudConfig", ""));
    json.setHint(
        "{\"projectId\":\"...\",\"applicationId\":\"1:...:android:...\",\"apiKey\":\"...\",\"webClientId\":\"...apps.googleusercontent.com\"}");
    Ui.add(b, json);
    new AlertDialog.Builder(this)
        .setTitle("Configuración del administrador")
        .setMessage(
            "Usa solo los valores públicos de tu proyecto Firebase. Nunca pegues claves privadas ni"
                + " un archivo de cuenta de servicio. Primero publica las reglas incluidas en el"
                + " proyecto.")
        .setView(b)
        .setPositiveButton(
            "Guardar",
            (d, n) -> {
              try {
                JSONObject j = Cloud.parseConfig(json.getText().toString(), getPackageName());
                for (String k : new String[] {"projectId", "applicationId", "apiKey"})
                  if (j.getString(k).isEmpty()) throw new Exception("Falta " + k);
                store.prefs.edit().putString("cloudConfig", j.toString()).apply();
                cloud = new Cloud(this);
                home();
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  void importConfig() {
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE),
        71);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 71 || result != RESULT_OK || data == null || data.getData() == null) return;
    try (java.io.InputStream in = getContentResolver().openInputStream(data.getData());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) {
        if (out.size() + n > 262144) throw new Exception("El archivo es demasiado grande");
        out.write(b, 0, n);
      }
      JSONObject j = Cloud.parseConfig(out.toString("UTF-8"), getPackageName());
      for (String k : new String[] {"projectId", "applicationId", "apiKey"})
        if (j.getString(k).isEmpty()) throw new Exception("Falta " + k);
      store.prefs.edit().putString("cloudConfig", j.toString()).apply();
      cloud = new Cloud(this);
      home();
    } catch (Exception e) {
      error(e);
    }
  }

  void google() {
    if (cloud.webClient.isEmpty()) {
      error(new Exception("Falta webClientId y la configuración OAuth de Google."));
      return;
    }
    GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(cloud.webClient).build();
    GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
    credentialCancel = new CancellationSignal();
    CredentialManager.create(this)
        .getCredentialAsync(
            this,
            req,
            credentialCancel,
            ContextCompat.getMainExecutor(this),
            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
              public void onResult(GetCredentialResponse r) {
                try {
                  Credential c = r.getCredential();
                  if (!(c instanceof CustomCredential)
                      || !c.getType()
                          .equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL))
                    throw new Exception("Credencial Google no válida");
                  String token = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
                  cloud
                      .auth
                      .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                      .addOnSuccessListener(v -> home())
                      .addOnFailureListener(SocialActivity.this::error);
                } catch (Exception e) {
                  error(e);
                }
              }

              public void onError(GetCredentialException e) {
                if (!(e instanceof GetCredentialCancellationException)) error(e);
              }
            });
  }

  void email(boolean create) {
    LinearLayout b = Ui.col(this);
    Ui.pad(b, 16, 8, 16, 8);
    EditText mail = new EditText(this);
    mail.setHint("Correo electrónico");
    mail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    EditText pass = new EditText(this);
    pass.setHint("Contraseña");
    pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    Ui.add(b, mail);
    Ui.add(b, pass);
    new AlertDialog.Builder(this)
        .setTitle(create ? "Crear cuenta" : "Iniciar sesión")
        .setView(b)
        .setPositiveButton(
            "Continuar",
            (d, n) -> {
              String m = mail.getText().toString().trim(), p = pass.getText().toString();
              if (m.isEmpty() || p.length() < 6) {
                Ui.toast(this, "Introduce correo y contraseña de 6 caracteres o más");
                return;
              }
              var task =
                  create
                      ? cloud.auth.createUserWithEmailAndPassword(m, p)
                      : cloud.auth.signInWithEmailAndPassword(m, p);
              task.addOnSuccessListener(
                      v -> {
                        if (create) cloud.auth.getCurrentUser().sendEmailVerification();
                        home();
                      })
                  .addOnFailureListener(this::error);
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  void reset() {
    input(
        "Correo de la cuenta",
        email ->
            cloud
                .auth
                .sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> Ui.toast(this, "Solicitud enviada"))
                .addOnFailureListener(this::error));
  }

  void input(String title, java.util.function.Consumer<String> next) {
    EditText e = new EditText(this);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setView(e)
        .setPositiveButton("Continuar", (d, n) -> next.accept(e.getText().toString().trim()))
        .setNegativeButton("Cancelar", null)
        .show();
  }

  void openGroup(String id, String name) {
    group = id;
    layout(name);
    group = id;
    text("Grupo privado · comparte el código solo con personas a las que quieras invitar.");
    button(
        "Compartir invitación",
        () -> {
          Intent send =
              new Intent(Intent.ACTION_SEND)
                  .setType("text/plain")
                  .putExtra(Intent.EXTRA_TEXT, "Únete a mi grupo en PanaHoops. Código: " + id);
          startActivity(Intent.createChooser(send, "Compartir código"));
        });
    button("Comparar estadísticas", () -> comparison(id, name));
    Ui.add(body, Ui.title(this, "Mensajes", 24));
    LinearLayout messages = Ui.col(this);
    Ui.add(body, messages);
    listener =
        cloud
            .db
            .collection("groups")
            .document(id)
            .collection("messages")
            .orderBy("created", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener(
                (snap, e) -> {
                  if (dead) return;
                  if (e != null) {
                    error(e);
                    return;
                  }
                  messages.removeAllViews();
                  if (snap == null) return;
                  List<DocumentSnapshot> docs = new ArrayList<>(snap.getDocuments());
                  Collections.reverse(docs);
                  for (DocumentSnapshot doc : docs) {
                    TextView t =
                        Ui.text(
                            this,
                            doc.getString("name")
                                + ": "
                                + doc.getString("text")
                                + (doc.getMetadata().hasPendingWrites() ? " · enviando…" : ""),
                            14,
                            Ui.INK,
                            false);
                    Ui.pad(t, 8, 10, 8, 10);
                    Ui.add(messages, t);
                    if (cloud.uid().equals(doc.getString("author")))
                      t.setOnLongClickListener(
                          v -> {
                            new AlertDialog.Builder(this)
                                .setMessage("¿Eliminar este mensaje?")
                                .setPositiveButton(
                                    "Eliminar",
                                    (d, n) ->
                                        doc.getReference()
                                            .delete()
                                            .addOnFailureListener(this::error))
                                .setNegativeButton("Cancelar", null)
                                .show();
                            return true;
                          });
                  }
                });
    EditText draft = new EditText(this);
    draft.setHint("Escribe un mensaje (máx. 1000)");
    Ui.add(body, draft);
    Button send =
        Ui.button(
            this,
            "Enviar",
            Ui.BLUE,
            Ui.WHITE,
            () -> {
              String txt = draft.getText().toString().trim();
              if (txt.isEmpty() || txt.length() > 1000) return;
              Map<String, Object> m = new HashMap<>();
              m.put("author", cloud.uid());
              m.put("name", cloud.name());
              m.put("text", txt);
              m.put("created", FieldValue.serverTimestamp());
              cloud
                  .db
                  .collection("groups")
                  .document(id)
                  .collection("messages")
                  .add(m)
                  .addOnSuccessListener(
                      v -> {
                        draft.setText("");
                        Ui.toast(this, "Mensaje enviado");
                      })
                  .addOnFailureListener(this::error);
            });
    Ui.add(body, send);
    button(
        "Salir del grupo",
        () ->
            new AlertDialog.Builder(this)
                .setMessage("Dejarás de ver los mensajes y estadísticas del grupo.")
                .setPositiveButton(
                    "Salir",
                    (d, n) ->
                        cloud
                            .db
                            .collection("groups")
                            .document(id)
                            .update("members", FieldValue.arrayRemove(cloud.uid()))
                            .addOnSuccessListener(v -> home())
                            .addOnFailureListener(this::error))
                .setNegativeButton("Cancelar", null)
                .show());
  }

  void comparison(String id, String name) {
    layout("Estadísticas\ndel equipo");
    group = id;
    text(
        "Solo se publican al pulsar Compartir. Incluyen tiros manuales y resultados de cámara que"
            + " confirmaste. Son datos declarados por cada jugador, sin verificación competitiva.");
    button(
        "Elegir mi jugador de cámara",
        () ->
            input(
                "Nombre exacto de tu jugador en Courtside",
                n -> {
                  store.prefs.edit().putString("myMatchPlayer", n).apply();
                  Ui.toast(this, "Solo se compartirán los tiros confirmados de " + n);
                }));
    button(
        "Compartir mis estadísticas",
        () -> {
          int made = 0, attempts = 0;
          for (TrainingStore.Shot s : store.shots(0)) {
            attempts++;
            if (s.made) made++;
          }
          try {
            JSONArray a = new JSONArray(store.prefs.getString("matchShots", "[]"));
            for (int i = 0; i < a.length(); i++) {
              String r = a.getJSONObject(i).optString("result");
              if (a.getJSONObject(i)
                      .optString("player")
                      .equals(store.prefs.getString("myMatchPlayer", "__none__"))
                  && (r.equals("Enceste confirmado") || r.equals("Fallo confirmado"))) {
                attempts++;
                if (r.equals("Enceste confirmado")) made++;
              }
            }
          } catch (JSONException ignored) {
          }
          Map<String, Object> m = new HashMap<>();
          m.put("name", cloud.name());
          m.put("attempts", attempts);
          m.put("made", made);
          m.put("sessions", store.sessions().size());
          m.put("updated", FieldValue.serverTimestamp());
          cloud
              .db
              .collection("groups")
              .document(id)
              .collection("stats")
              .document(cloud.uid())
              .set(m)
              .addOnSuccessListener(v -> Ui.toast(this, "Estadísticas compartidas"))
              .addOnFailureListener(this::error);
        });
    button(
        "Retirar mis estadísticas",
        () ->
            cloud
                .db
                .collection("groups")
                .document(id)
                .collection("stats")
                .document(cloud.uid())
                .delete()
                .addOnFailureListener(this::error));
    LinearLayout list = Ui.col(this);
    Ui.add(body, list);
    statsListener =
        cloud
            .db
            .collection("groups")
            .document(id)
            .collection("stats")
            .addSnapshotListener(
                (snap, e) -> {
                  if (dead) return;
                  if (e != null) {
                    error(e);
                    return;
                  }
                  list.removeAllViews();
                  if (snap == null) return;
                  for (DocumentSnapshot d : snap) {
                    long a = d.getLong("attempts") == null ? 0 : d.getLong("attempts"),
                        m = d.getLong("made") == null ? 0 : d.getLong("made");
                    Ui.add(
                        list,
                        Ui.text(
                            this,
                            d.getString("name")
                                + " · "
                                + m
                                + "/"
                                + a
                                + " · "
                                + (a == 0 ? "—" : Math.round(m * 100f / a) + " %"),
                            18,
                            Ui.INK,
                            true));
                    Ui.gap(list, 16);
                  }
                });
    button("Volver al chat", () -> openGroup(id, name));
  }

  @Override
  protected void onStop() {
    clean();

    super.onStop();
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    home();
  }

  @Override
  protected void onDestroy() {
    dead = true;
    if (credentialCancel != null) credentialCancel.cancel();
    clean();
    store.close();
    super.onDestroy();
  }
}
