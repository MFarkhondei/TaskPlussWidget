package com.taskpluss.widget

import java.util.Calendar
import java.util.TimeZone

/**
 * تبدیل میلادی → شمسی مطابق الگوریتم Code.gs (nowToJalaliString_)
 * خروجی نمونه: 1405/05/12 19:15
 */
object JalaliUtils {

    fun nowTehranJalaliString(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val hh = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        val j = gregorianToJalali(gy, gm, gd)
        return "${j.first}/${pad(j.second)}/${pad(j.third)} $hh:$mm"
    }

    private fun pad(n: Int): String = n.toString().padStart(2, '0')

    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a - div(a, b) * b

    private fun jalCal(jy: Int): Triple<Int, Int, Int> {
        val breaks = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
        )
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        var jm: Int
        var i = 1
        while (i < breaks.size) {
            jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Triple(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return Triple(gy, gm, gd)
    }

    private fun d2j(jdn: Int): Triple<Int, Int, Int> {
        var gy = d2g(jdn).first
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.third)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return Triple(jy, 1 + div(k, 31), mod(k, 31) + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += if (jalCal(jy).first == 0) 366 else 365
        }
        return Triple(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        return d2j(g2d(gy, gm, gd))
    }
}
