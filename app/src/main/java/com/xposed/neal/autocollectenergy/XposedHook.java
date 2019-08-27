package com.xposed.neal.autocollectenergy;


import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyUtils.ANT_FOREST_URL_HOME;
import static com.xposed.neal.autocollectenergy.AliMobileAutoCollectEnergyUtils.ANT_FOREST_URL_PREFIX;

public class XposedHook implements IXposedHookLoadPackage {

    private static boolean notFirst = false;
    private static String TAG = "XposedHookZFB";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 限制hook支付宝的主进程
        if ("com.eg.android.AlipayGphone".equals(lpparam.packageName) && "com.eg.android.AlipayGphone".equals(lpparam.processName)) {
            hookRpcCall(lpparam);
        }
    }

    private void hookRpcCall(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new ApplicationAttachMethodHook());
        } catch (Exception e2) {
            // Log.i(TAG, "hookRpcCall err:" + Log.getStackTraceString(e2));
        }
    }

    private static class ApplicationAttachMethodHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            if (notFirst)
                return;
            Context context = ((Context) param.args[0]);
            final ClassLoader loader = context.getClassLoader();
            AliMobileAutoCollectEnergyUtils.loader = loader;
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            if (packageInfo.versionCode >= 135) {
                Class clazz = loader.loadClass("com.alipay.mobile.nebulacore.ui.H5FragmentManager");
                if (clazz != null) {
                    Class<?> h5FragmentClazz = loader.loadClass("com.alipay.mobile.nebulacore.ui.H5Fragment");
                    if (h5FragmentClazz != null) {
                        XposedHelpers.findAndHookMethod(clazz, "pushFragment", h5FragmentClazz,
                                boolean.class, Bundle.class, boolean.class, boolean.class, new XC_MethodHook() {

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        super.afterHookedMethod(param);
                                        AliMobileAutoCollectEnergyUtils.curH5FragmentRef = new WeakReference<>(param.args[0]);
                                    }

                                });
                    }
                }

                clazz = loader.loadClass("com.alipay.mobile.nebulacore.web.H5WebView");
                if (clazz != null) {
                    XposedHelpers.findAndHookMethod(clazz, "loadUrl", String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
//                            String url = (String) param.args[0];
//                            if (url.startsWith("http://") || url.startsWith("https://")) {
//                                if (ANT_FOREST_URL_HOME.equals(url)) {
//                                    AliMobileAutoCollectEnergyUtils.startAutoCollect();
//                                } else if (!url.startsWith(ANT_FOREST_URL_PREFIX)) {
//                                    AliMobileAutoCollectEnergyUtils.stopAutoCollect();
//                                }
//                            }
                        }
                    });
                }

                clazz = loader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil");
                if (clazz != null) {
                    notFirst = true;
                    Class<?> h5PageClazz = loader.loadClass("com.alipay.mobile.h5container.api.H5Page");
                    Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
                    if (h5PageClazz != null && jsonClazz != null) {
                        XposedHelpers.findAndHookMethod(clazz, "rpcCall", String.class, String.class, String.class,
                                boolean.class, jsonClazz, String.class, boolean.class, h5PageClazz,
                                int.class, String.class, boolean.class, int.class, String.class, new XC_MethodHook() {

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        super.afterHookedMethod(param);
                                        Object[] args = param.args;
                                        // Log.i(TAG, "params:" + args[0]
//                                                + "," + args[1]
//                                                + "," + args[2]
//                                                + "," + args[3]
//                                                + "," + args[4]
//                                                + "," + args[5]
//                                                + "," + args[6]
//                                                + "," + "H5"
//                                                + "," + args[7]
//                                                + "," + args[8]
//                                                + "," + args[9]
//                                                + "," + args[10]
//                                                + "," + args[11]
//                                        );
                                        if ("alipay.antmember.forest.h5.queryNextAction".equals(args[0])) {
                                            if (!((String) args[1]).contains("userId")) {
                                                AliMobileAutoCollectEnergyUtils.startAutoCollect();
                                            }
                                        }
                                    }
                                });
                    }
                }
            }
        }
    }
}
