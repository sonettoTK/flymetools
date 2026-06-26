package com.karen.flymetool.hook.feature.packageinstaller

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.karen.flymetool.hook.base.FeatureHook
import com.karen.flymetool.hook.base.Logger
import com.karen.flymetool.hook.base.XposedPrefs

object PackageInstallerHook : FeatureHook {

    private const val ACTIVITY_CLASS = "com.android.packageinstaller.FlymePackageInstallerActivity"
    private const val HOOK_NAME = "PackageInstaller"

    private var autoInstallEnabled = false
    private var skipSafetyCheckEnabled = false
    private var enableNativeInstallerEnabled = false

    override fun handle(lpparam: XC_LoadPackage.LoadPackageParam, packageName: String) {
        if (lpparam.packageName != "com.android.packageinstaller") return

        skipSafetyCheckEnabled = XposedPrefs.isFeatureEnabled(lpparam, packageName, "skip_safety_check")
        enableNativeInstallerEnabled = XposedPrefs.isFeatureEnabled(lpparam, packageName, "enable_native_installer")
        autoInstallEnabled = XposedPrefs.isFeatureEnabled(lpparam, packageName, "auto_install")

        try {
            if (skipSafetyCheckEnabled) {
                hookSafetyCheck(lpparam)
            }
            if (enableNativeInstallerEnabled) {
                hookNativeInstaller(lpparam)
            }
            if (skipSafetyCheckEnabled || enableNativeInstallerEnabled) {
                Logger.i(HOOK_NAME, "Hooks installed successfully")
            }
        } catch (e: Throwable) {
            Logger.e(HOOK_NAME, "Hook failed", e)
        }
    }

    private fun hookSafetyCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            ACTIVITY_CLASS,
            lpparam.classLoader,
            "setVirusCheckTime",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam) {
                    val thisObject = param.thisObject
                    if (autoInstallEnabled) {
                        XposedHelpers.callMethod(thisObject, "doInstallFlyme")
                        Logger.d(HOOK_NAME, "Auto install triggered")
                    } else {
                        val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler")
                        XposedHelpers.callMethod(mHandler, "sendEmptyMessage", 5)
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            ACTIVITY_CLASS,
            lpparam.classLoader,
            "replaceOrInstall",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setObjectField(param.thisObject, "mAppInfo", null)
                }
            }
        )
    }

    private fun hookNativeInstaller(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "com.meizu.safe.security.utils.Utils",
            lpparam.classLoader,
            "isCtsRunning",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            }
        )
        Logger.i(HOOK_NAME, "Hooked isCtsRunning -> true (native installer)")
    }

}