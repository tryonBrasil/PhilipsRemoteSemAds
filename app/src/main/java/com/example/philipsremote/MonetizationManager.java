package com.example.philipsremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.*;
import com.android.billingclient.api.*;
import com.google.android.gms.ads.*;
import java.util.*;

public class MonetizationManager {
    public static final String PREMIUM_PRODUCT_ID = "ir_remote_premium";
    private static final String PREFS = "remote_prefs";
    private static final String KEY_PREMIUM = "premium_unlocked";
    private static final String KEY_PREMIUM_UPDATED = "premium_unlocked_at";
    private static final int FREE_SAVED_LIMIT = 3;

    // IDs DE TESTE. Substituir pelos IDs reais antes da publicação comercial.
    public static final String ADMOB_APP_ID_TEST = "ca-app-pub-3940256099942544~3347511713";
    private static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/9214589741";

    private final Activity activity;
    private final android.content.SharedPreferences prefs;
    private final Runnable onPremiumChanged;
    private BillingClient billingClient;
    private ProductDetails premiumProduct;
    private boolean billingReady = false;
    private boolean consultaEmAndamento = false;
    private boolean compraEmAndamento = false;
    private boolean reconexaoAgendada = false;
    private final java.util.List<AdView> banners = new java.util.ArrayList<>();

    public MonetizationManager(Activity activity, Runnable onPremiumChanged) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        this.onPremiumChanged = onPremiumChanged;
        MobileAds.initialize(activity, status -> {});
        iniciarBilling();
    }

    public boolean isPremium() {
        return prefs.getBoolean(KEY_PREMIUM, false);
    }

    public boolean podeSalvarControle(int quantidadeAtual) {
        return isPremium() || Math.max(0, quantidadeAtual) < FREE_SAVED_LIMIT;
    }

    public int limiteGratuito() {
        return FREE_SAVED_LIMIT;
    }

    public String resumoLimite(int quantidadeAtual) {
        return isPremium()
                ? quantidadeAtual + " controles • ilimitado"
                : quantidadeAtual + "/" + FREE_SAVED_LIMIT + " controles salvos";
    }

    public void addBanner(LinearLayout root) {
        if (root == null || isPremium()) return;

        FrameLayout box = new FrameLayout(activity);
        box.setPadding(0, 6, 0, 6);

        AdView ad = new AdView(activity);
        ad.setAdUnitId(BANNER_TEST_ID);

        int width = Math.max(1, (int) (activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density));
        ad.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width));

        box.addView(ad, new FrameLayout.LayoutParams(-1, -2));
        root.addView(box, new LinearLayout.LayoutParams(-1, -2));
        banners.add(ad);
        ad.loadAd(new AdRequest.Builder().build());
    }

    /** Indica se há opções de privacidade de anúncios disponíveis nesta versão. */
    public boolean precisaOpcoesPrivacidade() {
        return false;
    }

    /** Ponto de entrada seguro para a tela de privacidade de anúncios. */
    public void mostrarOpcoesPrivacidade() {
        new AlertDialog.Builder(activity)
                .setTitle("Privacidade dos anúncios")
                .setMessage("Os anúncios desta versão usam configurações padrão. Não há opções adicionais de privacidade disponíveis no momento.")
                .setPositiveButton("OK", null)
                .show();
    }

    public void showPremiumDialog() {
        if (isPremium()) {
            new AlertDialog.Builder(activity)
                    .setTitle("⭐ IR Remote BR Premium")
                    .setMessage("Premium ativo neste aparelho.\n\n"
                            + "✓ Sem anúncios\n"
                            + "✓ Controles salvos ilimitados\n"
                            + "✓ Mais códigos e testes personalizados\n"
                            + "✓ Recursos avançados para controles universais\n\n"
                            + "Sua compra é vinculada à conta do Google Play.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("VERIFICAR COMPRA", (d, w) -> restaurarCompra())
                    .show();
            return;
        }

        String preco = premiumProduct != null
                && premiumProduct.getOneTimePurchaseOfferDetails() != null
                ? premiumProduct.getOneTimePurchaseOfferDetails().getFormattedPrice()
                : "preço no Google Play";

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("⭐ IR Remote BR Premium")
                .setMessage("Desbloqueie o aplicativo completo.\n\n"
                        + "✓ Sem anúncios\n"
                        + "✓ Controles salvos ilimitados\n"
                        + "✓ Mais códigos e testes personalizados\n"
                        + "✓ Recursos avançados para controles universais\n"
                        + "✓ Compra única, sem mensalidade\n\n"
                        + "Preço: " + preco)
                .setNegativeButton("AGORA NÃO", null)
                .setNeutralButton("RESTAURAR COMPRA", null)
                .setPositiveButton("DESBLOQUEAR", (d, w) -> comprar())
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(x -> restaurarCompra()));
        dialog.show();
    }

    public void restaurarCompra() {
        if (!billingReady || billingClient == null) {
            Toast.makeText(activity,
                    "Google Play ainda está conectando. Tente novamente em alguns segundos.",
                    Toast.LENGTH_SHORT).show();
            iniciarBilling();
            return;
        }
        consultarCompras(true);
    }

    private void iniciarBilling() {
        if (billingClient != null && billingClient.isReady()) {
            billingReady = true;
            carregarProduto();
            consultarCompras(false);
            return;
        }

        billingReady = false;
        premiumProduct = null;

        billingClient = BillingClient.newBuilder(activity)
                .setListener((billingResult, purchases) -> {
                    int code = billingResult.getResponseCode();

                    if (code == BillingClient.BillingResponseCode.OK) {
                        compraEmAndamento = false;
                        processarCompras(purchases);
                    } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                        compraEmAndamento = false;
                    } else {
                        compraEmAndamento = false;
                        Toast.makeText(activity,
                                "Não foi possível concluir a compra agora.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                billingReady = result.getResponseCode() == BillingClient.BillingResponseCode.OK;

                if (billingReady) {
                    reconexaoAgendada = false;
                    carregarProduto();
                    consultarCompras(false);
                } else {
                    agendarReconexao();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                billingReady = false;
                agendarReconexao();
            }
        });
    }

    private void agendarReconexao() {
        if (reconexaoAgendada || activity.isFinishing()) return;

        reconexaoAgendada = true;
        activity.getWindow().getDecorView().postDelayed(() -> {
            reconexaoAgendada = false;
            if (!billingReady) iniciarBilling();
        }, 1500L);
    }

    private void carregarProduto() {
        if (!billingReady || billingClient == null || !billingClient.isReady()) return;

        QueryProductDetailsParams.Product p =
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build();

        QueryProductDetailsParams params =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(p))
                        .build();

        billingClient.queryProductDetailsAsync(params, (result, details) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && details != null
                    && !details.getProductDetailsList().isEmpty()) {
                premiumProduct = details.getProductDetailsList().get(0);
            } else {
                premiumProduct = null;
                android.util.Log.w("MonetizationManager",
                        "Produto Premium indisponível: " + result.getDebugMessage());
            }
        });
    }

    private void consultarCompras(boolean mostrarResultado) {
        if (!billingReady || billingClient == null || !billingClient.isReady()
                || consultaEmAndamento) return;

        consultaEmAndamento = true;

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (result, purchases) -> {
            consultaEmAndamento = false;

            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                boolean encontrado = false;

                if (purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (!purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) continue;

                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            encontrado = true;
                            ativarPremium(purchase);
                        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING
                                && mostrarResultado) {
                            Toast.makeText(activity,
                                    "Compra Premium pendente. O acesso será liberado após a confirmação do Google Play.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }

                // Só altera o estado local quando o Google Play respondeu com sucesso.
                // Assim, uma falha temporária de conexão não remove um Premium já adquirido.
                if (!encontrado && isPremium()) {
                    definirPremium(false, false);
                }

                if (mostrarResultado && !encontrado) {
                    Toast.makeText(activity,
                            "Nenhuma compra Premium ativa foi encontrada nesta conta do Google Play.",
                            Toast.LENGTH_LONG).show();
                }
            } else if (mostrarResultado) {
                Toast.makeText(activity,
                        "Não foi possível consultar suas compras agora.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void processarCompras(List<Purchase> purchases) {
        if (purchases == null) return;

        for (Purchase purchase : purchases) {
            if (!purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) continue;

            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                ativarPremium(purchase);
            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                Toast.makeText(activity,
                        "Compra pendente. Aguarde a confirmação do Google Play.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void ativarPremium(Purchase purchase) {
        definirPremium(true, true);

        if (!purchase.isAcknowledged()
                && billingClient != null
                && billingClient.isReady()) {

            billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build(),
                    result -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            android.util.Log.w("MonetizationManager",
                                    "Falha ao reconhecer compra Premium: "
                                            + result.getDebugMessage());
                        }
                    });
        }
    }

    private void definirPremium(boolean ativo, boolean mostrarToast) {
        boolean mudou = isPremium() != ativo;

        prefs.edit()
                .putBoolean(KEY_PREMIUM, ativo)
                .putLong(KEY_PREMIUM_UPDATED, System.currentTimeMillis())
                .apply();

        if (mudou || mostrarToast) {
            activity.runOnUiThread(() -> {
                if (mostrarToast) {
                    Toast.makeText(activity,
                            ativo
                                    ? "⭐ Premium ativado/restaurado!"
                                    : "Premium não está ativo nesta conta.",
                            Toast.LENGTH_LONG).show();
                }

                if (mudou && onPremiumChanged != null) {
                    onPremiumChanged.run();
                }
            });
        }
    }

    private void comprar() {
        if (compraEmAndamento) {
            Toast.makeText(activity,
                    "A compra já está sendo processada.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!billingReady || billingClient == null || premiumProduct == null) {
            Toast.makeText(activity,
                    "Produto Premium ainda não disponível. Verifique sua conexão com o Google Play.",
                    Toast.LENGTH_LONG).show();
            iniciarBilling();
            return;
        }

        ProductDetails.OneTimePurchaseOfferDetails offer =
                premiumProduct.getOneTimePurchaseOfferDetails();

        if (offer == null) {
            Toast.makeText(activity,
                    "Oferta Premium indisponível no momento.",
                    Toast.LENGTH_LONG).show();
            carregarProduto();
            return;
        }

        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(premiumProduct)
                        .setOfferToken(offer.getOfferToken())
                        .build();

        BillingFlowParams flow =
                BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(productParams))
                        .build();

        compraEmAndamento = true;
        BillingResult result = billingClient.launchBillingFlow(activity, flow);

        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            compraEmAndamento = false;
            Toast.makeText(activity,
                    "O Google Play não iniciou a compra: " + result.getDebugMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
    /** Libera todos os banners e encerra a conexão do Google Play. */
    public void destroy() {
        for (AdView ad : new java.util.ArrayList<>(banners)) {
            try { ad.destroy(); } catch (Exception ignored) {}
        }
        banners.clear();
        if (billingClient != null) {
            try { billingClient.endConnection(); } catch (Exception ignored) {}
            billingClient = null;
        }
        billingReady = false;
        consultaEmAndamento = false;
        compraEmAndamento = false;
    }
