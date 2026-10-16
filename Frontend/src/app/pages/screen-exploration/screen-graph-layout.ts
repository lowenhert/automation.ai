import { RemoteKey, ScreenGraphEdge } from '../../shared/services/screen-exploration.service';

export interface LayoutOptions {
  nodeWidth: number;
  nodeHeight: number;
  columnGap: number;
  rowGap: number;
  padding: number;
}

export const DEFAULT_LAYOUT: LayoutOptions = { nodeWidth: 170, nodeHeight: 58, columnGap: 90, rowGap: 34, padding: 24 };

export interface PositionedNode {
  id: number;
  x: number;
  y: number;
  layer: number;
  selfLoops: { action: RemoteKey; count: number }[];
}

export interface PositionedEdge {
  from: number;
  to: number;
  labels: string[];
  count: number;
  path: string;
  labelX: number;
  labelY: number;
  backward: boolean;
}

export interface GraphLayout {
  nodes: Map<number, PositionedNode>;
  edges: PositionedEdge[];
  width: number;
  height: number;
}

const SHORT: Record<RemoteKey, string> = {
  DPAD_UP: '↑', DPAD_DOWN: '↓', DPAD_LEFT: '←', DPAD_RIGHT: '→', SELECT: 'OK', BACK: 'Back', HOME: 'Home'
};

export function actionLabel(action: RemoteKey): string {
  return SHORT[action] ?? action;
}

/**
 * Layered left-to-right layout: BFS distance from the root decides the column.
 * Self-loops (focus moves that stay on the same screen) become badges instead of edges,
 * and parallel edges between the same two screens are merged into one labelled edge.
 */
export function layoutGraph(nodeIds: number[], edges: ScreenGraphEdge[], rootId?: number,
                            options: LayoutOptions = DEFAULT_LAYOUT): GraphLayout {
  const ids = [...nodeIds];
  const known = new Set(ids);
  const adjacency = new Map<number, number[]>();
  for (const e of edges) {
    if (e.from === e.to || !known.has(e.from) || !known.has(e.to)) continue;
    adjacency.set(e.from, [...(adjacency.get(e.from) ?? []), e.to]);
  }

  // BFS from the root; unreachable components start in the next free column
  const layer = new Map<number, number>();
  const start = rootId !== undefined && known.has(rootId) ? rootId : ids[0];
  let nextLayer = 0;
  for (const seed of start !== undefined ? [start, ...ids] : ids) {
    if (layer.has(seed)) continue;
    const queue = [seed];
    layer.set(seed, nextLayer);
    let maxSeen = nextLayer;
    while (queue.length) {
      const current = queue.shift()!;
      for (const next of adjacency.get(current) ?? []) {
        if (layer.has(next)) continue;
        const l = layer.get(current)! + 1;
        layer.set(next, l);
        maxSeen = Math.max(maxSeen, l);
        queue.push(next);
      }
    }
    nextLayer = maxSeen + 1;
  }

  const columns = new Map<number, number[]>();
  for (const id of ids) {
    const l = layer.get(id)!;
    columns.set(l, [...(columns.get(l) ?? []), id]);
  }

  const { nodeWidth, nodeHeight, columnGap, rowGap, padding } = options;
  const nodes = new Map<number, PositionedNode>();
  let maxRows = 0;
  for (const [l, column] of columns) {
    column.sort((a, b) => a - b);
    maxRows = Math.max(maxRows, column.length);
    column.forEach((id, row) => nodes.set(id, {
      id, layer: l, selfLoops: [],
      x: padding + l * (nodeWidth + columnGap),
      y: padding + row * (nodeHeight + rowGap)
    }));
  }

  const merged = new Map<string, { from: number; to: number; labels: string[]; count: number }>();
  for (const e of edges) {
    const fromNode = nodes.get(e.from);
    if (!fromNode || !nodes.has(e.to)) continue;
    if (e.from === e.to) {
      fromNode.selfLoops.push({ action: e.action, count: e.count });
      continue;
    }
    const key = `${e.from}->${e.to}`;
    const m = merged.get(key) ?? { from: e.from, to: e.to, labels: [], count: 0 };
    m.labels.push(actionLabel(e.action));
    m.count += e.count;
    merged.set(key, m);
  }

  const positioned: PositionedEdge[] = [];
  for (const m of merged.values()) {
    const a = nodes.get(m.from)!;
    const b = nodes.get(m.to)!;
    const backward = b.layer <= a.layer;
    let path: string;
    let labelX: number;
    let labelY: number;
    if (!backward) {
      const x1 = a.x + nodeWidth, y1 = a.y + nodeHeight / 2;
      const x2 = b.x, y2 = b.y + nodeHeight / 2;
      const mid = (x1 + x2) / 2;
      path = `M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`;
      labelX = mid;
      labelY = (y1 + y2) / 2 - 6;
    } else {
      // Backward/same-column edges arc below the nodes so they don't hide forward edges
      const x1 = a.x + nodeWidth / 2, y1 = a.y + nodeHeight;
      const x2 = b.x + nodeWidth / 2, y2 = b.y + nodeHeight;
      const dip = Math.max(y1, y2) + rowGap + 10;
      path = `M ${x1} ${y1} C ${x1} ${dip}, ${x2} ${dip}, ${x2} ${y2}`;
      labelX = (x1 + x2) / 2;
      labelY = dip - 4;
    }
    positioned.push({ ...m, path, labelX, labelY, backward });
  }

  const layers = columns.size;
  return {
    nodes,
    edges: positioned,
    width: Math.max(padding * 2 + layers * nodeWidth + Math.max(0, layers - 1) * columnGap, 300),
    height: Math.max(padding * 2 + maxRows * nodeHeight + Math.max(0, maxRows - 1) * rowGap + rowGap + 20, 160)
  };
}
