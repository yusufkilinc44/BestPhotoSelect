package com.bestphotoselect.domain

/**
 * Fotoğrafları çekim zamanı + algısal hash benzerliğine göre kümeler.
 * Saf Kotlin: Android bağımlılığı yoktur, birim testleri JVM'de koşar.
 */
object PhotoGrouper {

    data class Input(val id: Long, val timeMs: Long, val hash: Long)

    /**
     * @param timeWindowMs iki fotoğrafın aynı grupta değerlendirilebilmesi için
     *   aralarındaki azami çekim zamanı farkı; 0 veya negatifse zaman koşulu uygulanmaz
     *   (bu durumda karşılaştırma, sıralı listede en fazla [MAX_NEIGHBOR_SCAN] komşuyla sınırlanır).
     * @param maxHammingDistance 64-bit dHash için eşik; tipik olarak 8..12.
     * @return her biri en az 2 eleman içeren, id listelerinden oluşan gruplar.
     */
    fun group(
        photos: List<Input>,
        timeWindowMs: Long,
        maxHammingDistance: Int
    ): List<List<Long>> {
        if (photos.size < 2) return emptyList()
        val sorted = photos.sortedBy { it.timeMs }
        val uf = UnionFind(sorted.size)

        for (i in sorted.indices) {
            var j = i + 1
            var scanned = 0
            while (j < sorted.size) {
                if (timeWindowMs > 0) {
                    if (sorted[j].timeMs - sorted[i].timeMs > timeWindowMs) break
                } else if (scanned >= MAX_NEIGHBOR_SCAN) {
                    break
                }
                if (DHash.hammingDistance(sorted[i].hash, sorted[j].hash) <= maxHammingDistance) {
                    uf.union(i, j)
                }
                j++
                scanned++
            }
        }

        val clusters = HashMap<Int, MutableList<Long>>()
        for (i in sorted.indices) {
            clusters.getOrPut(uf.find(i)) { mutableListOf() }.add(sorted[i].id)
        }
        return clusters.values.filter { it.size >= 2 }
    }

    private const val MAX_NEIGHBOR_SCAN = 25

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = IntArray(size)

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            when {
                rank[ra] < rank[rb] -> parent[ra] = rb
                rank[ra] > rank[rb] -> parent[rb] = ra
                else -> {
                    parent[rb] = ra
                    rank[ra]++
                }
            }
        }
    }
}
