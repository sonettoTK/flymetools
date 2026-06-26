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
    private var skipInstallScanEnabled = false
    private var enableNativeInstallerEnabled = false

    override fun handle(lpparam: XC_LoadPackage.LoadPackageParam, packageName: String) {
        if (lpparam.packageName != "com.android.packageinstaller") return

        skipInstallScanEnabled = XposedPrefs.isFeatureEnabled(lpparam, packageName, "skip_install_scan")
        enableNativeInstallerEnabled = XposedPrefs.isFeatureEnabled(lpparam, packageName, "enable_native_installer")
        autoInstallEnabled = XposedPrefs.isFeatureEnabled(lpparam, packageName, "auto_install")

        try {
            if (skipInstallScanEnabled) {
                hookStartInstallScan(lpparam)
                hookSafetyCheck(lpparam)
            }
            if (enableNativeInstallerEnabled) {
                hookNativeInstaller(lpparam)
            }
            if (skipInstallScanEnabled || enableNativeInstallerEnabled) {
                Logger.i(HOOK_NAME, "Hooks installed successfully")
            }
        } catch (e: Throwable) {
            Logger.e(HOOK_NAME, "Hook failed", e)
        }
    }

    private fun hookStartInstallScan(lpparam: XC_LoadPackage.LoadPackageParam) {
        val clazz = XposedHelpers.findClass(ACTIVITY_CLASS, lpparam.classLoader)

        XposedHelpers.findAndHookMethod(
            clazz,
            "startInstallScan",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val thisObject = param.thisObject

                    XposedHelpers.setBooleanField(thisObject, "mIsVirusCheckFinish", true)
                    XposedHelpers.setBooleanField(thisObject, "mIsVirusCheckResultSafe", true)
                    XposedHelpers.setBooleanField(thisObject, "receivedMzStoreInfo", true)
                    XposedHelpers.setIntField(thisObject, "isDisposaled", 0)
                    XposedHelpers.setBooleanField(thisObject, "isBlackApp", false)

                    val mzStoreAppInfo = XposedHelpers.getObjectField(thisObject, "mzStoreAppInfo")
                    if (mzStoreAppInfo != null) {
                        XposedHelpers.setBooleanField(mzStoreAppInfo, "querySuccess", false)
                        XposedHelpers.setBooleanField(mzStoreAppInfo, "showConfirm", false)
                        XposedHelpers.setBooleanField(mzStoreAppInfo, "icpStatus", false)
                        XposedHelpers.setBooleanField(mzStoreAppInfo, "isDisposalApp", false)
                        XposedHelpers.setBooleanField(mzStoreAppInfo, "isBlackApp", false)
                    }

                    Logger.d(HOOK_NAME, "Skipped install scan")

                    if (autoInstallEnabled) {
                        XposedHelpers.callMethod(thisObject, "doInstallFlyme")
                        Logger.d(HOOK_NAME, "Auto install triggered")
                    } else {
                        XposedHelpers.callMethod(thisObject, "updateViewForNewState", 3)
                        Logger.d(HOOK_NAME, "Showing install confirm UI")
                    }

                    param.result = null
                }
            }
        )

        Logger.i(HOOK_NAME, "Hooked startInstallScan")
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

    private fun hookSafetyCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        val clazz = XposedHelpers.findClass(ACTIVITY_CLASS, lpparam.classLoader)

        // setVirusCheckTime -> 直接让 handler 发消息跳转
        XposedHelpers.findAndHookMethod(
            clazz,
            "setVirusCheckTime",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam) {
                    val mHandler = XposedHelpers.getObjectField(param.thisObject, "mHandler")
                    XposedHelpers.callMethod(mHandler, "sendEmptyMessage", 5)
                }
            }
        )
        Logger.i(HOOK_NAME, "Hooked setVirusCheckTime")

        // replaceOrInstall 前清空 mAppInfo
        XposedHelpers.findAndHookMethod(
            clazz,
            "replaceOrInstall",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setObjectField(param.thisObject, "mAppInfo", null)
                }
            }
        )
        Logger.i(HOOK_NAME, "Hooked replaceOrInstall -> clear mAppInfo")
    }
}