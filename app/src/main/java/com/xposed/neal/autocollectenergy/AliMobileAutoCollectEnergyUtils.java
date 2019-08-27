package com.xposed.neal.autocollectenergy;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

class AliMobileAutoCollectEnergyUtils {

    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private static String TAG = "XposedHookZFB";
    private static Boolean RUNNING = false;
    private static ArrayList<String> friendsCanCollectIdList = new ArrayList<>();
    private static ArrayList<Boolean> friendsCanCollectList = new ArrayList<>();
    private static ArrayList<Boolean> friendsCanHelpCollectList = new ArrayList<>();
    private static ArrayList<Boolean> friendsCanCollectAfterList = new ArrayList<>();
    private static long mostRecentCollectTime = Long.MAX_VALUE;
    static ClassLoader loader;
    private static ArrayList<String> friendsWhiteListId = new ArrayList<String>() ;
    private static Integer totalEnergy = 0;
    private static Integer totalHelpEnergy = 0;
    private static Integer pageCount = 0;
    private static Object curH5PageImpl;
    static WeakReference<Object> curH5FragmentRef;
    public static String ANT_FOREST_URL_HOME = "https://60000002.h5app.alipay.com/www/home.html";
    public static String ANT_FOREST_URL_PREFIX = "https://60000002.h5app.alipay.com";
    private final static Handler handler = new Hand();

    static class Hand extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                String url = (String) msg.obj;
                // Log.i(TAG, "webView url:" + url);
                if (ANT_FOREST_URL_HOME.equals(url)) {
                    startAutoCollect();
                } else {
                    RUNNING = false;
                }
            } else if (msg.what == 0) {
                getUrl();
            }
        }
    }

    private static void reset() {
        handler.removeCallbacksAndMessages(null);
        friendsCanCollectIdList.clear();
        friendsCanCollectList.clear();
        friendsCanHelpCollectList.clear();
        friendsCanCollectAfterList.clear();
        totalEnergy = 0;
        totalHelpEnergy = 0;
        pageCount = 0;
        mostRecentCollectTime = Long.MAX_VALUE;
    }

    /**
     * 自动获取有能量的好友信息
     */
    static void startAutoCollect() {
        if (!RUNNING) {
            RUNNING = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    reset();
                    rpcCall_CanCollectEnergy(-1);
                    friendsRankList();
                }
            }).start();
        }
    }

    static void stopAutoCollect() {
        RUNNING = false;
    }

    static void getUrl() {
        Object curH5Fragment = curH5FragmentRef.get();
        if (curH5Fragment != null) {
            try {
                Method getActivity = curH5Fragment.getClass().getMethod("getActivity");
                getActivity.setAccessible(true);
                Activity activity = (Activity) getActivity.invoke(curH5Fragment);
                Field aF = curH5Fragment.getClass().getDeclaredField("b");
                aF.setAccessible(true);
                final Object webView = aF.get(curH5Fragment);
                final Method getUrl = webView.getClass().getDeclaredMethod("getUrl");
                getUrl.setAccessible(true);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Message msg = handler.obtainMessage(1);
                            msg.obj = getUrl.invoke(webView);
                            msg.sendToTarget();
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                // Log.i(TAG, "getRpcCallMethod err: " + Log.getStackTraceString(e));
                final Message msg = handler.obtainMessage(1);
                msg.obj = null;
                msg.sendToTarget();
            }
        } else {
            final Message msg = handler.obtainMessage(1);
            msg.obj = null;
            msg.sendToTarget();
        }
    }

    /**
     * 自动获取能收取的能量ID
     *
     * @param response
     */
    private static void autoGetCanCollectBubbleIdList(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray jsonArray = jsonObject.optJSONArray("bubbles");
            if (jsonArray != null && jsonArray.length() > 0) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject1 = jsonArray.getJSONObject(i);
                    String userId = jsonObject1.optString("userId");
                    String collectStatus = jsonObject1.optString("collectStatus");
                    if (!friendsWhiteListId.contains(userId)) {
                        if ("AVAILABLE".equals(collectStatus)) {
                            rpcCall_CollectEnergy(jsonObject1.optString("userId"), jsonObject1.optLong("id"));
                        }
                    }
                    if ("INSUFFICIENT".equals(collectStatus) && jsonObject1.optBoolean("canHelpCollect")) {
                        rpcCall_HelpCollectEnergy(jsonObject1.optString("userId"), jsonObject1.optLong("id"));
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private static boolean isBubbles(String response) {
        return (!TextUtils.isEmpty(response) && response.contains("collectStatus"));
    }

    /**
     * 解析好友信息
     *
     * @param response
     * @return
     */
    private static boolean parseFriendRankPageDataResponse(String response) {
        try {
            JSONObject jObject = new JSONObject(response);
            JSONArray optJSONArray = jObject.optJSONArray("friendRanking");
            if (optJSONArray == null || optJSONArray.length() == 0) {
                return false;
            } else {
                for (int i = 0; i < optJSONArray.length(); i++) {
                    JSONObject jsonObject = optJSONArray.getJSONObject(i);
                    boolean canCollect = jsonObject.optBoolean("canCollectEnergy");
                    boolean canHelpCollect = jsonObject.optBoolean("canHelpCollect");
                    String userId = jsonObject.optString("userId");
                    if (!friendsWhiteListId.contains(userId)) {
                        long collectLaterTime = jsonObject.optLong("canCollectLaterTime");
                        if (collectLaterTime != -1 && mostRecentCollectTime > collectLaterTime) {
                            mostRecentCollectTime = collectLaterTime;
                        }
                        if ((canCollect || canHelpCollect) && !friendsCanCollectIdList.contains(userId)) {
                            friendsCanCollectIdList.add(userId);
                            friendsCanCollectList.add(canCollect);
                            friendsCanHelpCollectList.add(canHelpCollect);
                            friendsCanCollectAfterList.add(collectLaterTime != -1);
                        }
                    }
                }
            }
            return jObject.optBoolean("hasMore");
        } catch (Exception e) {
        }
        return false;
    }

    private static void friendsRankList() {
        String response = rpcCall_FriendRankList();
        while (parseFriendRankPageDataResponse(response)) {
            response = rpcCall_FriendRankList();
        }
        showToast("开始收取能量...");
        if (friendsCanCollectIdList.size() > 0) {
            showToast("开始获取每个好友能够偷取的能量信息...");
            // Log.i(TAG, "" + friendsCanCollectIdList);
            // Log.i(TAG, "" + friendsCanCollectList);
            // Log.i(TAG, "" + friendsCanHelpCollectList);
            // Log.i(TAG, "" + friendsCanCollectAfterList);
            for (int i = 0; i < friendsCanCollectIdList.size(); i++) {
                rpcCall_CanCollectEnergy(i);
            }
            // Log.i(TAG, "collect energy finish refresh webview...");
        }
        showToast("一共收取了" + totalEnergy + "g能量" + ",帮助收取了:" + totalHelpEnergy + "g能量");
        if (mostRecentCollectTime != Long.MAX_VALUE) {
            Date dt = new Date();
            dt.setTime(mostRecentCollectTime);
            String nextTime = simpleDateFormat.format(dt);
            showToast("下一次可收取的时间:" + nextTime);
            handler.sendEmptyMessageAtTime(0, Long.max(System.currentTimeMillis(), mostRecentCollectTime) + 1000);
        } else {
            RUNNING = false;
        }
    }

    /**
     * 获取分页好友信息命令
     */
    private static String rpcCall_FriendRankList() {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("av", "5");
            json.put("ct", "android");
            json.put("pageSize", pageCount * 20);
            json.put("startPoint", "" + (pageCount * 20 + 1));
            pageCount++;
            jsonArray.put(json);
            showToast("开始获取可以收取第" + pageCount + "页好友信息的能量...");
            // Log.i(TAG, "call friendranklist params:" + jsonArray);

            return rpcCall("alipay.antmember.forest.h5.queryEnergyRanking", jsonArray);
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_FriendRankList err: " + Log.getStackTraceString(e));
            return "";
        }
    }

    private static String rpcCall(String funcName, JSONArray jsonArray) {
        try {
            Method rpcCallMethod = getRpcCallMethod();
            Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
            Object obj = jsonClazz.newInstance();

            Object resp = rpcCallMethod.invoke(null, funcName, jsonArray.toString(),
                    "", true, obj, null, false, curH5PageImpl, 0, "", false, -1, "");
            Method method = resp.getClass().getMethod("getResponse", new Class<?>[]{});
            return (String) method.invoke(resp, new Object[]{});
        } catch (Exception e) {

        }
        RUNNING = false;
        return "";
    }

    /**
     * 获取指定用户可以收取的能量信息
     *
     * @param index
     */
    private static void rpcCall_CanCollectEnergy(int index) {
        try {
            if (index < 0) {
                JSONArray jsonArray = new JSONArray();
                JSONObject json = new JSONObject();
                json.put("version", "20181220");
                jsonArray.put(json);
                // Log.i(TAG, "call cancollect energy params:" + jsonArray);

                final String response = rpcCall("alipay.antmember.forest.h5.queryNextAction", jsonArray);
                if (AliMobileAutoCollectEnergyUtils.isBubbles(response)) {
                    AliMobileAutoCollectEnergyUtils.autoGetCanCollectBubbleIdList(response);
                }
            } else {
                JSONArray jsonArray = new JSONArray();
                JSONObject json = new JSONObject();
                String userId = friendsCanCollectIdList.get(index);
                String flags;
                if (friendsCanCollectList.get(index)) {
                    flags = "T";
                } else {
                    flags = "F";
                }
                if (friendsCanHelpCollectList.get(index)) {
                    flags = flags + ",T";
                } else {
                    flags = flags + ",F";
                }
                if (friendsCanCollectAfterList.get(index)) {
                    flags = flags + ",T";
                } else {
                    flags = flags + ",F";
                }
                json.put("canRobFlags", flags);
                json.put("userId", userId);
                json.put("version", "20181220");
                jsonArray.put(json);
                // Log.i(TAG, "call cancollect energy params:" + jsonArray);

                final String response = rpcCall("alipay.antmember.forest.h5.queryNextAction", jsonArray);
                if (AliMobileAutoCollectEnergyUtils.isBubbles(response)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            AliMobileAutoCollectEnergyUtils.autoGetCanCollectBubbleIdList(response);
                        }
                    }).start();
                }

                JSONArray pkArray = new JSONArray();
                JSONObject pkObject = new JSONObject();
                pkObject.put("pkType", "Week");
                pkObject.put("pkUser", userId);
                pkArray.put(pkObject);
                rpcCall("alipay.antmember.forest.h5.queryPKRecord", pkArray);

                JSONArray jArray = new JSONArray();
                JSONObject jObject = new JSONObject();
                jObject.put("pageSize", 10);
                jObject.put("startIndex", 0);
                jObject.put("userId", userId);
                jArray.put(jObject);
                rpcCall("alipay.antmember.forest.h5.pageQueryDynamics", jArray);
            }
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_CanCollectEnergy err: " + Log.getStackTraceString(e));
        }
    }

    private static void rpcCall_HelpCollectEnergy(String userId, long bubbleId) {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONArray bubbleAry = new JSONArray();
            bubbleAry.put(bubbleId);
            JSONObject json = new JSONObject();
            json.put("targetUserId", userId);
            json.put("bubbleIds", bubbleAry);
            jsonArray.put(json);
            // Log.i(TAG, "call cancollect energy params:" + jsonArray);

            Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
            String response = rpcCall("alipay.antmember.forest.h5.forFriendCollectEnergy", jsonArray);
            AliMobileAutoCollectEnergyUtils.parseHelpCollectEnergyResponse(response);
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_CanCollectEnergy err: " + Log.getStackTraceString(e));
        }
    }

    /**
     * 收取能量命令
     *
     * @param userId
     * @param bubbleId
     */
    private static void rpcCall_CollectEnergy(String userId, long bubbleId) {
        try {
            JSONArray jsonArray = new JSONArray();
            JSONArray bubbleAry = new JSONArray();
            bubbleAry.put(bubbleId);
            JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("bubbleIds", bubbleAry);
            jsonArray.put(json);
            // Log.i(TAG, "call cancollect energy params:" + jsonArray);

            String response = rpcCall("alipay.antmember.forest.h5.collectEnergy", jsonArray);
            AliMobileAutoCollectEnergyUtils.parseCollectEnergyResponse(response);
        } catch (Exception e) {
            // Log.i(TAG, "rpcCall_CanCollectEnergy err: " + Log.getStackTraceString(e));
        }
    }

    private static Method getRpcCallMethod() {
        Object curH5Fragment = curH5FragmentRef.get();
        if (curH5Fragment != null) {
            try {
                Field aF = curH5Fragment.getClass().getDeclaredField("a");
                aF.setAccessible(true);
                Object viewHolder = aF.get(curH5Fragment);
                Field hF = viewHolder.getClass().getDeclaredField("h");
                hF.setAccessible(true);
                curH5PageImpl = hF.get(viewHolder);
                Class<?> h5PageClazz = loader.loadClass("com.alipay.mobile.h5container.api.H5Page");
                Class<?> jsonClazz = loader.loadClass("com.alibaba.fastjson.JSONObject");
                Class<?> rpcClazz = loader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil");
                if (curH5PageImpl != null) {
                    Method callM = rpcClazz.getMethod("rpcCall", String.class, String.class, String.class,
                            boolean.class, jsonClazz, String.class, boolean.class, h5PageClazz,
                            int.class, String.class, boolean.class, int.class, String.class);
                    return callM;
                }
            } catch (Exception e) {
                // Log.i(TAG, "getRpcCallMethod err: " + Log.getStackTraceString(e));
            }
        }
        return null;
    }

    private static void parseHelpCollectEnergyResponse(String response) {
        if (!TextUtils.isEmpty(response) && response.contains("failedBubbleIds")) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                JSONArray jsonArray = jsonObject.optJSONArray("bubbles");
                for (int i = 0; i < jsonArray.length(); i++) {
                    totalHelpEnergy += jsonArray.getJSONObject(i).optInt("collectedEnergy");
                }
            } catch (Exception e) {
            }
        }
    }

    private static void parseCollectEnergyResponse(String response) {
        if (!TextUtils.isEmpty(response) && response.contains("failedBubbleIds")) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                JSONArray jsonArray = jsonObject.optJSONArray("bubbles");
                for (int i = 0; i < jsonArray.length(); i++) {
                    totalEnergy += jsonArray.getJSONObject(i).optInt("collectedEnergy");
                }
            } catch (Exception e) {
            }
        }
    }

    private static Activity getActivity() {
        final Object h5Fragment = curH5FragmentRef.get();
        if (h5Fragment != null) {
            try {
                Method getActivity = h5Fragment.getClass().getMethod("getActivity");
                getActivity.setAccessible(true);
                return (Activity) getActivity.invoke(h5Fragment);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return null;
    }

    private static void showToast(final String str) {
        final Activity activity = getActivity();
        if (activity != null) {
            try {
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(activity, str, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                // Log.i(TAG, "showToast err: " + Log.getStackTraceString(e));
            }
        } else {
            RUNNING = false;
        }
    }
}
