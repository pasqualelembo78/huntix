import re, subprocess, sys

def read(path):
    with open(path, encoding="utf-8") as f: return f.read()
def write(path, s):
    with open(path, "w", encoding="utf-8") as f: f.write(s)

base = "/root/giochi/huntix/unity-project/Assets/City/Scripts"

# ── 1) ChunkVehiclePopulator.cs: cull 500 -> 260m (come da commento "260 m tiene il costo render basso") ──
p1 = base + "/Vehicle/Traffic/ChunkVehiclePopulator.cs"
s1 = read(p1)
old1 = "private const float CullRadius = 500f;\n"
new1 = "private const float CullRadius = 260f;\n"
assert s1.count(old1) == 1, "veh cull"
s1 = s1.replace(old1, new1)
write(p1, s1)

# ── 2) NPCPopulator.cs: 50 -> 30 pedoni/chunk ──
p2 = base + "/NPC/NPCPopulator.cs"
s2 = read(p2)
old2 = "private const int MaxNpcPerChunk = 50;"
new2 = "private const int MaxNpcPerChunk = 30;"
assert s2.count(old2) == 1, "npc cap"
s2 = s2.replace(old2, new2)
write(p2, s2)

# ── 3) NPCController.cs: raycast sondaggio suolo solo entro ~120m, oltre DEM ──
p3 = base + "/NPC/NPCController.cs"
s3 = read(p3)
old3 = "private const float CullDistSqr = 280f * 280f;   // ~280 m\n"
new3 = "private const float CullDistSqr = 280f * 280f;   // ~280 m\n        // il sondaggio fisico del suolo (RaycastAll 700m) costa CPU: solo entro ~120m\n        private const float GroundProbeSqr = 120f * 120f;\n"
assert s3.count(old3) == 1, "npc const"
s3 = s3.replace(old3, new3)
old3b = "            if (!_culled)\n            {\n                Vector3 from = pos + Vector3.up * 220f;"
new3b = "            if (!_culled && (_camCache == null\n                || (_camCache.transform.position - transform.position).sqrMagnitude <= GroundProbeSqr))\n            {\n                Vector3 from = pos + Vector3.up * 220f;"
assert s3.count(old3b) == 1, "npc probe"
s3 = s3.replace(old3b, new3b)
write(p3, s3)

# ── 4) CityChunkedWorld.cs: marker WorldReady quando il ponte si toglie (percorso chunked) ──
p4 = base + "/OSM/CityChunkedWorld.cs"
s4 = read(p4)
old4 = "                        RemoveSpawnBridge();\n                        NotifyCityReady();"
new4 = "                        RemoveSpawnBridge();\n                        NotifyCityReady();\n                        try\n                        {\n                            var telemetry = City.Diagnostics.SessionTelemetry.Instance;\n                            if (telemetry != null)\n                                telemetry.WorldReady(0, Manager.BuiltCount, 0, 0);\n                        }\n                        catch (System.Exception) {}"
assert s4.count(old4) == 1, "chunked hook"
s4 = s4.replace(old4, new4)
write(p4, s4)

print("OK patch24 applicata")