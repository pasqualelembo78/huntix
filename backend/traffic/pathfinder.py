"""A* pathfinder — server-side mirror of Unity RoadPathfinder."""

from __future__ import annotations
from typing import List, Optional, Tuple
from .road_graph import RoadGraph


def find_path(graph: RoadGraph, start_id: int, end_id: int) -> Optional[List[int]]:
    if start_id == end_id:
        return [start_id]

    node_count = len(graph.nodes)
    g_score = [float("inf")] * node_count
    f_score = [float("inf")] * node_count
    came_from = [-1] * node_count
    closed = set()

    g_score[start_id] = 0.0
    sx, sz = graph.node_map[start_id].x, graph.node_map[start_id].z
    ex, ez = graph.node_map[end_id].x, graph.node_map[end_id].z
    f_score[start_id] = ((sx-ex)**2 + (sz-ez)**2) ** 0.5

    open_list = [start_id]
    safety = 0

    while open_list and safety < 10000:
        safety += 1
        current = -1
        best_f = float("inf")
        for nid in open_list:
            if f_score[nid] < best_f:
                best_f = f_score[nid]
                current = nid

        if current == end_id:
            return _reconstruct(came_from, current)

        open_list.remove(current)
        closed.add(current)

        for nid in graph.get_neighbors(current):
            if nid in closed:
                continue
            arc_id = graph.get_arc_between(current, nid)
            if arc_id is None:
                continue

            arc = graph.arc_map[arc_id]
            tentative_g = g_score[current] + arc.length

            if tentative_g < g_score[nid]:
                came_from[nid] = current
                g_score[nid] = tentative_g
                nx, nz = graph.node_map[nid].x, graph.node_map[nid].z
                f_score[nid] = tentative_g + ((nx-ex)**2 + (nz-ez)**2) ** 0.5

                if nid not in open_list:
                    open_list.append(nid)

    return None


def _reconstruct(came_from: List[int], current: int) -> List[int]:
    path = [current]
    while came_from[current] != -1:
        current = came_from[current]
        path.append(current)
    path.reverse()
    return path
