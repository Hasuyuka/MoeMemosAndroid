package me.mudkip.moememos.util

import java.time.Instant

/**
 * 附件的创建时间：保证**同一条备忘内严格递增**。
 *
 * 一次选十几张图时，循环里的 `Instant.now()` 常常落在同一毫秒里。日期相同的话，
 * 排序就分不出先后，图片显示顺序会变成数据库返回的任意顺序——用户看到的就是
 * 「选了一堆图，顺序全乱了」。
 *
 * 做法很简单：只要不比上一张新，就比它晚 1 毫秒。既不用改已有数据，
 * 也让「按日期排序」这一条规则足以还原用户点选的先后。
 */
fun nextResourceDate(previous: Instant?, now: Instant): Instant =
    if (previous == null || now.isAfter(previous)) now else previous.plusMillis(1)