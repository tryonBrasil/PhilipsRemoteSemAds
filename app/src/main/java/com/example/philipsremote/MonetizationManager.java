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

    // IDs de TESTE. Substituir pelos IDs reais antes da monetizacao em producao.
    public static final String ADMOB_APP_ID_TEST = "ca-app-pub-3940256099942544~3347511713";
    private static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/9214589741";

    private final Activity activity;
    private final android.content.SharedPreferences prefs;
    private final Runnable onPremiumChanged;
    private BillingClient billingClient;
    private ProductDetails premiumProduct;
    private boolean billingReady = false;
    private boolean consultaEmAndamento = false;

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
        return isPremium() || quantidadeAtual < 3;
    }

    public int limiteGratuito() { return 3; }

    public void addBanner(LinearLayout root) {
        if (root == null || isPremium()) return;
        FrameLayout box = new FrameLayout(activity);
        box.setPadding(0, 6, 0, 6);
        AdView ad = new AdView(activity);
        ad.setAdUnitId(BANNER_TEST_ID);
        int width = Math.max(1, (int)(activity.getResources().getDisplayMetrics().widthPixels /
                activity.getResources().getDisplayMetrics().density));
        ad.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width));
        box.addView(ad, new FrameLayout.LayoutParams(-1, -2));
        root.addView(box, new LinearLayout.LayoutParams(-1, -2));
        ad.loadAd(new AdRequest.Builder().build());
    }

    public void showPremiumDialog() {
        if (isPremium()) {
            new AlertDialog.Builder(activity)
                    .setTitle("⭐ IR Remote BR Premium")
                    .setMessage("Premium ativo nesta conta/app.\n\n"
                            + "✓ Sem anuncios\n"
                            + "✓ Controles salvos ilimitados\n"
                            + "✓ Mais codigos e testes personalizados\n"
                            + "✓ Recursos avancados para controles universais\n\n"
                            + "Sua compra fica vinculada a sua conta do Google Play.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("VERIFICAR COMPRA", (d,w) -> restaurarCompra())
                    .show();
            return;
        }

        String preco = premiumProduct != null && premiumProduct.getOneTimePurchaseOfferDetails() != null
                ? premiumProduct.getOneTimePurchaseOfferDetails().getFormattedPrice() : "preco no Google Play";

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("⭐ IR Remote BR Premium")
                .setMessage("Desbloqueie o aplicativo completo.\n\n"
                        + "✓ Sem anuncios\n"
                        + "✓ Controles salvos ilimitados\n"
                        + "✓ Mais codigos e testes personalizados\n"
                        + "✓ Recursos avancados para controles universais\n"
                        + "✓ Compra unica, sem mensalidade\n\n"
                        + "Preco: " + preco)
                .setNegativeButton("AGORA NAO", null)
                .setNeutralButton("RESTAURAR COMPRA", null)
                .setPositiveButton("DESBLOQUEAR", (d,w) -> comprar())
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(x -> restaurarCompra()));
        dialog.show();
    }

    public void restaurarCompra() {
        if (!billingReady || billingClient == null) {
            Toast.makeText(activity, "Google Play ainda esta conectando. Tente novamente.", Toast.LENGTH_SHORT).show();
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
        billingClient = BillingClient.newBuilder(activity)
                .setListener((billingResult, purchases) -> {
                    int code = billingResult.getResponseCode();
                    if (code == BillingClient.BillingResponseCode.OK) {
                        processarCompras(purchases);
                    } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                        // Cancelamento normal: nao exibir erro.
                    } else {
                        Toast.makeText(activity, "Nao foi possivel concluir a compra agora.", Toast.LENGTH_SHORT).show();
                    }
                })
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                billingReady = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                if (billingReady) {
                    carregarProduto();
                    consultarCompras(false);
                }
            }
            @Override public void onBillingServiceDisconnected() {
                billingReady = false;
            }
        });
    }

    private void carregarProduto() {
        if (!billingReady) return;
        QueryProductDetailsParams.Product p = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(p))
                .build();

        billingClient.queryProductDetailsAsync(params, (result, details) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && details != null && !details.getProductDetailsList().isEmpty()) {
                premiumProduct = details.getProductDetailsList().get(0);
            }
        });
    }

    private void consultarCompras(boolean mostrarResultado) {
        if (!billingReady || consultaEmAndamento) return;
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
                        if (purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) {
                            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                encontrado = true;
                                ativarPremium(purchase);
                            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING && mostrarResultado) {
                                Toast.makeText(activity, "Compra Premium pendente. O acesso sera liberado apos a confirmacao do Google Play.", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }

                // So removemos o sinal local quando o Google Play respondeu com sucesso.
                // Assim uma falha temporaria de rede nao bloqueia um Premium ja adquirido.
                if (!encontrado && isPremium()) {
                    definirPremium(false, false);
                }

                if (mostrarResultado && !encontrado) {
                    Toast.makeText(activity,
                            "Nenhuma compra Premium ativa encontrada nesta conta do Google Play.",
                            Toast.LENGTH_LONG).show();
                }
            } else if (mostrarResultado) {
                Toast.makeText(activity,
                        "Nao foi possivel consultar suas compras agora.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void processarCompras(List<Purchase> purchases) {
        if (purchases == null) return;
        for (Purchase purchase : purchases) {
            if (purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    ativarPremium(purchase);
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    Toast.makeText(activity, "Compra pendente. Aguarde a confirmacao do Google Play.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void ativarPremium(Purchase purchase) {
        definirPremium(true, true);

        if (!purchase.isAcknowledged() && billingClient != null && billingClient.isReady()) {
            billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build(),
                    result -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            android.util.Log.w("MonetizationManager", "Falha ao reconhecer compra Premium: " + result.getDebugMessage());
                        }
                    });
        }
    }

    private void definirPremium(boolean ativo, boolean mostrarToast) {
        boolean mudou = isPremium() != ativo;
        prefs.edit().putBoolean(KEY_PREMIUM, ativo).apply();
        if (mudou || mostrarToast) {
            activity.runOnUiThread(() -> {
                if (mostrarToast) {
                    Toast.makeText(activity,
                            ativo ? "⭐ Premium ativado/restaurado!" : "Premium nao esta ativo nesta conta.",
                            Toast.LENGTH_LONG).show();
                }
                if (mudou && onPremiumChanged != null) onPremiumChanged.run();
            });
        }
    }

    private void comprar() {
        if (!billingReady || billingClient == null || premiumProduct == null) {
            Toast.makeText(activity,
                    "Produto Premium ainda nao disponivel. Verifique o Google Play Console.",
                    Toast.LENGTH_LONG).show();
            iniciarBilling();
            return;
        }

        ProductDetails.OneTimePurchaseOfferDetails offer =
                premiumProduct.getOneTimePurchaseOfferDetails();
        if (offer == null) {
            Toast.makeText(activity, "Oferta Premium indisponivel no momento.", Toast.LENGTH_LONG).show();
            return;
        }

        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(premiumProduct)
                        .setOfferToken(offer.getOfferToken())
                        .build();

        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();

        billingClient.launchBillingFlow(activity, flow);
    }
}
