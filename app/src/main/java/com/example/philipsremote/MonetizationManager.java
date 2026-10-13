package com.example.philipsremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonetizationManager {
    public static final String PREMIUM_PRODUCT_ID = "ir_remote_premium";
    private static final String PREFS = "remote_prefs";
    private static final String KEY_PREMIUM = "premium_unlocked";
    private static final String KEY_PREMIUM_UID = "premium_unlocked_uid";
    private static final String KEY_PREMIUM_UPDATED = "premium_unlocked_at";
    private static final String KEY_PURCHASE_ID = "mp_purchase_id";
    private static final String KEY_DEVICE_ID = "premium_device_id";
    private static final int FREE_SAVED_LIMIT = 1;
    private static final String SERVER = "https://ir-remote-br-premium-server.vercel.app";
    private final StartIoAdsManager startIoAds;

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Runnable onPremiumChanged;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean requestInProgress = false;

    public MonetizationManager(Activity activity, Runnable onPremiumChanged) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        this.onPremiumChanged = onPremiumChanged;
        this.startIoAds = new StartIoAdsManager(activity);
    }

    public boolean isPremium() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null
                && user.getUid().equals(prefs.getString(KEY_PREMIUM_UID, ""))
                && prefs.getBoolean(KEY_PREMIUM, false);
    }

    public boolean podeSalvarControle(int quantidadeAtual) {
        return podeSalvar(quantidadeAtual, isPremium());
    }

    static boolean podeSalvar(int quantidadeAtual, boolean premium) {
        return premium || Math.max(0, quantidadeAtual) < FREE_SAVED_LIMIT;
    }

    public int limiteGratuito() {
        return FREE_SAVED_LIMIT;
    }

    public String resumoLimite(int quantidadeAtual) {
        return isPremium()
                ? quantidadeAtual + " controles • ilimitado"
                : quantidadeAtual + "/" + FREE_SAVED_LIMIT + " controles salvos";
    }

    /** Compatibilidade com chamadas antigas: banners foram removidos na migração para intersticiais. */
    public void addBanner(LinearLayout root) {
        // Sem banners; anúncios em tela cheia são chamados em pontos de navegação específicos.
    }

    public void mostrarIntersticialSePermitido() {
        if (!isPremium()) startIoAds.showIfReady();
    }

    public void showPremiumDialog() {
        if (isPremium()) {
            new AlertDialog.Builder(activity)
                    .setTitle("⭐ IR Remote BR Premium")
                    .setMessage("Premium ativo neste aparelho.\n\n"
                            + "✓ Sem anúncios\n✓ Controles salvos ilimitados\n"
                            + "✓ Mais códigos e testes personalizados\n"
                            + "✓ Recursos avançados para controles universais")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("VERIFICAR PAGAMENTO", (d, w) -> verificarPagamento(true))
                    .show();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("⭐ IR Remote BR Premium")
                .setMessage("Desbloqueie o aplicativo completo.\n\n"
                        + "✓ Sem anúncios\n"
                        + "✓ Controles salvos ilimitados\n"
                        + "✓ Mais códigos e testes personalizados\n"
                        + "✓ Recursos avançados para controles universais\n"
                        + "✓ Compra única, sem mensalidade\n\n"
                        + "Preço: R$ 19,90\n"
                        + "Pagamento seguro pelo Mercado Pago.")
                .setNegativeButton("AGORA NÃO", null)
                .setNeutralButton("GERAR PIX", null)
                .setPositiveButton("OUTRAS FORMAS DE PAGAR", (d, w) -> criarCheckout())
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(x -> solicitarPix()));
        dialog.show();
    }

    private void solicitarPix() {
        EditText emailInput = new EditText(activity);
        emailInput.setSingleLine(true);
        emailInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setHint("seuemail@exemplo.com");
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad / 2, pad, 0);
        TextView note = new TextView(activity);
        note.setText("Informe seu e-mail para gerar o Pix de R$ 19,90 pelo Mercado Pago.");
        form.addView(note);
        form.addView(emailInput, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(activity)
                .setTitle("Gerar Pix")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("GERAR PIX", (d, w) -> {
                    String email = emailInput.getText().toString().trim();
                    if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        toast("Informe um e-mail válido para gerar o Pix.");
                    } else {
                        criarPix(email);
                    }
                }).show();
    }

    private void criarPix(String email) {
        if (requestInProgress) {
            toast("Aguarde, estamos processando sua solicitação.");
            return;
        }
        requestInProgress = true;
        toast("Gerando Pix seguro...");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER + "/api/create-pix");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + getFirebaseIdToken());
                JSONObject request = new JSONObject();
                request.put("email", email);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(request.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                String response = readResponse(connection, status);
                JSONObject json = new JSONObject(response);
                if (status < 200 || status >= 300) {
                    throw new Exception(json.optString("message",
                            json.optString("error", "Não foi possível gerar o Pix.")));
                }
                String purchaseId = json.optString("purchase_id", "");
                String qrCode = json.optString("qr_code", "");
                String qrBase64 = json.optString("qr_code_base64", "");
                if (purchaseId.isEmpty() || qrCode.isEmpty() || qrBase64.isEmpty()) {
                    throw new Exception("O Mercado Pago não retornou os dados do Pix.");
                }
                prefs.edit().putString(KEY_PURCHASE_ID, purchaseId).apply();
                main.post(() -> {
                    requestInProgress = false;
                    if (!activity.isFinishing()) mostrarPix(qrCode, qrBase64);
                });
            } catch (Exception e) {
                android.util.Log.e("MonetizationManager", "Falha ao gerar Pix", e);
                main.post(() -> {
                    requestInProgress = false;
                    if (!activity.isFinishing()) new AlertDialog.Builder(activity)
                            .setTitle("Não foi possível gerar o Pix")
                            .setMessage(safeMessage(e))
                            .setPositiveButton("Tentar novamente", (d, w) -> solicitarPix())
                            .setNegativeButton("Fechar", null).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarPix(String qrCode, String qrBase64) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView instructions = new TextView(activity);
        instructions.setText("Escaneie o QR Code no aplicativo do seu banco ou copie o código Pix abaixo. Após pagar, toque em “JÁ PAGUEI — VERIFICAR”.");
        layout.addView(instructions);
        ImageView qrImage = new ImageView(activity);
        qrImage.setAdjustViewBounds(true);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            byte[] bytes = Base64.decode(qrBase64, Base64.DEFAULT);
            qrImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            int size = (int) (240 * activity.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(size, size);
            imageParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            imageParams.topMargin = pad / 2;
            imageParams.bottomMargin = pad / 2;
            layout.addView(qrImage, imageParams);
        } catch (Exception e) {
            android.util.Log.e("MonetizationManager", "Falha ao mostrar QR Pix", e);
        }
        TextView copyLabel = new TextView(activity);
        copyLabel.setText("Pix Copia e Cola:");
        layout.addView(copyLabel);
        TextView code = new TextView(activity);
        code.setText(qrCode);
        code.setTextIsSelectable(true);
        code.setMaxLines(4);
        code.setEllipsize(android.text.TextUtils.TruncateAt.END);
        code.setPadding(0, pad / 3, 0, pad / 3);
        layout.addView(code);
        new AlertDialog.Builder(activity)
                .setTitle("Pix de R$ 19,90")
                .setView(layout)
                .setNegativeButton("FECHAR", null)
                .setPositiveButton("COPIAR PIX", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Pix IR Remote BR Premium", qrCode));
                        toast("Código Pix copiado. Cole no aplicativo do seu banco.");
                    }
                })
                .setNeutralButton("JÁ PAGUEI — VERIFICAR", (d, w) -> verificarPagamento(true))
                .show();
    }

    private String getDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    /** O servidor é a autoridade: vincula o Premium a um único aparelho e controla a troca em 30 dias. */
    private void verificarAcessoDispositivo() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(SERVER + "/api/device-access");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + getFirebaseIdToken());
            connection.setRequestProperty("X-Device-ID", getDeviceId());
            try (OutputStream out = connection.getOutputStream()) {
                out.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            JSONObject json = new JSONObject(readResponse(connection, status));
            if (status >= 200 && status < 300 && json.optBoolean("allowed", false)) return;
            String error = json.optString("error", "");
            if ("device_transfer_cooldown".equals(error)) {
                int days = json.optInt("days_remaining", 30);
                throw new Exception("Este Premium já está vinculado a outro celular. A troca poderá ser feita em aproximadamente "
                        + days + " dia(s), respeitando o intervalo de 30 dias.");
            }
            if ("not_entitled".equals(error)) {
                throw new Exception("Não encontramos uma compra Premium aprovada nesta conta Google.");
            }
            throw new Exception("Não foi possível autorizar este celular. Tente novamente mais tarde.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Envia ao servidor um pedido de troca antecipada para análise do administrador. */
    public void solicitarTrocaDispositivo() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            toast("Entre com sua conta Google antes de solicitar a troca.");
            return;
        }
        EditText reasonInput = new EditText(activity);
        reasonInput.setHint("Explique por que precisa trocar de aparelho (opcional)");
        reasonInput.setMinLines(2);
        reasonInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int pad = (int) (18 * activity.getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad / 2, pad, 0);
        TextView note = new TextView(activity);
        note.setText("A solicitação será enviada ao ADM. A troca só será feita após aprovação. A regra normal de 30 dias continua valendo.");
        form.addView(note);
        form.addView(reasonInput, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(activity)
                .setTitle("Solicitar troca de aparelho")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("ENVIAR SOLICITAÇÃO", (dialog, which) -> {
                    String reason = reasonInput.getText().toString().trim();
                    enviarSolicitacaoTroca(reason);
                }).show();
    }

    private void enviarSolicitacaoTroca(String reason) {
        if (requestInProgress) {
            toast("Aguarde a solicitação atual terminar.");
            return;
        }
        requestInProgress = true;
        toast("Enviando solicitação ao ADM...");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER + "/api/device-transfer-request");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + getFirebaseIdToken());
                connection.setRequestProperty("X-Device-ID", getDeviceId());
                JSONObject body = new JSONObject();
                body.put("device_id", getDeviceId());
                body.put("reason", reason == null ? "" : reason);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                JSONObject json = new JSONObject(readResponse(connection, status));
                if (status < 200 || status >= 300) {
                    String error = json.optString("error", "request_unavailable");
                    String message;
                    if ("not_entitled".equals(error)) message = "Não encontramos o Premium ativo nesta conta. Entre com a conta correta e restaure a compra.";
                    else if ("device_already_registered".equals(error)) message = "Este aparelho já está vinculado ao Premium.";
                    else if ("invalid_device_id".equals(error)) message = "Não foi possível identificar este aparelho. Feche e abra o aplicativo e tente novamente.";
                    else if ("authentication_required".equals(error)) message = "Sua sessão expirou. Entre novamente com Google.";
                    else message = "Não foi possível enviar o pedido agora (HTTP " + status + "). Tente novamente mais tarde.";
                    throw new Exception(message);
                }
                main.post(() -> {
                    requestInProgress = false;
                    if (activity.isFinishing()) return;
                    String state = json.optString("status", "pending");
                    if ("pending".equals(state)) {
                        new AlertDialog.Builder(activity)
                                .setTitle("Solicitação enviada")
                                .setMessage("Seu pedido de troca de aparelho foi enviado ao ADM. O aparelho atual continuará vinculado até a aprovação.")
                                .setPositiveButton("OK", null).show();
                    } else {
                        toast("Solicitação processada.");
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("MonetizationManager", "Falha ao solicitar troca de aparelho", e);
                main.post(() -> {
                    requestInProgress = false;
                    if (!activity.isFinishing()) new AlertDialog.Builder(activity)
                            .setTitle("Não foi possível solicitar a troca")
                            .setMessage(safeMessage(e))
                            .setPositiveButton("OK", null).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String getFirebaseIdToken() throws Exception {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new Exception("Entre com sua conta Google antes de continuar.");
        return Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS).getToken();
    }

    public void restaurarCompra() {
        if (requestInProgress) {
            toast("Aguarde a consulta atual terminar.");
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            toast("Entre com Google para restaurar o Premium em qualquer aparelho.");
            return;
        }
        requestInProgress = true;
        toast("Consultando sua conta Premium...");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER + "/api/premium-account");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Authorization", "Bearer " + getFirebaseIdToken());
                int status = connection.getResponseCode();
                String response = readResponse(connection, status);
                JSONObject json = new JSONObject(response);
                if (status < 200 || status >= 300) {
                    throw new Exception(json.optString("message", json.optString("error", "Não foi possível consultar a conta.")));
                }
                boolean active = json.optBoolean("premium_unlocked", false);
                if (active) verificarAcessoDispositivo();
                main.post(() -> {
                    requestInProgress = false;
                    if (activity.isFinishing()) return;
                    if (active) {
                        boolean changed = !isPremium();
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser == null) return;
                        prefs.edit().putBoolean(KEY_PREMIUM, true)
                                .putString(KEY_PREMIUM_UID, currentUser.getUid())
                                .putLong(KEY_PREMIUM_UPDATED, System.currentTimeMillis()).apply();
                        toast("⭐ Premium restaurado nesta conta Google!");
                        if (changed && onPremiumChanged != null) onPremiumChanged.run();
                    } else {
                        // O servidor é a fonte de verdade: remova o cache que não foi confirmado para esta conta.
                        prefs.edit().putBoolean(KEY_PREMIUM, false).remove(KEY_PREMIUM_UID).apply();
                        // A conta ainda não tem entitlement; consulta a compra local autenticada, se existir.
                        if (prefs.getString(KEY_PURCHASE_ID, "").isEmpty()) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("Premium não encontrado")
                                    .setMessage("Não há compra aprovada vinculada a esta conta Google ainda.")
                                    .setPositiveButton("OK", null).show();
                        } else {
                            verificarPagamento(true);
                        }
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("MonetizationManager", "Falha ao restaurar Premium", e);
                main.post(() -> {
                    requestInProgress = false;
                    if (!activity.isFinishing()) new AlertDialog.Builder(activity)
                            .setTitle("Não foi possível restaurar")
                            .setMessage(safeMessage(e))
                            .setPositiveButton("OK", null).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void criarCheckout() {
        if (requestInProgress) {
            toast("Aguarde, estamos processando sua solicitação.");
            return;
        }
        requestInProgress = true;
        toast("Preparando pagamento seguro...");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER + "/api/create-preference");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + getFirebaseIdToken());
                try (OutputStream out = connection.getOutputStream()) {
                    out.write("{}".getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                String response = readResponse(connection, status);
                JSONObject json = new JSONObject(response);
                if (status < 200 || status >= 300) {
                    String error = json.optString("message", json.optString("error", "Não foi possível criar o pagamento."));
                    throw new Exception(error);
                }
                String checkout = json.optString("checkout_url", "");
                String purchaseId = json.optString("purchase_id", "");
                if (!checkout.startsWith("https://") || purchaseId.isEmpty()) {
                    throw new Exception("O servidor não retornou um checkout válido.");
                }
                prefs.edit().putString(KEY_PURCHASE_ID, purchaseId).apply();
                main.post(() -> {
                    requestInProgress = false;
                    if (activity.isFinishing()) return;
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(checkout)));
                    } catch (Exception e) {
                        toast("Não foi possível abrir o Mercado Pago neste aparelho.");
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("MonetizationManager", "Falha ao criar checkout", e);
                main.post(() -> {
                    requestInProgress = false;
                    if (!activity.isFinishing()) new AlertDialog.Builder(activity)
                            .setTitle("Pagamento indisponível")
                            .setMessage("Não foi possível iniciar o pagamento. Verifique a internet e tente novamente.\n\nDetalhe: " + safeMessage(e))
                            .setPositiveButton("OK", null).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void verificarPagamento(boolean mostrarResultado) {
        String purchaseId = prefs.getString(KEY_PURCHASE_ID, "");
        if (purchaseId.isEmpty()) {
            if (mostrarResultado) toast("Nenhuma compra iniciada neste aparelho. Toque em Pagar R$ 19,90 primeiro.");
            return;
        }
        if (requestInProgress) {
            if (mostrarResultado) toast("Aguarde a consulta atual terminar.");
            return;
        }
        requestInProgress = true;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER + "/api/payment-status?purchase_id=" + purchaseId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Authorization", "Bearer " + getFirebaseIdToken());
                int status = connection.getResponseCode();
                String response = readResponse(connection, status);
                JSONObject json = new JSONObject(response);
                if (status < 200 || status >= 300) {
                    throw new Exception(json.optString("error", "Falha ao consultar pagamento."));
                }
                boolean approved = json.optBoolean("premium_unlocked", false)
                        && "approved".equalsIgnoreCase(json.optString("status", ""));
                if (approved) verificarAcessoDispositivo();
                main.post(() -> {
                    requestInProgress = false;
                    if (activity.isFinishing()) return;
                    if (approved) {
                        boolean changed = !isPremium();
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser == null) return;
                        prefs.edit().putBoolean(KEY_PREMIUM, true)
                                .putString(KEY_PREMIUM_UID, currentUser.getUid())
                                .putLong(KEY_PREMIUM_UPDATED, System.currentTimeMillis()).apply();
                        toast("⭐ Pagamento confirmado! Premium ativado.");
                        if (changed && onPremiumChanged != null) onPremiumChanged.run();
                    } else if (mostrarResultado) {
                        String state = json.optString("status", "not_paid");
                        String message = "pending".equalsIgnoreCase(state)
                                ? "O pagamento ainda está pendente. Aguarde a confirmação do Mercado Pago e tente novamente."
                                : "Ainda não encontramos um pagamento aprovado para esta compra. Se você já pagou, aguarde um instante e verifique novamente.";
                        new AlertDialog.Builder(activity).setTitle("Pagamento não confirmado")
                                .setMessage(message).setPositiveButton("Tentar novamente", (d, w) -> verificarPagamento(true))
                                .setNegativeButton("Fechar", null).show();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("MonetizationManager", "Falha ao verificar pagamento", e);
                main.post(() -> {
                    requestInProgress = false;
                    if (mostrarResultado && !activity.isFinishing()) toast("Não foi possível verificar agora. Confira a conexão e tente novamente.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String readResponse(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "{}";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return "erro de conexão";
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private void toast(String message) {
        main.post(() -> {
            if (!activity.isFinishing()) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        });
    }
}
