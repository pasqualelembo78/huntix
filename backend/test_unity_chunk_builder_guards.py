#!/usr/bin/env python3
# Test statici del fix dell'NRE in ChunkBuilder (log 25/11 durante la guida):
# il chunk puo' essere scaricato fra due yield, quindi ogni sospensione deve
# essere seguita da un controllo che la root sia ancora viva. Nessun device:
# verifichiamo le barriere nel punto esatto della coroutine.
import os
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SCRIPTS = 'unity-project/Assets/City/Scripts/'


def read(path):
    with open(os.path.join(ROOT, path), encoding='utf-8', errors='replace') as f:
        return f.read()


def build_body(src):
    """Solo il corpo della coroutine Build: esclude gli helper di elevazione."""
    start = src.index('public static IEnumerator Build(')
    end = src.index('Racoglie le altezze degli edifici', start)
    return src[start:end]


class TestChunkAliveHelper(unittest.TestCase):
    def test_helper_controlla_chunk_root_e_transform(self):
        src = read(SCRIPTS + 'OSM/ChunkBuilder.cs')
        self.assertIn('private static bool ChunkAlive(ChunkData chunk)', src)
        helper = src[src.index('private static bool ChunkAlive(ChunkData chunk)'):]
        helper = helper[:helper.index('public static IEnumerator Build(')]
        self.assertIn('chunk != null && chunk.root != null &&', helper)
        self.assertIn('chunk.root.transform != null;', helper)


class TestYieldGuards(unittest.TestCase):
    def setUp(self):
        self.body = build_body(read(SCRIPTS + 'OSM/ChunkBuilder.cs'))

    def test_ogni_yield_e_seguito_da_guardia_sul_chunk(self):
        lines = self.body.splitlines()
        yields = [i for i, line in enumerate(lines) if 'yield return null;' in line]
        self.assertGreaterEqual(len(yields), 10)
        for n, i in enumerate(yields):
            # Fra DUE yield consecutivi deve esserci una guardia: puo' stare
            # subito dopo, oppure dopo la chiusura di un blocco `if/else`
            # (sezioni strade) o dentro il pump degli NPC.
            stop = yields[n + 1] if n + 1 < len(yields) else len(lines)
            segment = '\n'.join(lines[i + 1:stop])
            self.assertIn('ChunkAlive(chunk)', segment,
                          'yield alla riga %d senza guardia prima del successivo'
                          % (i + 1))

    def test_buildings_hanno_guardia_su_record_e_root(self):
        self.assertIn('if (cand.rec == null || !ChunkAlive(chunk)) yield break;', self.body)
        self.assertNotIn('cand == null', self.body)  # BuildingCandidate e' uno struct

    def test_log_edifici_null_safe(self):
        self.assertIn('(b != null ? b.id.ToString() : "null")', self.body)
        self.assertIn('(b != null ? b.t : "null")', self.body)
        self.assertIn('(cand.rec.t ?? "null")', self.body)

    def test_terreno_non_deref_componenti_assenti(self):
        start = self.body.index('// Il terreno puo\' non essere stato creato')
        end = self.body.index('// ── strade (una mesh sola)', start)
        terrain = self.body[start:end]
        self.assertNotIn('GetComponent<MeshFilter>().sharedMesh', terrain)
        self.assertNotIn('GetComponent<MeshRenderer>().sharedMaterial', terrain)
        self.assertIn('Mesh terrMesh = terrFilter != null ? terrFilter.sharedMesh : null;', terrain)

    def test_chiusura_non_marca_chunk_scaricato_come_costruito(self):
        built = self.body.index('chunk.built = true;')
        before = self.body[max(0, built - 300):built]
        self.assertIn('if (!ChunkAlive(chunk)) yield break;', before)


class TestEggSpawn(unittest.TestCase):
    def setUp(self):
        self.body = build_body(read(SCRIPTS + 'OSM/ChunkBuilder.cs'))

    def test_uova_non_spawnano_su_root_distrutta(self):
        self.assertIn('var eggMgr = City.Economy.EggSpawnManager.Instance;', self.body)
        self.assertIn('if (eggMgr != null && ChunkAlive(chunk))', self.body)
        # La vecchia riga `Instance?.SpawnEggsInChunk(chunk.root.transform, ...)`
        # era il NRE dentro MoveNext: il `?.` proteggeva Manager, non la root.
        self.assertNotIn('Instance?.SpawnEggsInChunk(', self.body)


class TestChunkDataDestroy(unittest.TestCase):
    def test_destroy_azzerra_subito_i_riferimenti_per_le_guardie(self):
        src = read(SCRIPTS + 'OSM/ChunkData.cs')
        destroy = src[src.index('public void Destroy()'):]
        for field in ('root', 'terrainGo', 'roadsGo', 'sidewalksGo',
                      'buildingsGo', 'natureGo', 'npcsGo', 'airportsGo'):
            self.assertIn(field + ' = null;', destroy)
        self.assertIn('Object.Destroy(root);', destroy)


if __name__ == '__main__':
    unittest.main()
