package ru.cultureguide.kids.content

import ru.cultureguide.navigation.GeoPoint
import ru.cultureguide.navigation.distanceMeters
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Улицы и тротуары центра Троицка без дворов (строит `app/tools/build_streets.py`
 * в `assets/kids/streets.json`). По ним прямо на телефоне, без интернета, строится
 * путь «от меня до точки»: к первой точке прогулки и заново, если свернули.
 *
 * @param lat широты узлов
 * @param lon долготы узлов
 * @param edgeA первый узел каждого отрезка
 * @param edgeB второй узел каждого отрезка
 * @param edgeFactor во сколько раз путь по отрезку «дороже» его длины (дорожки скверов чуть дороже улиц)
 */
class StreetGraph(
    private val lat: DoubleArray,
    private val lon: DoubleArray,
    private val edgeA: IntArray,
    private val edgeB: IntArray,
    private val edgeFactor: DoubleArray
) {
    private val edgeCost = DoubleArray(edgeA.size) { e ->
        distanceMeters(lat[edgeA[e]], lon[edgeA[e]], lat[edgeB[e]], lon[edgeB[e]]) * edgeFactor[e]
    }

    // Список смежности в плоских массивах: рёбра узла n — adjEdges[adjStart[n] until adjStart[n + 1]].
    private val adjStart = IntArray(lat.size + 1)
    private val adjEdges: IntArray

    init {
        require(lat.size == lon.size && edgeA.size == edgeB.size && edgeA.size == edgeFactor.size)
        for (e in edgeA.indices) {
            adjStart[edgeA[e] + 1]++
            adjStart[edgeB[e] + 1]++
        }
        for (n in 1..lat.size) adjStart[n] += adjStart[n - 1]
        val fill = adjStart.copyOf()
        adjEdges = IntArray(edgeA.size * 2)
        for (e in edgeA.indices) {
            adjEdges[fill[edgeA[e]]++] = e
            adjEdges[fill[edgeB[e]]++] = e
        }
    }

    val isEmpty: Boolean get() = edgeA.isEmpty()

    /** Ближайшая к [p] точка сети: на отрезке [edge] в доле [t] от его начала. */
    data class Snap(val edge: Int, val t: Double, val point: GeoPoint, val distanceMeters: Double)

    fun snap(p: GeoPoint): Snap? {
        var best: Snap? = null
        val kx = METERS_PER_DEGREE * cos(Math.toRadians(p.lat))
        for (e in edgeA.indices) {
            val a = edgeA[e]
            val b = edgeB[e]
            val bx = (lon[b] - lon[a]) * kx
            val by = (lat[b] - lat[a]) * METERS_PER_DEGREE
            val px = (p.lon - lon[a]) * kx
            val py = (p.lat - lat[a]) * METERS_PER_DEGREE
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val d = hypot(px - t * bx, py - t * by)
            if (best == null || d < best.distanceMeters) {
                best = Snap(e, t, GeoPoint(lat[a] + (lat[b] - lat[a]) * t, lon[a] + (lon[b] - lon[a]) * t), d)
            }
        }
        return best
    }

    /**
     * Путь по улицам от [from] до [to]: начинается в [from], кончается в [to].
     * null — одна из точек дальше [maxSnapMeters] от сети (например, мы на другом конце города).
     */
    fun route(from: GeoPoint, to: GeoPoint, maxSnapMeters: Double = MAX_SNAP_METERS): List<GeoPoint>? {
        val s = snap(from)?.takeIf { it.distanceMeters <= maxSnapMeters } ?: return null
        val g = snap(to)?.takeIf { it.distanceMeters <= maxSnapMeters } ?: return null
        val middle = if (s.edge == g.edge) listOf(s.point, g.point) else listOf(s.point) + shortest(s, g) + g.point
        val out = ArrayList<GeoPoint>(middle.size + 2)
        out += from
        for (p in middle + to) {
            val last = out.last()
            if (distanceMeters(last.lat, last.lon, p.lat, p.lon) > 0.5) out += p
        }
        if (out.size < 2) out += to
        return out
    }

    /** Узлы кратчайшего пути от точки [s] до точки [g] на разных отрезках (Дейкстра). */
    private fun shortest(s: Snap, g: Snap): List<GeoPoint> {
        val best = DoubleArray(lat.size) { Double.MAX_VALUE }
        val prev = IntArray(lat.size) { -1 }
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        fun push(node: Int, cost: Double, from: Int) {
            if (cost < best[node]) {
                best[node] = cost
                prev[node] = from
                queue += cost to node
            }
        }
        push(edgeA[s.edge], s.t * edgeCost[s.edge], -1)
        push(edgeB[s.edge], (1 - s.t) * edgeCost[s.edge], -1)
        val goalA = edgeA[g.edge]
        val goalB = edgeB[g.edge]
        val tailA = g.t * edgeCost[g.edge]
        val tailB = (1 - g.t) * edgeCost[g.edge]
        var done = 0
        while (queue.isNotEmpty()) {
            val (cost, u) = queue.poll()!!
            if (cost > best[u]) continue
            if (u == goalA || u == goalB) done++
            // Оба конца целевого отрезка найдены — дальше искать незачем.
            if (done == 2 || best[goalA] + tailA <= cost && best[goalB] + tailB <= cost) break
            for (i in adjStart[u] until adjStart[u + 1]) {
                val e = adjEdges[i]
                val v = if (edgeA[e] == u) edgeB[e] else edgeA[e]
                push(v, cost + edgeCost[e], u)
            }
        }
        val end = if (best[goalA] + tailA <= best[goalB] + tailB) goalA else goalB
        if (best[end] == Double.MAX_VALUE) return emptyList()
        val chain = ArrayList<GeoPoint>()
        var n = end
        while (n != -1) {
            chain += GeoPoint(lat[n], lon[n])
            n = prev[n]
        }
        return chain.asReversed()
    }

    companion object {
        /** Дальше этого от улиц центра встроенная карта не помогает — путь спрашиваем у OSRM. */
        const val MAX_SNAP_METERS = 150.0
        private const val METERS_PER_DEGREE = 111_320.0

        val EMPTY = StreetGraph(DoubleArray(0), DoubleArray(0), IntArray(0), IntArray(0), DoubleArray(0))

        /** Из плоских массивов streets.json: узлы — широта, долгота; отрезки — узел, узел, «цена» ×100. */
        fun fromFlat(nodes: DoubleArray, edges: IntArray): StreetGraph {
            val n = nodes.size / 2
            val m = edges.size / 3
            return StreetGraph(
                DoubleArray(n) { nodes[2 * it] },
                DoubleArray(n) { nodes[2 * it + 1] },
                IntArray(m) { edges[3 * it] },
                IntArray(m) { edges[3 * it + 1] },
                DoubleArray(m) { edges[3 * it + 2] / 100.0 }
            )
        }
    }
}
