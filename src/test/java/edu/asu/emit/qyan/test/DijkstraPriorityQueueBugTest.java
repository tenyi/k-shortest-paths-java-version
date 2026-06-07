package edu.asu.emit.qyan.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import edu.asu.emit.algorithm.graph.*;
import edu.asu.emit.algorithm.graph.abstraction.*;
import edu.asu.emit.algorithm.graph.shortestpaths.DijkstraShortestPathAlg;

/**
 * 壓力測試與迴歸測試：驗證 Dijkstra 最短路徑演算法是否會因為 PriorityQueue 更新元素時
 * 損壞堆積結構（Heap Invariant）而算出錯誤的最短路徑。
 */
public class DijkstraPriorityQueueBugTest {

    private static final String TEMP_GRAPH_FILE = "target/temp_dijkstra_bug_test_graph.txt";

    @BeforeClass
    public void setUp() {
        // 確保 target 目錄存在
        new File("target").mkdirs();
    }

    @AfterClass
    public void tearDown() {
        // 測試完畢後清理臨時檔案
        try {
            Files.deleteIfExists(Paths.get(TEMP_GRAPH_FILE));
        } catch (IOException e) {
            // 忽略清理錯誤
        }
    }

    /**
     * Naive Dijkstra 實作，不使用 PriorityQueue 堆積，而是每次遍歷 List 來選出最小值。
     * 這種寫法雖然效率為 O(V^2)，但能百分之百保證其最短路徑結果之正確性，用作比對基準。
     */
    private Path getShortestPathNaive(BaseGraph graph, BaseVertex sourceVertex, BaseVertex sinkVertex) {
        Map<BaseVertex, Double> distances = new HashMap<>();
        Map<BaseVertex, BaseVertex> predecessors = new HashMap<>();
        Set<BaseVertex> settled = new HashSet<>();
        List<BaseVertex> queue = new ArrayList<>();

        for (BaseVertex v : graph.getVertexList()) {
            distances.put(v, Double.MAX_VALUE);
        }
        distances.put(sourceVertex, 0.0);
        queue.add(sourceVertex);

        while (!queue.isEmpty()) {
            BaseVertex u = null;
            double minDist = Double.MAX_VALUE;
            for (BaseVertex v : queue) {
                double d = distances.get(v);
                if (d < minDist) {
                    minDist = d;
                    u = v;
                }
            }

            if (u == null) break;
            queue.remove(u);
            settled.add(u);

            if (u.equals(sinkVertex)) {
                break;
            }

            for (BaseVertex v : graph.getAdjacentVertices(u)) {
                if (settled.contains(v)) continue;
                double newDist = distances.get(u) + graph.getEdgeWeight(u, v);
                if (newDist < distances.get(v)) {
                    distances.put(v, newDist);
                    predecessors.put(v, u);
                    if (!queue.contains(v)) {
                        queue.add(v);
                    }
                }
            }
        }

        List<BaseVertex> vertexList = new Vector<>();
        double weight = distances.get(sinkVertex);
        if (weight != Double.MAX_VALUE) {
            BaseVertex curVertex = sinkVertex;
            do {
                vertexList.add(curVertex);
                curVertex = predecessors.get(curVertex);
            } while (curVertex != null && curVertex != sourceVertex);
            vertexList.add(sourceVertex);
            Collections.reverse(vertexList);
        }
        return new Path(vertexList, weight);
    }

    @Test
    public void testDijkstraPriorityQueueCorrectness() {
        // 使用固定種子確保測試可重現
        Random random = new Random(100);
        int numNodes = 30;
        int numEdges = 150;
        int testRuns = 1000; // 1,000 次測試，在單元測試中只需不到一秒即可完成

        for (int run = 0; run < testRuns; run++) {
            StringBuilder sb = new StringBuilder();
            sb.append(numNodes).append("\n\n");

            Set<String> edges = new HashSet<>();
            for (int i = 0; i < numEdges; i++) {
                int u = random.nextInt(numNodes);
                int v = random.nextInt(numNodes);
                if (u == v) continue;
                double weight = Math.round((random.nextDouble() * 20.0 + 1.0) * 10.0) / 10.0;
                String key = u + " " + v;
                if (!edges.contains(key)) {
                    edges.add(key);
                    sb.append(u).append(" ").append(v).append(" ").append(weight).append("\n");
                }
            }

            try {
                Files.write(Paths.get(TEMP_GRAPH_FILE), sb.toString().getBytes());
                Graph graph = new Graph(TEMP_GRAPH_FILE);

                int startId = random.nextInt(numNodes);
                int endId = random.nextInt(numNodes);
                while (startId == endId) {
                    endId = random.nextInt(numNodes);
                }

                BaseVertex start = graph.getVertex(startId);
                BaseVertex end = graph.getVertex(endId);

                if (start == null || end == null) continue;

                DijkstraShortestPathAlg alg = new DijkstraShortestPathAlg(graph);
                Path pathPQ = alg.getShortestPath(start, end);
                Path pathNaive = getShortestPathNaive(graph, start, end);

                // 斷言兩者計算出的權重必須完全一致
                Assert.assertEquals(pathPQ.getWeight(), pathNaive.getWeight(), 1e-6,
                        "Dijkstra 演算法結果與基準 Naive 演算法不一致！在回合: " + run);

            } catch (Exception e) {
                Assert.fail("測試中發生異常: " + e.getMessage(), e);
            }
        }
    }
}
