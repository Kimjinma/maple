import java.util.*;

public class AlgorithmBenchmark {
    // 가상의 장비 노드
    static class Node {
        String slot;
        double gain;
        long costMeso;
        double efficiency;

        public Node(String slot, double gain, long costMeso) {
            this.slot = slot;
            this.gain = gain;
            this.costMeso = costMeso;
            this.efficiency = gain / (costMeso == 0 ? 1 : costMeso);
        }
    }

    static int M = 5; // 슬롯당 교체 후보 아이템 수
    static int N = 10; // 교체 가능한 장비 부위 수(방어구, 무기, 장신구 등)
    static List<List<Node>> alternativesBySlot = new ArrayList<>();
    
    static double maxGain = -1; // DFS 최적해 저장용
    
    public static void main(String[] args) {
        // 데이터 준비 (10부위 * 부위당 5개 아이템 = 총 5^10 = 9,765,625 가지 경우의 수)
        for(int i=0; i<N; i++) {
            List<Node> candidates = new ArrayList<>();
            for(int j=0; j<M; j++) {
                double gain = Math.random() * 100;
                long cost = (long) (Math.random() * 1000000000L) + 100000000L;
                candidates.add(new Node("Slot_"+i, gain, cost));
            }
            alternativesBySlot.add(candidates);
        }

        // 1. 기존 완전탐색 (Brute-Force / DFS) 방식 측정
        long startDfs = System.currentTimeMillis();
        // 타겟 갭(가장 작은 경우의 수)
        dfs(0, 0, 0, 30000000000L); // 예산 300억 내에서 최대 스펙업 찾기
        long endDfs = System.currentTimeMillis();


        // 2. 현재 구현된 가성비(Greedy) 방식 측정
        long startGreedy = System.currentTimeMillis();
        greedySolve(30000000000L);
        long endGreedy = System.currentTimeMillis();

        long dfsTime = endDfs - startDfs;
        long greedyTime = endGreedy - startGreedy;

        System.out.println("========== 최적화 알고리즘 성능 비교 ==========");
        System.out.println("장비 부위(Slot) 수: " + N);
        System.out.println("부위당 교체 후보(Item) 수: " + M + " (조합의 수: " + (long)Math.pow(M, N) + ")");
        System.out.println("----------------------------------------------");
        System.out.println("1. 기존 완전탐색(Brute-Force) 소요 시간: " + dfsTime + " ms");
        System.out.println("2. 현재 그리디(Greedy) 최적화 소요 시간: " + greedyTime + " ms");
        System.out.println("----------------------------------------------");
        System.out.println("성능 개선 배율: 약 " + (dfsTime / (double)Math.max(1, greedyTime)) + "배 빨라짐!");
    }

    // 1안. 완전 탐색 (이전 방식) - 모든 경우의 수 조합 (DFS)
    public static void dfs(int depth, double currentGain, long currentCost, long maxBudget) {
        if (currentCost > maxBudget) return;
        if (depth == N) {
            if (currentGain > maxGain) {
                maxGain = currentGain;
            }
            return;
        }

        for (Node node : alternativesBySlot.get(depth)) {
            dfs(depth + 1, currentGain + node.gain, currentCost + node.costMeso, maxBudget);
        }
    }

    // 2안. 가성비 그리디 탐색 (현재 방식)
    public static void greedySolve(long maxBudget) {
        // 모든 후보를 가성비(efficiency) 내림차순 정렬
        List<Node> allCandidates = new ArrayList<>();
        for (List<Node> nodes : alternativesBySlot) {
            allCandidates.addAll(nodes);
        }
        allCandidates.sort((a, b) -> Double.compare(b.efficiency, a.efficiency));

        double totalGain = 0;
        long totalCost = 0;
        Set<String> usedSlots = new HashSet<>();

        // 가성비 좋은 순으로 탐색하며 한 부위당 1개씩만 장착하도록 예산 내에서 선택
        for (Node node : allCandidates) {
            if (usedSlots.contains(node.slot)) continue;
            if (totalCost + node.costMeso <= maxBudget) {
                totalGain += node.gain;
                totalCost += node.costMeso;
                usedSlots.add(node.slot);
            }
            if (usedSlots.size() == N) break; // 전 부위 장착 완료 시 종료
        }
    }
}
