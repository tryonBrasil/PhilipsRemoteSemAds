package com.example.philipsremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import com.android.billingclient.api.*;
import com.google.android.gms.ads.*;
import java.util.*;

public class MonetizationManager {
    public static final String PREMIUM_PRODUCT_ID = "ir_remote_premium";
    private static final String PREFS = "remote_prefs";
    private static final String KEY_PREMIUM = "premium_unlocked";

    // IDs de TESTE do Google. Substitua pelos seus IDs antes de publicar a versão monetizada.
    public static final String ADMOB_APP_ID_TEST = "ca-app-pub-3940256099942544~3347511713";
    private static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/9214589741";

    private final Activity activity;
    private final android.content.SharedPreferences prefs;
    private final Runnable onPremiumChanged;
    private BillingClient billingClient;
    private ProductDetails premiumProduct;

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
        new AlertDialog.Builder(activity)
                .setTitle("⭐ IR Remote BR Premium")
                .setMessage("Desbloqueie o aplicativo completo.\n\n"
                        + "✓ Sem anúncios\n"
                        + "✓ Controles salvos ilimitados\n"
                        + "✓ Mais códigos e testes personalizados\n"
                        + "✓ Recursos avançados para controles universais\n"
                        + "✓ Compra única, sem mensalidade\n\n"
                        + "Preço: " + preco)
                .setNegativeButton("AGORA NÃO", null)
                .setPositiveButton("DESBLOQUEAR", (d,w) -> comprar())
                .show();
    }

    private void iniciarBilling() {
        billingClient = BillingClient.newBuilder(activity)
                .setListener((billingResult, purchases) -> processarCompras(purchases))
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    carregarProduto();
                    consultarCompras();
                }
            }
            @Override public void onBillingServiceDisconnected() {}
        });
    }

    private void carregarProduto() {
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

    private void consultarCompras() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (result, purchases) -> processarCompras(purchases));
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
        prefs.edit().putBoolean(KEY_PREMIUM, true).apply();
        if (!purchase.isAcknowledged()) {
            billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build(),
                    result -> {});
        }
        activity.runOnUiThread(() -> {
            Toast.makeText(activity, "⭐ Premium ativado!", Toast.LENGTH_LONG).show();
            if (onPremiumChanged != null) onPremiumChanged.run();
        });
    }

    private void comprar() {
        if (billingClient == null || premiumProduct == null) {
            Toast.makeText(activity,
                    "Produto Premium ainda não disponível. Configure o produto no Google Play Console.",
                    Toast.LENGTH_LONG).show();
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
