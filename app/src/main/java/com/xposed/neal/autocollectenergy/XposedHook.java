package com.xposed.neal.autocollectenergy;


import android.app.Activity;
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
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedHook implements IXposedHookLoadPackage {

    private static boolean notFirst = false;
    private static String TAG = "XposedHookZFB";
    private static String antForestUrl = "https://60000002.h5app.alipay.com/www/home.html";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 限制hook支付宝的主进程
        if ("com.eg.android.AlipayGphone".equals(lpparam.packageName) && "com.eg.android.AlipayGphone".equals(lpparam.processName)) {
            hookRpcCall(lpparam);
        }
    }

    private void hookSecurity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class loadClass;
            loadClass = lpparam.classLoader.loadClass("android.util.Base64");
            if (loadClass != null) {
                XposedHelpers.findAndHookMethod(loadClass, "decode", String.class, Integer.TYPE, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                    }
                });
            }
            loadClass = lpparam.classLoader.loadClass("android.app.Dialog");
            if (loadClass != null) {
                XposedHelpers.findAndHookMethod(loadClass, "show", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        try {
                            throw new NullPointerException();
                        } catch (Exception e) {
                        }
                    }
                });
            }
            loadClass = lpparam.classLoader.loadClass("com.alipay.mobile.base.security.CI");
            if (loadClass != null) {
                XposedHelpers.findAndHookMethod(loadClass, "a", loadClass, Activity.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return null;
                    }
                });
                XposedHelpers.findAndHookMethod(loadClass, "a", String.class, String.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        param.setResult(null);
                    }
                });
            }
        } catch (Throwable e) {
            Log.i(TAG, "hookSecurity err:" + Log.getStackTraceString(e));
        }
    }

    private void hookRpcCall(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new ApplicationAttachMethodHook());
        } catch (Exception e2) {
            Log.i(TAG, "hookRpcCall err:" + Log.getStackTraceString(e2));
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
            if ("10.1.70.8308".equals(packageInfo.versionName)) { // "10.1.70.8308版本"
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

                clazz = loader.loadClass("com.alipay.mobile.nebulacore.ui.H5Activity");
                if (clazz != null) {
                    XposedHelpers.findAndHookMethod(clazz, "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            AliMobileAutoCollectEnergyUtils.h5ActivityRef = new WeakReference<>((Activity) param.thisObject);
                        }
                    });
                    XposedHelpers.findAndHookMethod(clazz, "onPause", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            AliMobileAutoCollectEnergyUtils.h5ActivityRef = new WeakReference<>(null);
                        }
                    });
                }


                clazz = loader.loadClass("com.alipay.mobile.nebulacore.web.H5WebView");
                if (clazz != null) {
                    XposedHelpers.findAndHookMethod(clazz, "loadUrl", String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String url = (String) param.args[0];
                            if (url != null && url.contains("h5app.alipay.com")) {
                                Log.i(TAG, "url:" + url);
                            }
                            super.beforeHookedMethod(param);
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
                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        super.beforeHookedMethod(param);
                                        Object[] args = param.args;
                                        Log.i(TAG, "params:" + args[0]
                                                + "," + args[1]
                                                + "," + args[2]
                                                + "," + args[3]
                                                + "," + args[4]
                                                + "," + args[5]
                                                + "," + args[6]
                                                + "," + "H5"
                                                + "," + args[7]
                                                + "," + args[8]
                                                + "," + args[9]
                                                + "," + args[10]
                                                + "," + args[11]
                                        );
                                    }

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        super.afterHookedMethod(param);
                                        Object resp = param.getResult();
                                        if (resp != null) {
                                            Method method = resp.getClass().getMethod("getResponse", new Class<?>[]{});
                                            String response = (String) method.invoke(resp, new Object[]{});
                                            Log.i(TAG, response);

                                            if (AliMobileAutoCollectEnergyUtils.enterAntForest(response)) {
                                                AliMobileAutoCollectEnergyUtils.autoGetCanCollectUserIdList();
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
