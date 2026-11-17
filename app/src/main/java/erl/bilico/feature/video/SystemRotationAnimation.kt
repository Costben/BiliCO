package erl.bilico.feature.video

import android.os.Build

/**
 * 转屏动画由谁来演。
 *
 * 播放页的横屏转场是照着 ColorOS 白名单转屏做的：竖屏窗口里先把画面转 ±90° 铺满，
 * 动画落稳后再申请横屏，靠窗口上的 `ROTATION_ANIMATION_SEAMLESS` 让系统在新方向上
 * 直接接着合成当前画面。这两个前提只有 ColorOS 系成立——它对白名单外的应用本来就不播
 * 自己的转屏动画，`SEAMLESS` 也真的生效，所以屏幕上只会看到应用这一套。
 *
 * 其他 ROM（HyperOS 等）不认这个属性，会在应用自绘的同时再播一遍系统转屏，
 * 用户看到的就是「转一次屏放了两个动画」。这类设备退回系统动画：
 * 应用只把画面摆到位并立刻申请朝向，转场整个交给系统。
 */
internal object SystemRotationAnimation {

    /** OPPO / OnePlus / realme 共用同一套 oplus 转屏实现（ColorOS / OxygenOS / realme UI）。 */
    private val COLOROS_VENDORS = setOf("oppo", "oneplus", "realme")

    /**
     * true：应用自绘转屏；false：回落给系统转屏动画。
     *
     * 必须声明在 [COLOROS_VENDORS] 之后：object 的属性按声明顺序初始化，
     * 放到前面就会在集合还是 null 的时候去判断厂商，直接 NullPointerException。
     */
    val appDriven: Boolean = run {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        manufacturer in COLOROS_VENDORS || brand in COLOROS_VENDORS
    }
}
