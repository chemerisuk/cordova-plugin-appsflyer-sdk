package com.appsflyer.cordova.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.appsflyer.AppsFlyerProperties;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static by.chemerisuk.cordova.firebase.FirebaseMessagingPluginService.ACTION_FCM_TOKEN;
import static by.chemerisuk.cordova.firebase.FirebaseMessagingPluginService.EXTRA_FCM_TOKEN;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_CONVERSION_DATA;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_DEV_KEY;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_FAILURE;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_IS_DEBUG;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_ON_APP_OPEN_ATTRIBUTION;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_ON_ATTRIBUTION_FAILURE;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_ON_INSTALL_CONVERSION_DATA_LOADED;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_ON_INSTALL_CONVERSION_FAILURE;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_SENDER_ID;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.AF_SUCCESS;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.NO_DEVKEY_FOUND;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.NO_EVENT_NAME_FOUND;
import static com.appsflyer.cordova.plugin.AppsFlyerConstants.SUCCESS;

public class AppsFlyerPlugin extends CordovaPlugin {

	private CallbackContext mConversionListener = null;
	private LocalBroadcastManager broadcastManager;

	@Override
	protected void pluginInitialize() {
		broadcastManager = LocalBroadcastManager.getInstance(cordova.getActivity());
		broadcastManager.registerReceiver(tokenReceiver, new IntentFilter(ACTION_FCM_TOKEN));
	}

	@Override
	public void onDestroy() {
		broadcastManager.unregisterReceiver(tokenReceiver);
	}

	private BroadcastReceiver tokenReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String token = intent.getStringExtra(EXTRA_FCM_TOKEN);
			if (token != null) {
				Context appContext = cordova.getActivity().getApplicationContext();
				AppsFlyerLib.getInstance().updateServerUninstallToken(appContext, token);
			}
		}
	};

	/**
	 * Called when the activity receives a new intent.
	 */
	@Override
	public void onNewIntent(Intent intent) {
		cordova.getActivity().setIntent(intent);
		AppsFlyerLib.getInstance().sendDeepLinkData(cordova.getActivity());
	}

	@Override
	public boolean execute(final String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d("AppsFlyer", "Executing...");
		if("setCurrencyCode".equals(action))
		{
			return setCurrencyCode(args);
		}
		else if("setAppUserId".equals(action))
		{
			return setAppUserId(args, callbackContext);
		}
		else if("getAppsFlyerUID".equals(action))
		{
			return getAppsFlyerUID(callbackContext);
		}
		else if("initSdk".equals(action))
		{
			return initSdk(args,callbackContext);
		}
		else if ("trackEvent".equals(action)) {
			return trackEvent(args, callbackContext);
		}
		else if("updateServerUninstallToken".equals(action))
		{
			return updateServerUninstallToken(args, callbackContext);
		}

		return false;
	}



    private void trackAppLaunch(){
        Context c = this.cordova.getActivity().getApplicationContext();
        AppsFlyerLib.getInstance().trackEvent(c, null, null);
    }

	/**
	 *
	 * @param args
	 * @param callbackContext
     */
	private boolean initSdk(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
		final JSONObject options = args.getJSONObject(0);
		final String devKey = options.optString(AF_DEV_KEY, "").trim();
		final boolean isConversionData = options.optBoolean(AF_CONVERSION_DATA, false);
		final boolean isDebug = options.optBoolean(AF_IS_DEBUG, false);
		final String senderId = options.optString(AF_SENDER_ID, "").trim();

		if(devKey.equals("")){
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, NO_DEVKEY_FOUND));
		} else {
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					AppsFlyerProperties.getInstance().set(AppsFlyerProperties.LAUNCH_PROTECT_ENABLED, false);
					AppsFlyerLib instance = AppsFlyerLib.getInstance();

					if(!senderId.equals("")){
						instance.updateServerUninstallToken(cordova.getActivity().getApplicationContext(), senderId);
					}
					instance.setDebugLog(isDebug);

					trackAppLaunch();

					instance.startTracking(cordova.getActivity().getApplication(), devKey);

					if (isConversionData == true) {
						if(mConversionListener == null){
							mConversionListener = callbackContext;
						}

						registerConversionListener(instance);
						sendPluginNoResult(callbackContext);
					} else {
						callbackContext.success(SUCCESS);
					}
				}
			});
		}

		return true;
	}

	private void registerConversionListener(AppsFlyerLib instance){
		instance.registerConversionListener(cordova.getActivity().getApplicationContext(), new AppsFlyerConversionListener(){

			@Override
			public void onAppOpenAttribution(Map<String, String> attributionData) {
				handleSuccess(AF_ON_APP_OPEN_ATTRIBUTION, attributionData);
			}

			@Override
			public void onAttributionFailure(String errorMessage) {
				handleError(AF_ON_ATTRIBUTION_FAILURE, errorMessage);
			}

			@Override
			public void onConversionDataSuccess(Map<String, Object> conversionData) {
				handleSuccess(AF_ON_INSTALL_CONVERSION_DATA_LOADED, conversionData);
			}

			@Override
			public void onConversionDataFail(String errorMessage) {
				handleError(AF_ON_INSTALL_CONVERSION_FAILURE, errorMessage);
			}


			private void handleError(String eventType, String errorMessage){

				try {
					JSONObject obj = new JSONObject();

					obj.put("status", AF_FAILURE);
					obj.put("type", eventType);
					obj.put("data", errorMessage);

					sendEvent(obj);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			private void handleSuccess(String eventType, Map eventData){
				try {
					JSONObject obj = new JSONObject();

					obj.put("status", AF_SUCCESS);
					obj.put("type", eventType);
					obj.put("data", new JSONObject(eventData));

					sendEvent(obj);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			private void sendEvent(JSONObject params) {

				final String jsonStr = params.toString();


				if (mConversionListener != null) {
					PluginResult result = new PluginResult(PluginResult.Status.OK, jsonStr);
					result.setKeepCallback(false);

					mConversionListener.sendPluginResult(result);
					mConversionListener = null;
				}
			}
		});
	}

	private boolean trackEvent(JSONArray parameters, final CallbackContext callbackContext) {
		String eventName;
		Map<String, Object> eventValues = null;
		try{
			eventName = parameters.getString(0);

			if(parameters.length() >1 && !parameters.get(1).equals(null)){
				JSONObject jsonEventValues = parameters.getJSONObject(1);
				eventValues = jsonToMap(jsonEventValues.toString());
			}
		}
		catch (JSONException e){
			e.printStackTrace();
			return true;
		}

		if(eventName == null || eventName.trim().length()==0){
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, NO_EVENT_NAME_FOUND));
			return true;
		}

		Context c = this.cordova.getActivity().getApplicationContext();
		AppsFlyerLib.getInstance().trackEvent(c, eventName, eventValues);

		return true;
	}

	private boolean setCurrencyCode(JSONArray parameters){

		String currencyId=null;
		try
		{
			currencyId = parameters.getString(0);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
			return true; //TODO error
		}
		if(currencyId == null || currencyId.length()==0)
		{
			return true; //TODO error
		}
		AppsFlyerLib.getInstance().setCurrencyCode(currencyId);

		return true;
	}

	private boolean setAppUserId(JSONArray parameters, CallbackContext callbackContext){

		try
		{
			String customeUserId = parameters.getString(0);
			if(customeUserId == null || customeUserId.length()==0){
				return true; //TODO error
			}
        	AppsFlyerLib.getInstance().setCustomerUserId(customeUserId);
        	PluginResult r = new PluginResult(PluginResult.Status.OK);
        	r.setKeepCallback(false);
        	callbackContext.sendPluginResult(r);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
			return true; //TODO error
		}

		return true;
	}

	private boolean getAppsFlyerUID(CallbackContext callbackContext){

    	String id = AppsFlyerLib.getInstance().getAppsFlyerUID(cordova.getActivity().getApplicationContext());
    	PluginResult r = new PluginResult(PluginResult.Status.OK, id);
    	r.setKeepCallback(false);
    	callbackContext.sendPluginResult(r);

		return true;
	}

	private static Map<String,Object> jsonToMap(String inputString){
		Map<String,Object> newMap = new HashMap<String, Object>();

		try {
			JSONObject jsonObject = new JSONObject(inputString);
			Iterator iterator = jsonObject.keys();
			while (iterator.hasNext()){
				String key = (String) iterator.next();
				newMap.put(key,jsonObject.getString(key));

			}
		} catch(JSONException e) {
			return null;
		}

		return newMap;
	}

	private boolean updateServerUninstallToken(JSONArray parameters, CallbackContext callbackContext) throws JSONException {
		String token = parameters.getString(0);
		Context c = this.cordova.getActivity().getApplicationContext();
		AppsFlyerLib.getInstance().updateServerUninstallToken(c, token);
		callbackContext.success(SUCCESS);
		return true;
	}

	private void sendPluginNoResult(CallbackContext callbackContext) {
		PluginResult pluginResult = new PluginResult(
				PluginResult.Status.NO_RESULT);
		pluginResult.setKeepCallback(true);
		callbackContext.sendPluginResult(pluginResult);
	}
}
