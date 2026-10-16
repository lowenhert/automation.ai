import { DEFAULT_LAYOUT, layoutGraph } from './screen-graph-layout';
import { ScreenGraphEdge } from '../../shared/services/screen-exploration.service';

function edge(from: number, to: number, action: ScreenGraphEdge['action'], count = 1): ScreenGraphEdge {
  return { id: from * 100 + to, from, to, action, count, lastSeenAt: '' };
}

describe('layoutGraph', () => {
  it('places screens in columns by distance from the root', () => {
    const layout = layoutGraph([1, 2, 3, 4], [edge(1, 2, 'SELECT'), edge(2, 3, 'SELECT'), edge(1, 4, 'DPAD_RIGHT')], 1);
    expect(layout.nodes.get(1)!.layer).toBe(0);
    expect(layout.nodes.get(2)!.layer).toBe(1);
    expect(layout.nodes.get(4)!.layer).toBe(1);
    expect(layout.nodes.get(3)!.layer).toBe(2);
    expect(layout.nodes.get(2)!.x).toBeGreaterThan(layout.nodes.get(1)!.x);
    expect(layout.nodes.get(2)!.y).not.toBe(layout.nodes.get(4)!.y);
  });

  it('turns self-loops into badges and merges parallel edges', () => {
    const layout = layoutGraph([1, 2], [
      edge(1, 1, 'DPAD_DOWN', 3), edge(1, 1, 'DPAD_UP'), edge(1, 2, 'SELECT', 2), edge(1, 2, 'DPAD_RIGHT')
    ], 1);
    expect(layout.nodes.get(1)!.selfLoops).toEqual([{ action: 'DPAD_DOWN', count: 3 }, { action: 'DPAD_UP', count: 1 }]);
    expect(layout.edges.length).toBe(1);
    expect(layout.edges[0].labels).toEqual(['OK', '→']);
    expect(layout.edges[0].count).toBe(3);
  });

  it('marks back-navigation edges as backward', () => {
    const layout = layoutGraph([1, 2], [edge(1, 2, 'SELECT'), edge(2, 1, 'BACK')], 1);
    const back = layout.edges.find(e => e.from === 2)!;
    expect(back.backward).toBeTrue();
    expect(back.labels).toEqual(['Back']);
    expect(layout.edges.find(e => e.from === 1)!.backward).toBeFalse();
  });

  it('places unreachable screens in later columns and ignores edges to unknown nodes', () => {
    const layout = layoutGraph([1, 2, 3], [edge(1, 2, 'SELECT'), edge(3, 99, 'SELECT')], 1);
    expect(layout.nodes.get(3)!.layer).toBe(2);
    expect(layout.edges.length).toBe(1);
  });

  it('handles an empty graph', () => {
    const layout = layoutGraph([], []);
    expect(layout.nodes.size).toBe(0);
    expect(layout.width).toBeGreaterThanOrEqual(300);
    expect(layout.height).toBeGreaterThanOrEqual(160);
  });

  it('sizes the canvas to fit all columns', () => {
    const layout = layoutGraph([1, 2, 3], [edge(1, 2, 'SELECT'), edge(2, 3, 'SELECT')], 1);
    const rightmost = Math.max(...[...layout.nodes.values()].map(n => n.x));
    expect(layout.width).toBeGreaterThanOrEqual(rightmost + DEFAULT_LAYOUT.nodeWidth);
  });
});
