package io.github.guyeven.issueflow.ticket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketDependencyGraphTests {

    private final TicketDependencyGraph graph = new TicketDependencyGraph();

    @Test
    void acceptsDependencyInEmptyGraph() {
        assertThat(graph.wouldCreateCycle(List.of(), 1L, 2L)).isFalse();
    }

    @Test
    void acceptsChainExtension() {
        assertThat(graph.wouldCreateCycle(edges(edge(1, 2), edge(2, 3)), 3L, 4L)).isFalse();
    }

    @Test
    void acceptsDiamond() {
        assertThat(graph.wouldCreateCycle(
                edges(edge(1, 2), edge(1, 3), edge(2, 4)), 3L, 4L
        )).isFalse();
    }

    @Test
    void convergingPathsAreNotCycles() {
        assertThat(graph.wouldCreateCycle(
                edges(edge(1, 2), edge(1, 3), edge(2, 4), edge(3, 4)), 5L, 1L
        )).isFalse();
    }

    @Test
    void rejectsDirectTwoNodeCycle() {
        assertThat(graph.wouldCreateCycle(edges(edge(2, 1)), 1L, 2L)).isTrue();
    }

    @Test
    void rejectsThreeNodeTransitiveCycle() {
        assertThat(graph.wouldCreateCycle(edges(edge(1, 2), edge(2, 3)), 3L, 1L)).isTrue();
    }

    @Test
    void rejectsLongerTransitiveCycle() {
        assertThat(graph.wouldCreateCycle(
                edges(edge(1, 2), edge(2, 3), edge(3, 4), edge(4, 5)), 5L, 1L
        )).isTrue();
    }

    private TicketDependencyEdge edge(long ticketId, long blockerId) {
        return new TicketDependencyEdge(ticketId, blockerId);
    }

    private List<TicketDependencyEdge> edges(TicketDependencyEdge... edges) {
        return List.of(edges);
    }
}
