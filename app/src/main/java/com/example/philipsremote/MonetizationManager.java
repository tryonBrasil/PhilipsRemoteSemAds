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

    // IDs de TESTE do Google. Troque pelos IDs reais antes da publicação monetizada.
    public static final String ADMOB_APP_ID_TEST = "ca-app-pub-3940256099942544~3347511713";
    private static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/9214589741";

    private final Activity activity;
    private final android.content.SharedPreferences prefs;
    private final Runnable onPremiumChanged;
    private BillingClient billingClient;
    private ProductDetails premiumProduct;
    private boolean billingReady = false;

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

    public void addBanner(LinearLayout root) {
        if (isPremium()) return;
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
        String preco = premiumProduct != null && premiumProduct.getOneTimePurchaseOfferDetails() != null
                ? premiumProduct.getOneTimePurchaseOfferDetails().getFormattedPrice() : "preço no Google Play";

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
                .setPositiveButton("DESBLOQUEAR", (d,w) -> comprar())
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(x -> restaurarCompra()));
        dialog.show();
    }

    public void restaurarCompra() {
        if (!billingReady || billingClient == null) {
            Toast.makeText(activity, "Google Play ainda está conectando. Tente novamente.", Toast.LENGTH_SHORT).show();
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
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        processarCompras(purchases);
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
        if (!billingReady) return;
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                boolean encontrado = false;
                if (purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                                && purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) {
                            encontrado = true;
                            ativarPremium(purchase);
                        }
                    }
                }
                if (mostrarResultado && !encontrado) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Nenhuma compra Premium encontrada nesta conta do Google Play.",
                            Toast.LENGTH_LONG).show());
                }
            } else if (mostrarResultado) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Não foi possível consultar suas compras agora.",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void processarCompras(List<Purchase> purchases) {
        if (purchases == null) return;
        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                    && purchase.getProducts().contains(PREMIUM_PRODUCT_ID)) {
                ativarPremium(purchase);
            }
        }
    }

    private void ativarPremium(Purchase purchase) {
        boolean mudou = !isPremium();
        prefs.edit().putBoolean(KEY_PREMIUM, true).apply();

        if (!purchase.isAcknowledged() && billingClient != null && billingClient.isReady()) {
            billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build(),
                    result -> {});
        }

        if (mudou) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "⭐ Premium ativado/restaurado!", Toast.LENGTH_LONG).show();
                if (onPremiumChanged != null) onPremiumChanged.run();
            });
        }
    }

    private void comprar() {
        if (!billingReady || billingClient == null || premiumProduct == null) {
            Toast.makeText(activity,
                    "Produto Premium ainda não disponível. Verifique o Google Play Console.",
                    Toast.LENGTH_LONG).show();
            iniciarBilling();
            return;
        }

        ProductDetails.OneTimePurchaseOfferDetails offer =
                premiumProduct.getOneTimePurchaseOfferDetails();
        if (offer == null) {
            Toast.makeText(activity, "Oferta Premium indisponível no momento.", Toast.LENGTH_LONG).show();
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
