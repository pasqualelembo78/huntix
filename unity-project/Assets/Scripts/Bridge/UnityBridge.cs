using UnityEngine;
using System.Collections;
using System.Collections.Generic;

namespace Huntix.Bridge
{
    public static class UnityBridge
    {
        private static AndroidJavaObject _bridge;

        public static void Init()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            {
                var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
                _bridge = activity.Call<AndroidJavaObject>("getBridge");
            }
            #endif
        }

        public static void SaveData(string json)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("saveData", json);
            #endif
        }

        public static string LoadData()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            return _bridge?.Call<string>("loadData");
            #endif
            return "{}";
        }

        public static void ShowToast(string message)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("showToast", message);
            #endif
        }

        public static void OpenAndroidActivity(string className)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("openAndroidActivity", className);
            #endif
        }

        public static string GetSharedPreference(string key)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            return _bridge?.Call<string>("getSharedPreference", key);
            #endif
            return "";
        }

        public static void SetSharedPreference(string key, string value)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("setSharedPreference", key, value);
            #endif
        }

        public static void SendMessageToAndroid(string eventName, string jsonData)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            var jc = new AndroidJavaClass("com.intelligame.huntix.Bridge");
            jc.CallStatic("onUnityMessage", eventName, jsonData);
            #endif
        }

        public static void QuitToAndroid()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            OpenAndroidActivity("com.intelligame.huntix.ui.OutdoorWorldActivity");
            #endif
        }
    }
}