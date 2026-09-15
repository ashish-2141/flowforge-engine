package com.ashish.flowforge.engine.definition;

import java.util.*;

public final class DagValidator {
    public Optional<List<String>> findCycle(List<TaskDefinitionInput> tasks) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (TaskDefinitionInput task : tasks) {
            graph.put(task.taskKey(), new ArrayList<>(task.dependsOn()));
        }

        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (String key : graph.keySet()) indegree.put(key, 0);
        for (List<String> deps : graph.values()) {
            for (String dependency : deps) indegree.merge(dependency, 1, Integer::sum);
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((key, degree) -> { if (degree == 0) queue.addLast(key); });
        int processed = 0;
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            processed++;
            for (String dependency : graph.get(node)) {
                int next = indegree.merge(dependency, -1, Integer::sum);
                if (next == 0) queue.addLast(dependency);
            }
        }
        if (processed == graph.size()) return Optional.empty();
        return findCyclePath(graph);
    }

    private Optional<List<String>> findCyclePath(Map<String, List<String>> graph) {
        Map<String, Integer> color = new HashMap<>();
        graph.keySet().forEach(key -> color.put(key, 0));

        for (String start : graph.keySet()) {
            if (color.get(start) != 0) continue;
            List<Frame> stack = new ArrayList<>();
            List<String> path = new ArrayList<>();
            stack.add(new Frame(start));
            path.add(start);
            color.put(start, 1);

            while (!stack.isEmpty()) {
                Frame frame = stack.get(stack.size() - 1);
                List<String> nextNodes = graph.get(frame.node);
                if (frame.nextIndex >= nextNodes.size()) {
                    color.put(frame.node, 2);
                    stack.remove(stack.size() - 1);
                    path.remove(path.size() - 1);
                    continue;
                }

                String next = nextNodes.get(frame.nextIndex++);
                int nextColor = color.getOrDefault(next, 0);
                if (nextColor == 0) {
                    color.put(next, 1);
                    stack.add(new Frame(next));
                    path.add(next);
                } else if (nextColor == 1) {
                    int cycleStart = path.indexOf(next);
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(next);
                    return Optional.of(cycle);
                }
            }
        }
        return Optional.empty();
    }

    private static final class Frame {
        private final String node;
        private int nextIndex;
        private Frame(String node) { this.node = node; }
    }
}
