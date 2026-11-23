package com.bestphotoselect.domain

import com.bestphotoselect.ui.tips.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsTest {

    private val ids = (1..10).map { "t$it" }
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `ilk acilista en onemli birkac ipucu art arda gelir`() {
        assertEquals(listOf("t1", "t2", "t3", "t4"), Tips.selectIds(ids, emptySet(), 0L, day))
    }

    @Test
    fun `gorulen ipucu tekrar gosterilmez`() {
        val seen = setOf("t1", "t2", "t3", "t4")
        val next = Tips.selectIds(ids, seen, day, 3 * day)
        assertTrue(next.isNotEmpty())
        assertTrue(next.none { it in seen })
        assertEquals("t5", next.first())
    }

    @Test
    fun `cok sik gosterilmez`() {
        val seen = setOf("t1", "t2", "t3", "t4")
        assertTrue(Tips.selectIds(ids, seen, day, day + 60_000).isEmpty())
    }

    @Test
    fun `bazen tek bazen iki ipucu`() {
        val counts = (4..8).map { n ->
            Tips.selectIds(ids, ids.take(n).toSet(), day, 3 * day).size
        }
        assertTrue(counts.all { it in 1..2 })
        assertTrue(1 in counts && 2 in counts)
    }

    @Test
    fun `hepsi gorulunce ipucu kalmaz`() {
        assertTrue(Tips.selectIds(ids, ids.toSet(), day, 10 * day).isEmpty())
    }

    @Test
    fun `ipucu kimlikleri benzersiz`() {
        val all = Tips.ALL.map { it.id }
        assertEquals(all.size, all.toSet().size)
    }
}
